# Documentación Técnica: Integración del Servicio Externo de Catálogo de Productos

## 1. Resumen de la Solución
Se implementó la integración con el servicio externo de consulta de productos (`GET /sistema/service/getProductList.do`) sobre la arquitectura existente del proyecto Spring Boot 3.3.6 / Java 17. La solución incorpora consumo HTTP declarativo mediante **Spring Cloud OpenFeign**, autenticación por **Bearer Token** desacoplada de código fuente, manejo integral de excepciones, registro seguro de trazas (logging) y una suite completa de pruebas unitarias.

---

## 2. Arquitectura de Capas

Se respetaron y reforzaron las convenciones y capas del proyecto:

```
src/main/java/com/proyecto/servicios/
│
├── client/
│   └── GestoPagoProductoClient.java    # Cliente declarativo OpenFeign
│
├── config/
│   ├── FeignClientConfig.java           # Timeouts, Logger level y registro de ErrorDecoder
│   └── FeignErrorDecoder.java          # Decodificación y tipado de errores HTTP (401, 403, 504, 5xx)
│
├── controller/
│   ├── ProductoController.java         # Endpoint REST expuesto con anotaciones OpenAPI/Swagger
│   └── GlobalExceptionHandler.java     # Manejador centralizado (@RestControllerAdvice)
│
├── exception/
│   ├── ExternalServiceException.java   # Excepción base de integración
│   ├── ServiceAuthenticationException.java # 401 / 403
│   ├── ServiceTimeoutException.java    # Timeouts / 504
│   ├── ServiceCommunicationException.java # Errores de red / conexión
│   └── ServiceResponseException.java   # Respuestas HTTP no exitosas (4xx, 5xx)
│
├── model/
│   ├── ErrorResponse.java              # DTO para respuestas de error uniformes
│   └── producto/
│       ├── ProductoDTO.java            # DTO del producto individual
│       └── ProductoListResponse.java   # DTO raíz de respuesta del servicio externo
│
└── service/
    ├── ProductoService.java            # Contrato de negocio
    └── Impl/
        └── ProductoServiceImpl.java    # Implementación con DI por constructor y logging seguro
```

---

## 3. Configuración de Propiedades y Seguridad del Token

Las propiedades fueron centralizadas en `src/main/resources/application.properties` para evitar valores *hardcodeados*:

```properties
# Servicio Externo GestoPago - Catálogo de Productos
gestopago.productos.url=${gestopago.auth.url}
gestopago.productos.endpoint=/sistema/service/getProductList.do
gestopago.productos.token=${GESTOPAGO_PRODUCTOS_TOKEN:bearer_token_de_configuracion_12345}
gestopago.productos.connect-timeout=5000
gestopago.productos.read-timeout=10000

# Configuración específica de OpenFeign
spring.cloud.openfeign.client.config.gestoPagoProductoClient.connect-timeout=5000
spring.cloud.openfeign.client.config.gestoPagoProductoClient.read-timeout=10000
```

### Decisiones técnicas sobre el Bearer Token:
- **Desacoplamiento:** Se inyecta mediante `@Value("${gestopago.productos.token}")` o variable de entorno `GESTOPAGO_PRODUCTOS_TOKEN`.
- **Formateo Seguro:** `ProductoServiceImpl` valida la existencia del token y garantiza el prefijo `Bearer ` sin duplicarlo. Si no está configurado, lanza `ServiceAuthenticationException` antes de emitir cualquier petición externa.

---

## 4. Cliente de Integración (OpenFeign)

Se utilizó la interfaz declarativa `GestoPagoProductoClient`:
```java
@FeignClient(
    name = "gestoPagoProductoClient",
    url = "${gestopago.productos.url}",
    configuration = FeignClientConfig.class
)
public interface GestoPagoProductoClient {

    @GetMapping(
        value = "/sistema/service/getProductList.do",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    ProductoListResponse getProductList(
        @RequestHeader("Authorization") String authorization
    );
}
```

