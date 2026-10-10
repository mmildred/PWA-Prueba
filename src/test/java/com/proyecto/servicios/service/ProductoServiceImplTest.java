package com.proyecto.servicios.service;

import com.proyecto.servicios.client.GestoPagoProductoClient;
import com.proyecto.servicios.exception.ServiceAuthenticationException;
import com.proyecto.servicios.exception.ServiceCommunicationException;
import com.proyecto.servicios.exception.ServiceResponseException;
import com.proyecto.servicios.exception.ServiceTimeoutException;
import com.proyecto.servicios.model.producto.ProductoDTO;
import com.proyecto.servicios.model.producto.ProductoListResponse;
import com.proyecto.servicios.service.Impl.ProductoServiceImpl;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductoServiceImplTest {

    @Mock
    private GestoPagoProductoClient productoClient;

    private final String testToken = "sample-secret-jwt-token-12345";

    private ProductoServiceImpl productoService;

    @BeforeEach
    void setUp() {
        productoService = new ProductoServiceImpl(productoClient, testToken);
    }

    @Test
    @DisplayName("Debe retornar lista de productos correctamente en escenario exitoso")
    void obtenerListaProductos_Exitoso() {
        // Arrange
        ProductoDTO item1 = ProductoDTO.builder()
                .id("PROD-01")
                .sku("SKU-100")
                .nombre("Recarga Telefónica")
                .descripcion("Recarga de saldo")
                .precio(BigDecimal.valueOf(50.00))
                .categoria("Tiempo Aire")
                .activo(true)
                .build();

        ProductoListResponse expectedResponse = ProductoListResponse.builder()
                .status(200)
                .message("OK")
                .productos(List.of(item1))
                .build();

        when(productoClient.getProductList("Bearer " + testToken)).thenReturn(expectedResponse);

        // Act
        ProductoListResponse actualResponse = productoService.obtenerListaProductos();

        // Assert
        assertNotNull(actualResponse);
        assertEquals(200, actualResponse.getStatus());
        assertEquals("OK", actualResponse.getMessage());
        assertEquals(1, actualResponse.getProductos().size());
        assertEquals("PROD-01", actualResponse.getProductos().get(0).getId());
        assertEquals("Recarga Telefónica", actualResponse.getProductos().get(0).getNombre());

        verify(productoClient, times(1)).getProductList("Bearer " + testToken);
    }

    @Test
    @DisplayName("Debe manejar token que ya contiene prefijo 'Bearer ' sin duplicarlo")
    void obtenerListaProductos_ConPrefijoBearerEnConfiguracion_Exitoso() {
        // Arrange
        String tokenWithBearer = "Bearer sample-token-with-prefix";
        ProductoServiceImpl serviceWithBearer = new ProductoServiceImpl(productoClient, tokenWithBearer);

        ProductoListResponse mockResponse = ProductoListResponse.builder()
                .status(200)
                .message("OK")
                .productos(Collections.emptyList())
                .build();

        when(productoClient.getProductList(tokenWithBearer)).thenReturn(mockResponse);

        // Act
        ProductoListResponse response = serviceWithBearer.obtenerListaProductos();

        // Assert
        assertNotNull(response);
        verify(productoClient, times(1)).getProductList(tokenWithBearer);
    }

    @Test
    @DisplayName("Debe lanzar ServiceAuthenticationException si el token no está configurado")
    void obtenerListaProductos_TokenNoConfigurado_LanzaExcepcion() {
        // Arrange
        ProductoServiceImpl serviceSinToken = new ProductoServiceImpl(productoClient, null);

        // Act & Assert
        ServiceAuthenticationException ex = assertThrows(
                ServiceAuthenticationException.class,
                serviceSinToken::obtenerListaProductos
        );

        assertTrue(ex.getMessage().contains("Bearer Token no configurado"));
        verifyNoInteractions(productoClient);
    }

    @Test
    @DisplayName("Debe lanzar ServiceAuthenticationException si el cliente responde error 401 Unauthorized")
    void obtenerListaProductos_ErrorAutenticacion401_LanzaExcepcion() {
        // Arrange
        when(productoClient.getProductList(anyString()))
                .thenThrow(new ServiceAuthenticationException("Error de autenticación con el servicio externo"));

        // Act & Assert
        assertThrows(
                ServiceAuthenticationException.class,
                () -> productoService.obtenerListaProductos()
        );

        verify(productoClient, times(1)).getProductList(anyString());
    }

    @Test
    @DisplayName("Debe lanzar ServiceTimeoutException si ocurre un SocketTimeoutException")
    void obtenerListaProductos_TimeoutSocket_LanzaServiceTimeoutException() {
        // Arrange
        Request mockRequest = Request.create(
                Request.HttpMethod.GET,
                "/sistema/service/getProductList.do",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8,
                null
        );

        RetryableException retryableException = new RetryableException(
                504,
                "Read timed out",
                Request.HttpMethod.GET,
                new SocketTimeoutException("Read timed out"),
                new Date(),
                mockRequest
        );

        when(productoClient.getProductList(anyString())).thenThrow(retryableException);

        // Act & Assert
        ServiceTimeoutException ex = assertThrows(
                ServiceTimeoutException.class,
                () -> productoService.obtenerListaProductos()
        );

        assertTrue(ex.getMessage().contains("Tiempo de espera agotado"));
    }

    @Test
    @DisplayName("Debe lanzar ServiceCommunicationException si ocurre un fallo de conexión")
    void obtenerListaProductos_FalloComunicacion_LanzaServiceCommunicationException() {
        // Arrange
        when(productoClient.getProductList(anyString()))
                .thenThrow(new ServiceCommunicationException("Fallo de conexión al host"));

        // Act & Assert
        ServiceCommunicationException ex = assertThrows(
                ServiceCommunicationException.class,
                () -> productoService.obtenerListaProductos()
        );

        assertTrue(ex.getMessage().contains("Fallo de conexión"));
    }

    @Test
    @DisplayName("Debe lanzar ServiceResponseException cuando el servicio retorna status 500")
    void obtenerListaProductos_Error500Servidor_LanzaServiceResponseException() {
        // Arrange
        when(productoClient.getProductList(anyString()))
                .thenThrow(new ServiceResponseException("Error interno en servidor externo", 500));

        // Act & Assert
        ServiceResponseException ex = assertThrows(
                ServiceResponseException.class,
                () -> productoService.obtenerListaProductos()
        );

        assertEquals(500, ex.getRawStatusCode());
    }

    @Test
    @DisplayName("Debe lanzar ServiceResponseException si el cliente retorna cuerpo nulo")
    void obtenerListaProductos_CuerpoNulo_LanzaServiceResponseException() {
        // Arrange
        when(productoClient.getProductList(anyString())).thenReturn(null);

        // Act & Assert
        ServiceResponseException ex = assertThrows(
                ServiceResponseException.class,
                () -> productoService.obtenerListaProductos()
        );

        assertEquals(502, ex.getRawStatusCode());
        assertTrue(ex.getMessage().contains("Respuesta vacía"));
    }
}
