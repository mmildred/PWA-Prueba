package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoProductoClient;
import com.proyecto.servicios.exception.ExternalServiceException;
import com.proyecto.servicios.exception.ServiceAuthenticationException;
import com.proyecto.servicios.exception.ServiceCommunicationException;
import com.proyecto.servicios.exception.ServiceResponseException;
import com.proyecto.servicios.exception.ServiceTimeoutException;
import com.proyecto.servicios.model.producto.ProductoListResponse;
import feign.FeignException;
import feign.RetryableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;

@Service
@Slf4j
public class ProductoServiceImpl implements com.proyecto.servicios.service.ProductoService {

    private final GestoPagoProductoClient productoClient;
    private final String apiToken;

    public ProductoServiceImpl(
            GestoPagoProductoClient productoClient,
            @Value("${gestopago.productos.token:}") String apiToken
    ) {
        this.productoClient = productoClient;
        this.apiToken = apiToken;
    }

    @Override
    public ProductoListResponse obtenerListaProductos() {
        log.info("Iniciando invocación a servicio externo: [GET /sistema/service/getProductList.do]");

        String bearerHeader = prepararBearerToken();

        try {
            ProductoListResponse response = productoClient.getProductList(bearerHeader);

            if (response == null) {
                log.error("El servicio externo retornó un cuerpo de respuesta nulo");
                throw new ServiceResponseException("Respuesta vacía del servicio externo", 502);
            }

            int cantidadProductos = response.getProductos() != null ? response.getProductos().size() : 0;
            log.info("Finalizando invocación a servicio externo con éxito. Total productos obtenidos: {}", cantidadProductos);

            return response;

        } catch (ExternalServiceException ese) {
            // Excepciones ya procesadas y tipadas por FeignErrorDecoder
            log.error("Error controlado en servicio de productos: tipo={}, mensaje={}",
                    ese.getClass().getSimpleName(), ese.getMessage());
            throw ese;

        } catch (RetryableException re) {
            log.error("Timeout o error de reintento al conectar con el servicio externo de productos: {}",
                    sanitizeLogMessage(re.getMessage()));
            if (re.getCause() instanceof SocketTimeoutException) {
                throw new ServiceTimeoutException("Tiempo de espera agotado al conectar con el servicio de productos", re);
            }
            throw new ServiceCommunicationException("Fallo en la comunicación con el servicio externo de productos", re);

        } catch (FeignException.Unauthorized | FeignException.Forbidden feAuth) {
            log.error("Fallo de autenticación con el servicio externo: status={}", feAuth.status());
            throw new ServiceAuthenticationException("Error de autenticación con el servicio externo", feAuth);

        } catch (FeignException fe) {
            log.error("Error HTTP al consumir el servicio externo: status={}, mensaje={}",
                    fe.status(), sanitizeLogMessage(fe.getMessage()));
            throw new ServiceResponseException("Error al consumir el servicio externo de productos", fe.status());

        } catch (Exception ex) {
            log.error("Excepción inesperada al comunicarse con el servicio de productos: {}",
                    sanitizeLogMessage(ex.getMessage()));
            throw new ServiceCommunicationException("Error inesperado en la comunicación con el servicio externo", ex);
        }
    }

    /**
     * Valida y formatea el Bearer Token configurado sin exponerlo en logs.
     */
    private String prepararBearerToken() {
        if (apiToken == null || apiToken.trim().isEmpty()) {
            log.error("No se encontró el Bearer Token configurado en 'gestopago.productos.token'");
            throw new ServiceAuthenticationException("Bearer Token no configurado en la aplicación");
        }

        String trimmed = apiToken.trim();
        return trimmed.regionMatches(true, 0, "Bearer ", 0, 7) ? trimmed : "Bearer " + trimmed;
    }

    /**
     * Enmascara posibles credenciales o tokens en los mensajes de log.
     */
    private String sanitizeLogMessage(String message) {
        if (message == null) {
            return "";
        }
        return message.replaceAll("(?i)(bearer\\s+[a-zA-Z0-9._-]+)", "Bearer ***")
                .replaceAll("(?i)(password|token|secret)=[^&\\s]+", "$1=***");
    }
}
