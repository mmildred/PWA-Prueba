package com.proyecto.servicios.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.transaccion.EstatusTransaccion;
import com.proyecto.servicios.exception.SaldoInsuficienteException;
import com.proyecto.servicios.exception.TransaccionDuplicadaException;
import com.proyecto.servicios.exception.TransaccionNoEncontradaException;
import com.proyecto.servicios.model.transaccion.ConciliarTransaccionRequest;
import com.proyecto.servicios.model.transaccion.PagarServicioRequest;
import com.proyecto.servicios.model.transaccion.TransaccionResponse;
import com.proyecto.servicios.service.TransaccionService;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TransaccionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TransaccionService transaccionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        TransaccionController controller = new TransaccionController(transaccionService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /transacciones/pagar debe retornar 200 OK con el resultado de la transacción")
    void pagarServicio_Exito_Retorna200() throws Exception {
        PagarServicioRequest request = PagarServicioRequest.builder()
                .numeroCuenta("CTA-1234567890")
                .clienteId(1L)
                .idServicio("TELEFONIA")
                .idProducto("PROD-01")
                .monto(new BigDecimal("50.00"))
                .referencia("5512345678")
                .idempotencyKey("IDEMP-KEY-TEST-001")
                .build();

        TransaccionResponse mockResponse = TransaccionResponse.builder()
                .folio("550e8400-e29b-41d4-a716-446655440000")
                .idempotencyKey("IDEMP-KEY-TEST-001")
                .numeroCuenta("CTA-******7890")
                .clienteId(1L)
                .idServicio("TELEFONIA")
                .idProducto("PROD-01")
                .nombreProducto("Recarga Telcel 50")
                .monto(new BigDecimal("50.00"))
                .referencia("******5678")
                .estatus(EstatusTransaccion.COMPLETADA)
                .saldoAnterior(new BigDecimal("1000.00"))
                .saldoPosterior(new BigDecimal("950.00"))
                .folioProveedor("GP-999888")
                .autorizacionProveedor("AUTH-123456")
                .mensaje("TRANSACCION EXITOSA")
                .fechaHora(LocalDateTime.now())
                .build();

        when(transaccionService.procesarPagoServicio(any())).thenReturn(mockResponse);

        mockMvc.perform(post("/transacciones/pagar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folio").value("550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(jsonPath("$.estatus").value("COMPLETADA"))
                .andExpect(jsonPath("$.autorizacionProveedor").value("AUTH-123456"))
                .andExpect(jsonPath("$.saldoPosterior").value(950.00));
    }

    @Test
    @DisplayName("POST /transacciones/pagar debe retornar 400 Bad Request si faltan campos obligatorios")
    void pagarServicio_ParametrosInvalidos_Retorna400() throws Exception {
        PagarServicioRequest requestInvalido = PagarServicioRequest.builder()
                .numeroCuenta("") // vacío
                .monto(new BigDecimal("-10.00")) // negativo
                .build();

        mockMvc.perform(post("/transacciones/pagar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Error de Validación"));
    }

    @Test
    @DisplayName("POST /transacciones/pagar debe retornar 409 Conflict si la transacción es duplicada")
    void pagarServicio_Duplicada_Retorna409() throws Exception {
        PagarServicioRequest request = PagarServicioRequest.builder()
                .numeroCuenta("CTA-1234567890")
                .clienteId(1L)
                .idServicio("TELEFONIA")
                .idProducto("PROD-01")
                .monto(new BigDecimal("50.00"))
                .referencia("5512345678")
                .idempotencyKey("IDEMP-KEY-DUPLICADA")
                .build();

        when(transaccionService.procesarPagoServicio(any()))
                .thenThrow(new TransaccionDuplicadaException("La transacción con clave de idempotencia ya está en proceso."));

        mockMvc.perform(post("/transacciones/pagar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST /transacciones/pagar debe retornar 422 Unprocessable Entity si no hay saldo suficiente")
    void pagarServicio_SaldoInsuficiente_Retorna422() throws Exception {
        PagarServicioRequest request = PagarServicioRequest.builder()
                .numeroCuenta("CTA-1234567890")
                .clienteId(1L)
                .idServicio("TELEFONIA")
                .idProducto("PROD-01")
                .monto(new BigDecimal("5000.00"))
                .referencia("5512345678")
                .idempotencyKey("IDEMP-KEY-SALDO")
                .build();

        when(transaccionService.procesarPagoServicio(any()))
                .thenThrow(new SaldoInsuficienteException("Saldo insuficiente en la cuenta."));

        mockMvc.perform(post("/transacciones/pagar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    @DisplayName("GET /transacciones/{folio} debe retornar 200 OK con la transacción")
    void consultarPorFolio_Exito_Retorna200() throws Exception {
        TransaccionResponse mockResponse = TransaccionResponse.builder()
                .folio("FOLIO-123")
                .estatus(EstatusTransaccion.COMPLETADA)
                .monto(new BigDecimal("100.00"))
                .build();

        when(transaccionService.consultarPorFolio("FOLIO-123")).thenReturn(mockResponse);

        mockMvc.perform(get("/transacciones/FOLIO-123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folio").value("FOLIO-123"))
                .andExpect(jsonPath("$.monto").value(100.00));
    }

    @Test
    @DisplayName("GET /transacciones/{folio} debe retornar 404 Not Found si no existe el folio")
    void consultarPorFolio_NoExiste_Retorna404() throws Exception {
        when(transaccionService.consultarPorFolio("FOLIO-INEXISTENTE"))
                .thenThrow(new TransaccionNoEncontradaException("Transacción no encontrada"));

        mockMvc.perform(get("/transacciones/FOLIO-INEXISTENTE")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /transacciones/{folio}/conciliar debe retornar 200 OK con la transacción conciliada")
    void conciliar_Exito_Retorna200() throws Exception {
        ConciliarTransaccionRequest req = ConciliarTransaccionRequest.builder()
                .accion("CONFIRMAR")
                .folioProveedor("GP-CONCIL-123")
                .autorizacion("AUTH-CONCIL-456")
                .observaciones("Validado en portal externo")
                .build();

        TransaccionResponse mockResponse = TransaccionResponse.builder()
                .folio("FOLIO-PENDIENTE")
                .estatus(EstatusTransaccion.COMPLETADA)
                .folioProveedor("GP-CONCIL-123")
                .autorizacionProveedor("AUTH-CONCIL-456")
                .build();

        when(transaccionService.conciliarTransaccion(eq("FOLIO-PENDIENTE"), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/transacciones/FOLIO-PENDIENTE/conciliar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folio").value("FOLIO-PENDIENTE"))
                .andExpect(jsonPath("$.estatus").value("COMPLETADA"))
                .andExpect(jsonPath("$.autorizacionProveedor").value("AUTH-CONCIL-456"));
    }
}