### Configuración de Resiliencia (`FeignClientConfig`):
- **Timeouts:** Conexión (5s) y lectura (10s) configurables.
- **Nivel de Logger Feign:** Configurado en `Logger.Level.BASIC` para evitar volcar cabeceras HTTP (`Authorization`) en texto claro a los logs del sistema.
- **ErrorDecoder Personalizado:** Mapea automáticamente respuestas no exitosas a excepciones de negocio.

---

## 5. Manejo de Excepciones

| Escenario | Excepción Disparada | Código HTTP Retornado |
| :--- | :--- | :---: |
| Token faltante / 401 Unauthorized / 403 Forbidden | `ServiceAuthenticationException` | `401 Unauthorized` |
| Timeout de conexión / lectura / SocketTimeout | `ServiceTimeoutException` | `504 Gateway Timeout` |
| Host inalcanzable / Conexión rechazada / Red caída | `ServiceCommunicationException` | `503 Service Unavailable` |
| Respuesta remota no exitosa (500, 502, etc.) | `ServiceResponseException` | `502 Bad Gateway` |
| Error no controlado | `Exception` | `500 Internal Server Error` |

Todos los errores retornan un payload uniforme en formato JSON vía `GlobalExceptionHandler`:
```json
{
  "codigo": 504,
  "mensaje": "Tiempo de espera agotado al consultar el servicio externo",
  "detalle": "Read timed out",
  "timestamp": "2026-10-09T17:10:00.123"
}
```

---

## 6. Registro y Monitoreo (Logging Seguro)

- **Inicio y Fin:** Se registra con nivel `INFO` cada invocación:
  - Inicio: `Iniciando invocación a servicio externo: [GET /sistema/service/getProductList.do]`
  - Fin: `Finalizando invocación a servicio externo con éxito. Total productos obtenidos: X`
- **Sanitización de Datos Sensibles:** Se implementó `sanitizeLogMessage()` en el servicio y en el `FeignErrorDecoder` con expresiones regulares para enmascarar cualquier patrón de `Bearer ***`, `token=***`, `password=***` antes de enviar al logger.

---

## 7. Pruebas Unitarias

Se desarrollaron pruebas unitarias completas bajo **JUnit 5** y **Mockito**:

### 1. `ProductoServiceImplTest` (8 pruebas):
- ✅ `obtenerListaProductos_Exitoso`: Valida respuesta y mapeo de datos con 200 OK.
- ✅ `obtenerListaProductos_ConPrefijoBearerEnConfiguracion_Exitoso`: Valida que no se duplique el prefijo si ya viene en la propiedad.
- ✅ `obtenerListaProductos_TokenNoConfigurado_LanzaExcepcion`: Valida que no se realice la llamada HTTP si falta el token.
- ✅ `obtenerListaProductos_ErrorAutenticacion401_LanzaExcepcion`: Simula rechazo 401 del servicio externo.
- ✅ `obtenerListaProductos_TimeoutSocket_LanzaServiceTimeoutException`: Simula caída por timeout (SocketTimeoutException).
- ✅ `obtenerListaProductos_FalloComunicacion_LanzaServiceCommunicationException`: Simula error de conexión / host inalcanzable.
- ✅ `obtenerListaProductos_Error500Servidor_LanzaServiceResponseException`: Simula falla 500 del servidor remoto.
- ✅ `obtenerListaProductos_CuerpoNulo_LanzaServiceResponseException`: Valida respuesta vacía del proveedor.

### 2. `ProductoControllerTest` (5 pruebas):
- ✅ `GET /productos` retorna 200 con listado.
- ✅ `GET /productos` retorna 401 ante error de autenticación.
- ✅ `GET /productos` retorna 504 ante timeout.
- ✅ `GET /productos` retorna 503 ante fallo de comunicación.
- ✅ `GET /productos` retorna 502 ante respuesta no exitosa del servicio externo.

---

## 8. Comandos de Verificación

Para ejecutar la compilación y pruebas en terminal:
```bash
.\gradlew.bat test
```
o con JDK 17 específico:
```powershell
$env:JAVA_HOME = "C:\Users\marim\.jdks\ms-17.0.20.1"
.\gradlew.bat test
```
