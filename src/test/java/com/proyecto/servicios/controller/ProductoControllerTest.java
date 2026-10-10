package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.ServiceAuthenticationException;
import com.proyecto.servicios.exception.ServiceCommunicationException;
import com.proyecto.servicios.exception.ServiceResponseException;
import com.proyecto.servicios.exception.ServiceTimeoutException;
import com.proyecto.servicios.model.producto.ProductoDTO;
import com.proyecto.servicios.model.producto.ProductoListResponse;
import com.proyecto.servicios.service.ProductoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductoControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProductoService productoService;

    @BeforeEach
    void setUp() {
        ProductoController controller = new ProductoController(productoService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /productos debe retornar 200 OK con la lista de productos")
    void obtenerProductos_Exito_Retorna200() throws Exception {
        ProductoListResponse mockResponse = ProductoListResponse.builder()
                .status(200)
                .message("OK")
                .productos(List.of(
                        ProductoDTO.builder()
                                .id("P1")
                                .sku("SKU1")
                                .nombre("Producto Test")
                                .precio(BigDecimal.valueOf(99.99))
                                .build()
                ))
                .build();

        when(productoService.obtenerListaProductos()).thenReturn(mockResponse);

        mockMvc.perform(get("/productos")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.productos[0].id").value("P1"))
                .andExpect(jsonPath("$.productos[0].nombre").value("Producto Test"));
    }

    @Test
    @DisplayName("GET /productos debe retornar 401 Unauthorized ante error de token")
    void obtenerProductos_ErrorAutenticacion_Retorna401() throws Exception {
        when(productoService.obtenerListaProductos())
                .thenThrow(new ServiceAuthenticationException("Token inválido o expirado"));

        mockMvc.perform(get("/productos")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value(401))
                .andExpect(jsonPath("$.mensaje").value("Fallo de autenticación con el servicio externo"));
    }

    @Test
    @DisplayName("GET /productos debe retornar 504 Gateway Timeout ante timeout")
    void obtenerProductos_Timeout_Retorna504() throws Exception {
        when(productoService.obtenerListaProductos())
                .thenThrow(new ServiceTimeoutException("Timeout de conexión"));

        mockMvc.perform(get("/productos")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.codigo").value(504))
                .andExpect(jsonPath("$.mensaje").value("Tiempo de espera agotado al consultar el servicio externo"));
    }

    @Test
    @DisplayName("GET /productos debe retornar 503 Service Unavailable ante fallo de comunicación")
    void obtenerProductos_FalloComunicacion_Retorna503() throws Exception {
        when(productoService.obtenerListaProductos())
                .thenThrow(new ServiceCommunicationException("Host inalcanzable"));

        mockMvc.perform(get("/productos")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value(503))
                .andExpect(jsonPath("$.mensaje").value("Servicio externo no disponible temporalmente"));
    }

    @Test
    @DisplayName("GET /productos debe retornar 502 Bad Gateway ante respuesta no exitosa del servicio")
    void obtenerProductos_RespuestaNoExitosa_Retorna502() throws Exception {
        when(productoService.obtenerListaProductos())
                .thenThrow(new ServiceResponseException("Error 500 del servicio remoto", 500));

        mockMvc.perform(get("/productos")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.codigo").value(502))
                .andExpect(jsonPath("$.mensaje").value("Respuesta no exitosa del servicio externo"));
    }
}
