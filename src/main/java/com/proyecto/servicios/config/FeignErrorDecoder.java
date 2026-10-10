package com.proyecto.servicios.config;

import com.proyecto.servicios.exception.ServiceAuthenticationException;
import com.proyecto.servicios.exception.ServiceCommunicationException;
import com.proyecto.servicios.exception.ServiceResponseException;
import com.proyecto.servicios.exception.ServiceTimeoutException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
public class FeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        String responseBody = extractBody(response);

        log.error("Error al invocar servicio externo [{}]. Código HTTP: {}, Motivo: {}",
                methodKey, status, sanitize(response.reason()));

        switch (status) {
            case 401:
                return new ServiceAuthenticationException(
                        "Error de autenticación con el servicio externo (401 Unauthorized)", HttpStatus.UNAUTHORIZED);
            case 403:
                return new ServiceAuthenticationException(
                        "Acceso prohibido al servicio externo (403 Forbidden)", HttpStatus.FORBIDDEN);
            case 408:
            case 504:
                return new ServiceTimeoutException(
                        "Tiempo de espera agotado al comunicarse con el servicio externo (" + status + ")");
            default:
                if (status >= 400 && status < 500) {
                    return new ServiceResponseException(
                            "Error del cliente al consumir el servicio externo (" + status + "): " + sanitize(responseBody),
                            status
                    );
                }
                if (status >= 500) {
                    return new ServiceResponseException(
                            "Error interno en el servidor del servicio externo (" + status + "): " + sanitize(responseBody),
                            status
                    );
                }
                return new ServiceCommunicationException(
                        "Error de comunicación inesperado con el servicio externo (" + status + ")"
                );
        }
    }

    private String extractBody(Response response) {
        if (response.body() != null) {
            try (InputStream inputStream = response.body().asInputStream()) {
                byte[] bytes = inputStream.readAllBytes();
                return new String(bytes, StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("No fue posible leer el cuerpo de error de la respuesta: {}", e.getMessage());
            }
        }
        return "Sin detalle en el cuerpo";
    }

    /**
     * Sanitiza mensajes para evitar fuga de información sensible en logs
     */
    private String sanitize(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("(?i)(password|token|secret|authorization)=[^&\\s]+", "$1=***");
    }
}
