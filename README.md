# Proyecto Integrador: Onboarding de Clientes Personas Físicas

![Java](https://img.shields.io/badge/Java-17%2B-orange?style=flat&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-green?style=flat&logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15%2B-blue?style=flat&logo=postgresql)
![Swagger](https://img.shields.io/badge/Swagger-OpenAPI_3.0-brightgreen?style=flat&logo=swagger)

Sistema de registro y onboarding para clientes personas físicas en una institución financiera. El proyecto permite capturar información personal, de contacto, laboral y de domicilio, aplicando validaciones estrictas y reglas de negocio, así como la asignación automática de una cuenta bancaria única con saldo inicial.

---

## Tabla de Contenidos

- [Características y Funcionalidades](#características-y-funcionalidades)
- [Arquitectura del Proyecto](#arquitectura-del-proyecto)
- [Tecnologías Utilizadas](#tecnologías-utilizadas)
- [Diagrama Entidad-Relación](#diagrama-entidad-relación)
- [Reglas de Negocio e Inmutabilidad](#reglas-de-negocio-e-inmutabilidad)
- [Requisitos Previos e Instalación](#requisitos-previos-e-instalación)
- [Endpoints de la API REST](#endpoints-de-la-api-rest)
- [Manejo de Excepciones](#manejo-de-excepciones)
- [Pruebas con Swagger UI](#pruebas-con-swagger-ui)

---

## Características y Funcionalidades

### 1. Registro de Clientes Personas Físicas

- Captura completa de datos personales, contacto, domicilio e información laboral.
- Creación automática de una cuenta bancaria única.
- Generación del número de cuenta con el formato `CTA-XXXXXXXXXX`.
- Asignación de un saldo inicial de `$1,000.00`.

### 2. Validaciones de Formato y Negocio

- Validación de CURP mediante expresiones regulares (`Regex`).
- Validación de RFC mediante expresiones regulares (`Regex`).
- Validación de correo electrónico.
- Validación de número telefónico.
- Verificación de mayoría de edad: 18 años o más.
- Unicidad garantizada para CURP, RFC y correo electrónico.

### 3. Mantenimiento e Inmutabilidad

El sistema permite actualizar información modificable del cliente, manteniendo protegidos los datos que identifican de forma única al registro.

**Campos inmutables:**

- CURP.
- RFC.
- Número de cuenta.

Estos campos no pueden modificarse mediante las operaciones de actualización.

### 4. Baja Lógica

La eliminación de clientes se realiza mediante una baja lógica:

- El cliente cambia su estado a `activo = false`.
- La cuenta bancaria asociada cambia a `estatus = INACTIVA`.
- Los registros permanecen almacenados físicamente en la base de datos.

### 5. Consultas Avanzadas

El sistema permite realizar consultas mediante diferentes criterios:

- ID del cliente.
- CURP.
- RFC.
- Correo electrónico.
- Estado activo/inactivo.
- Rango de fechas de registro.

---

## Arquitectura del Proyecto

El proyecto utiliza una arquitectura en capas (*Layered Architecture*) construida sobre Spring Boot. Esta separación permite mantener organizada la lógica de presentación, negocio, persistencia y configuración.

```text
com.proyecto.servicios
├── config/
│   └── Configuración de BD, Hibernate y Swagger/OpenAPI
│
├── controller/
│   └── Endpoints REST
│       ├── ClienteController
│       ├── CuentaController
│       └── GlobalExceptionHandler
│
├── entity/
│   └── onboarding/
│       ├── Cliente
│       ├── Domicilio
│       └── Cuenta
│
├── exception/
│   ├── BusinessException
│   └── ClienteNoEncontradoException
│
├── model/
│   └── onboarding/
│       └── DTOs y objetos de transferencia con validaciones
│
├── repositorys/
│   └── onboarding/
│       └── Repositorios JPA con Spring Data
│
└── service/
    ├── ClienteService
    └── ClienteServiceImpl
```

### Capas principales

| Capa | Responsabilidad |
|---|---|
| `controller` | Exponer los endpoints de la API REST y recibir las peticiones HTTP. |
| `service` | Contener la lógica de negocio y las reglas funcionales del sistema. |
| `repository` | Gestionar el acceso y persistencia de datos mediante Spring Data JPA. |
| `entity` | Representar las tablas y relaciones de la base de datos. |
| `model` | Contener DTOs utilizados para transferencia y validación de información. |
| `exception` | Definir y manejar excepciones propias del dominio. |
| `config` | Centralizar configuraciones de base de datos, Hibernate y documentación OpenAPI. |

---

## Tecnologías Utilizadas

| Tecnología | Versión / Uso |
|---|---|
| Java | 17+ |
| Spring Boot | 3.x |
| Spring Data JPA | Persistencia de datos |
| Hibernate | ORM |
| PostgreSQL | 15+ |
| Flyway | Migraciones de base de datos |
| SpringDoc OpenAPI | Documentación de API |
| Swagger UI | Pruebas y documentación interactiva |
| Lombok | Reducción de código repetitivo |
| Gradle | Gestión y construcción del proyecto |

---

## Diagrama Entidad-Relación

La base de datos utiliza el esquema `public` de PostgreSQL y cuenta con llaves foráneas y restricciones de unicidad para garantizar la integridad de la información.

```text
+-------------------------+             +-------------------------+
|       DOMICILIOS        |             |        CLIENTES         |
+-------------------------+             +-------------------------+
| PK  id                  |<------------| PK  id                  |
|     calle               |      1    1 |     nombre              |
|     numero_exterior     |             |     apellido_paterno    |
|     numero_interior     |             |     apellido_materno    |
|     colonia             |             | UK  curp                |
|     municipio           |             | UK  rfc                 |
|     estado              |             | UK  correo_electronico  |
|     codigo_postal       |             |     fecha_nacimiento    |
|     pais                |             |     activo              |
+-------------------------+             | FK  domicilio_id        |
                                        +-------------------------+
                                                   |
                                                   | 1
                                                   |
                                                   | 1
                                                   v
                                        +-------------------------+
                                        |         CUENTAS         |
                                        +-------------------------+
                                        | PK  id                  |
                                        | UK  numero_cuenta       |
                                        |     saldo               |
                                        |     estatus             |
                                        |     fecha_creacion      |
                                        | FK  cliente_id          |
                                        +-------------------------+
```

### Relaciones principales

- Un `Cliente` está asociado a un `Domicilio`.
- Un `Cliente` tiene una `Cuenta` bancaria.
- El número de cuenta es único.
- CURP, RFC y correo electrónico son únicos.
- La cuenta bancaria está vinculada directamente con el cliente mediante una llave foránea.

---

## Reglas de Negocio e Inmutabilidad

| Regla | Descripción |
|---|---|
| **Mayoría de edad** | El cliente debe tener 18 años o más, calculado a partir de su fecha de nacimiento. |
| **Unicidad** | No pueden existir dos clientes con el mismo CURP, RFC o correo electrónico. |
| **Cuenta automática** | Al registrar un cliente, el sistema asigna automáticamente una cuenta con estatus `ACTIVA` y saldo inicial de `$1,000.00`. |
| **Inmutabilidad** | En las peticiones `PUT /clientes/{id}`, los campos CURP, RFC y número de cuenta no pueden ser modificados. |
| **Baja lógica** | Al ejecutar `DELETE /clientes/{id}`, el cliente cambia a `activo = false` y su cuenta pasa a `INACTIVA`. |

---

## Requisitos Previos e Instalación

Antes de ejecutar el proyecto, asegúrate de contar con:

- Java 17 o superior.
- PostgreSQL 15 o superior.
- Gradle o el Gradle Wrapper incluido en el proyecto.
- IntelliJ IDEA u otro IDE compatible con Spring Boot.
- Git.

### 1. Clonar el repositorio

```bash
git clone <URL_DEL_REPOSITORIO>
cd prueba
```

### 2. Configurar PostgreSQL

Asegúrate de tener un servicio de PostgreSQL activo y configura las credenciales en:

```text
src/main/resources/application.properties
```

Ejemplo:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/tu_base_de_datos
spring.datasource.username=tu_usuario
spring.datasource.password=tu_contraseña
```

> Sustituye los valores de ejemplo por las credenciales correspondientes a tu entorno local.

### 3. Compilar el proyecto

En Windows PowerShell:

```powershell
.\gradlew clean build -x test
```

En Linux o macOS:

```bash
./gradlew clean build -x test
```

### 4. Ejecutar la aplicación

En Windows PowerShell:

```powershell
.\gradlew bootRun
```

En Linux o macOS:

```bash
./gradlew bootRun
```

También es posible ejecutar la aplicación directamente desde IntelliJ IDEA mediante la clase principal:

```text
com.proyecto.servicios.App
```

---

## Endpoints de la API REST

### Clientes

Base URL:

```text
/clientes
```

| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/clientes` | Registrar un nuevo cliente y crear su cuenta automáticamente. |
| `GET` | `/clientes` | Consultar la lista completa de clientes. |
| `GET` | `/clientes/{id}` | Buscar un cliente por su ID único. |
| `GET` | `/clientes/curp/{curp}` | Buscar un cliente por CURP. |
| `GET` | `/clientes/rfc/{rfc}` | Buscar un cliente por RFC. |
| `GET` | `/clientes/correo/{correo}` | Buscar un cliente por correo electrónico. |
| `GET` | `/clientes/activos` | Consultar únicamente los clientes activos. |
| `GET` | `/clientes/rango-fechas` | Buscar clientes registrados entre una fecha inicial y una fecha final. |
| `PUT` | `/clientes/{id}` | Actualizar información del cliente manteniendo CURP y RFC inmutables. |
| `DELETE` | `/clientes/{id}` | Ejecutar la baja lógica del cliente y desactivar su cuenta. |

### Cuentas

Base URL:

```text
/cuentas
```

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/cuentas/{numeroCuenta}` | Consultar el detalle de una cuenta bancaria. |
| `GET` | `/cuentas/{numeroCuenta}/saldo` | Consultar el saldo disponible de una cuenta. |
| `GET` | `/cuentas/activas` | Consultar todas las cuentas con estatus `ACTIVA`. |

---

## Manejo de Excepciones

Las respuestas de error están estandarizadas mediante el componente `GlobalExceptionHandler`.

### `400 Bad Request` — Error de validación

Se utiliza cuando los datos enviados no cumplen con las validaciones definidas mediante Jakarta Bean Validation, por ejemplo:

- `@NotBlank`
- `@Pattern`
- `@Past`
- Otras validaciones configuradas en los DTOs.

La respuesta contiene información sobre los campos que no cumplen con los requisitos establecidos.

### `400 Bad Request` — Error de regla de negocio

Se utiliza cuando se incumple una regla propia del sistema, por ejemplo:

- CURP duplicada.
- RFC duplicado.
- Correo electrónico duplicado.
- Cliente menor de edad.
- Incumplimiento de alguna otra regla de negocio.

Estos casos son manejados mediante `BusinessException`.

### `404 Not Found` — Recurso no encontrado

Se utiliza cuando no existe el recurso solicitado, por ejemplo:

- ID de cliente inexistente.
- CURP no encontrada.
- RFC no encontrado.
- Número de cuenta inexistente.

Estos casos son manejados mediante `ClienteNoEncontradoException`.

---

## Pruebas con Swagger UI

La API cuenta con documentación interactiva mediante **Swagger UI**, permitiendo consultar los endpoints disponibles y realizar peticiones directamente desde el navegador.

Una vez que la aplicación se encuentre ejecutándose, accede a:

```text
http://localhost:8080/swagger-ui/index.html
```

Desde Swagger UI es posible:

1. Consultar todos los endpoints disponibles.
2. Revisar los parámetros requeridos.
3. Visualizar los modelos de datos.
4. Ejecutar peticiones `GET`, `POST`, `PUT` y `DELETE`.
5. Revisar las respuestas HTTP.
6. Probar las validaciones y reglas de negocio.

---

## Flujo General del Registro

El proceso de alta de un cliente sigue el siguiente flujo:

```text
Cliente
   |
   v
POST /clientes
   |
   v
Validación de datos
   |
   +----> Datos inválidos ----> 400 Bad Request
   |
   v
Validación de reglas de negocio
   |
   +----> Regla incumplida ----> 400 Bad Request
   |
   v
Guardar domicilio
   |
   v
Guardar cliente
   |
   v
Generar número de cuenta
   |
   v
Crear cuenta ACTIVA
   |
   v
Asignar saldo inicial
$1,000.00
   |
   v
Respuesta exitosa
```

---

## Consideraciones de Seguridad y Configuración

Para evitar exponer información sensible, no se recomienda subir al repositorio contraseñas reales de PostgreSQL ni credenciales de servicios externos.

Se recomienda utilizar variables de entorno para configuraciones sensibles en ambientes de desarrollo, pruebas y producción.

Ejemplo:

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
```

Asimismo, se recomienda agregar archivos locales o información sensible al `.gitignore`.

---

## Estado del Proyecto

Proyecto integrador desarrollado como una API REST para la gestión del proceso de onboarding de clientes personas físicas.

La solución implementa:

- Registro y consulta de clientes.
- Validaciones de datos.
- Reglas de negocio.
- Persistencia con PostgreSQL.
- Migraciones mediante Flyway.
- Generación automática de cuentas.
- Manejo de excepciones.
- Baja lógica.
- Inmutabilidad de información crítica.
- Documentación y pruebas mediante Swagger UI.
