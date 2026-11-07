package com.proyecto.servicios.service;

import com.proyecto.servicios.client.GestoPagoTxClient;
import com.proyecto.servicios.entity.onboarding.Cliente;
import com.proyecto.servicios.entity.onboarding.Cuenta;
import com.proyecto.servicios.entity.transaccion.EstatusTransaccion;
import com.proyecto.servicios.entity.transaccion.Transaccion;
import com.proyecto.servicios.exception.BusinessException;
import com.proyecto.servicios.exception.SaldoInsuficienteException;
import com.proyecto.servicios.exception.ServiceCommunicationException;
import com.proyecto.servicios.exception.ServiceTimeoutException;
import com.proyecto.servicios.exception.TransaccionDuplicadaException;
import com.proyecto.servicios.exception.TransaccionNoEncontradaException;
import com.proyecto.servicios.model.producto.ProductoDTO;
import com.proyecto.servicios.model.producto.ProductoListResponse;
import com.proyecto.servicios.model.transaccion.ConciliarTransaccionRequest;
import com.proyecto.servicios.model.transaccion.GestoPagoTxResponse;
import com.proyecto.servicios.model.transaccion.PagarServicioRequest;
import com.proyecto.servicios.model.transaccion.TransaccionResponse;
import com.proyecto.servicios.repositorys.onboarding.CuentaRepository;
import com.proyecto.servicios.repositorys.transaccion.TransaccionRepository;
import com.proyecto.servicios.service.Impl.TransaccionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransaccionServiceImplTest {

    @Mock
    private TransaccionRepository transaccionRepository;

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private ProductoService productoService;

    @Mock
    private GestoPagoTxClient gestoPagoTxClient;

    @Mock
    private GestoPagoTokenService gestoPagoTokenService;

    private TransaccionServiceImpl transaccionService;

    private Cliente clienteMock;
    private Cuenta cuentaMock;
    private PagarServicioRequest requestValido;

    @BeforeEach
    void setUp() {
        transaccionService = new TransaccionServiceImpl(
                transaccionRepository,
                cuentaRepository,
                productoService,
                gestoPagoTxClient,
                gestoPagoTokenService
        );

        ReflectionTestUtils.setField(transaccionService, "configuredToken", "test-bearer-token-12345");
        ReflectionTestUtils.setField(transaccionService, "idDistribuidor", 83);
        ReflectionTestUtils.setField(transaccionService, "codigoDispositivo", "GPS83-TPV-17");

        clienteMock = Cliente.builder()
                .id(1L)
                .nombre("Juan")
                .apellidoPaterno("Pérez")
                .apellidoMaterno("López")
                .activo(true)
                .build();

        cuentaMock = Cuenta.builder()
                .id(10L)
                .numeroCuenta("CTA-1234567890")
                .saldo(new BigDecimal("1000.00"))
                .estatus("ACTIVA")
                .cliente(clienteMock)
                .build();

        requestValido = PagarServicioRequest.builder()
                .numeroCuenta("CTA-1234567890")
                .clienteId(1L)
                .idServicio("TELEFONIA")
                .idProducto("PROD-01")
                .monto(new BigDecimal("50.00"))
                .referencia("5512345678")
                .idempotencyKey("IDEMP-KEY-TEST-001")
                .build();
    }

    @Test
    @DisplayName("Procesamiento exitoso: debita saldo, llama a Gestopago y completa transacción")
    void procesarPagoServicio_Exitoso() {
        // Mock catalogo
        ProductoDTO prod = ProductoDTO.builder()
                .id("PROD-01")
                .nombre("Recarga Telcel 50")
                .precio(new BigDecimal("50.00"))
                .activo(true)
                .build();
        when(productoService.obtenerListaProductos()).thenReturn(
                ProductoListResponse.builder().productos(List.of(prod)).build()
        );

        when(transaccionRepository.findByIdempotencyKey("IDEMP-KEY-TEST-001")).thenReturn(Optional.empty());
        when(cuentaRepository.findByNumeroCuentaForUpdate("CTA-1234567890")).thenReturn(Optional.of(cuentaMock));

        when(transaccionRepository.saveAndFlush(any(Transaccion.class))).thenAnswer(invocation -> {
            Transaccion t = invocation.getArgument(0);
            t.setId(100L);
            return t;
        });

        GestoPagoTxResponse responseGp = GestoPagoTxResponse.builder()
                .status(0)
                .message("TRANSACCION EXITOSA")
                .folio("GP-999888")
                .autorizacion("AUTH-123456")
                .build();
        when(gestoPagoTxClient.sendTx(anyString(), any())).thenReturn(responseGp);

        // Act
        TransaccionResponse response = transaccionService.procesarPagoServicio(requestValido);

        // Assert
        assertNotNull(response);
        assertEquals(EstatusTransaccion.COMPLETADA, response.getEstatus());
        assertEquals("GP-999888", response.getFolioProveedor());
        assertEquals("AUTH-123456", response.getAutorizacionProveedor());
        assertEquals(new BigDecimal("950.00"), cuentaMock.getSaldo()); // 1000 - 50

        verify(cuentaRepository, times(1)).save(cuentaMock);
        verify(transaccionRepository, atLeastOnce()).save(any(Transaccion.class));
    }

    @Test
    @DisplayName("Idempotencia: Solicitud con idempotencyKey ya COMPLETADA retorna respuesta previa sin cobrar doble")
    void procesarPagoServicio_Idempotente_RetornaTransaccionPrevia() {
        Transaccion previa = Transaccion.builder()
                .id(99L)
                .folio("FOLIO-PREVIO-001")
                .idempotencyKey("IDEMP-KEY-TEST-001")
                .numeroCuenta("CTA-1234567890")
                .cliente(clienteMock)
                .idServicio("TELEFONIA")
                .idProducto("PROD-01")
                .monto(new BigDecimal("50.00"))
                .referencia("5512345678")
                .estatus(EstatusTransaccion.COMPLETADA)
                .saldoAnterior(new BigDecimal("1000.00"))
                .saldoPosterior(new BigDecimal("950.00"))
                .folioProveedor("GP-EXISTENTE")
                .autorizacionProveedor("AUTH-EXISTENTE")
                .fechaCreacion(LocalDateTime.now())
                .build();

        when(transaccionRepository.findByIdempotencyKey("IDEMP-KEY-TEST-001")).thenReturn(Optional.of(previa));

        // Act
        TransaccionResponse response = transaccionService.procesarPagoServicio(requestValido);

        // Assert
        assertNotNull(response);
        assertEquals("FOLIO-PREVIO-001", response.getFolio());
        assertEquals(EstatusTransaccion.COMPLETADA, response.getEstatus());

        // Garantizar que no se volvió a debitar ni a llamar a Gestopago
        verifyNoInteractions(cuentaRepository);
        verifyNoInteractions(gestoPagoTxClient);
    }

    @Test
    @DisplayName("Idempotencia: Solicitud con transacción en EN_PROCESO lanza TransaccionDuplicadaException")
    void procesarPagoServicio_Idempotente_EnProceso_LanzaTransaccionDuplicadaException() {
        Transaccion previa = Transaccion.builder()
                .id(99L)
                .folio("FOLIO-EN-PROCESO")
                .idempotencyKey("IDEMP-KEY-TEST-001")
                .estatus(EstatusTransaccion.EN_PROCESO)
                .build();

        when(transaccionRepository.findByIdempotencyKey("IDEMP-KEY-TEST-001")).thenReturn(Optional.of(previa));

        assertThrows(TransaccionDuplicadaException.class, () ->
                transaccionService.procesarPagoServicio(requestValido)
        );

        verifyNoInteractions(gestoPagoTxClient);
    }

    @Test
    @DisplayName("Validación cuenta: lanza BusinessException si la cuenta no existe")
    void procesarPagoServicio_CuentaNoExiste_LanzaBusinessException() {
        when(transaccionRepository.findByIdempotencyKey("IDEMP-KEY-TEST-001")).thenReturn(Optional.empty());
        when(productoService.obtenerListaProductos()).thenReturn(ProductoListResponse.builder().build());
        when(cuentaRepository.findByNumeroCuentaForUpdate("CTA-1234567890")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                transaccionService.procesarPagoServicio(requestValido)
        );
        assertTrue(ex.getMessage().contains("no existe en el sistema"));
    }

    @Test
    @DisplayName("Validación cuenta: lanza BusinessException si la cuenta está INACTIVA")
    void procesarPagoServicio_CuentaInactiva_LanzaBusinessException() {
        cuentaMock.setEstatus("INACTIVA");
        when(transaccionRepository.findByIdempotencyKey("IDEMP-KEY-TEST-001")).thenReturn(Optional.empty());
        when(productoService.obtenerListaProductos()).thenReturn(ProductoListResponse.builder().build());
        when(cuentaRepository.findByNumeroCuentaForUpdate("CTA-1234567890")).thenReturn(Optional.of(cuentaMock));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                transaccionService.procesarPagoServicio(requestValido)
        );
        assertTrue(ex.getMessage().contains("no se encuentra ACTIVA"));
    }

    @Test
    @DisplayName("Validación autorización: lanza BusinessException si el cliente no es el titular")
    void procesarPagoServicio_ClienteNoAutorizado_LanzaBusinessException() {
        requestValido.setClienteId(999L); // ID diferente al del mock (1L)
        when(transaccionRepository.findByIdempotencyKey("IDEMP-KEY-TEST-001")).thenReturn(Optional.empty());
        when(productoService.obtenerListaProductos()).thenReturn(ProductoListResponse.builder().build());
        when(cuentaRepository.findByNumeroCuentaForUpdate("CTA-1234567890")).thenReturn(Optional.of(cuentaMock));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                transaccionService.procesarPagoServicio(requestValido)
        );
        assertTrue(ex.getMessage().contains("no está autorizado"));
    }

    @Test
    @DisplayName("Validación cliente: lanza BusinessException si el cliente se encuentra inactivo")
    void procesarPagoServicio_ClienteInactivo_LanzaBusinessException() {
        clienteMock.setActivo(false);
        when(transaccionRepository.findByIdempotencyKey("IDEMP-KEY-TEST-001")).thenReturn(Optional.empty());
        when(productoService.obtenerListaProductos()).thenReturn(ProductoListResponse.builder().build());
        when(cuentaRepository.findByNumeroCuentaForUpdate("CTA-1234567890")).thenReturn(Optional.of(cuentaMock));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                transaccionService.procesarPagoServicio(requestValido)
        );
        assertTrue(ex.getMessage().contains("se encuentra inactivo"));
    }

    @Test
    @DisplayName("Validación saldo: lanza SaldoInsuficienteException si saldo < monto")
    void procesarPagoServicio_SaldoInsuficiente_LanzaSaldoInsuficienteException() {
        cuentaMock.setSaldo(new BigDecimal("20.00")); // Saldo menor que los 50.00 pedidos
        when(transaccionRepository.findByIdempotencyKey("IDEMP-KEY-TEST-001")).thenReturn(Optional.empty());
        when(productoService.obtenerListaProductos()).thenReturn(ProductoListResponse.builder().build());
        when(cuentaRepository.findByNumeroCuentaForUpdate("CTA-1234567890")).thenReturn(Optional.of(cuentaMock));

        SaldoInsuficienteException ex = assertThrows(SaldoInsuficienteException.class, () ->
                transaccionService.procesarPagoServicio(requestValido)
        );
        assertTrue(ex.getMessage().contains("Saldo insuficiente"));
        verifyNoInteractions(gestoPagoTxClient);
    }

    @Test
    @DisplayName("Validación catálogo: lanza BusinessException si el monto no coincide con el precio estipulado")
    void procesarPagoServicio_MontoNoCoincideConCatalogo_LanzaBusinessException() {
        ProductoDTO prod = ProductoDTO.builder()
                .id("PROD-01")
                .nombre("Recarga 100")
                .precio(new BigDecimal("100.00")) // Precio de catalogo es 100, peticion solicita 50
                .activo(true)
                .build();
        when(productoService.obtenerListaProductos()).thenReturn(
                ProductoListResponse.builder().productos(List.of(prod)).build()
        );

        when(transaccionRepository.findByIdempotencyKey("IDEMP-KEY-TEST-001")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                transaccionService.procesarPagoServicio(requestValido)
        );
        assertTrue(ex.getMessage().contains("no coincide con el valor permitido por el catálogo"));
    }

    @Test
    @DisplayName("Rechazo de Gestopago: ejecuta reverso automático restaurando el saldo al cliente")
    void procesarPagoServicio_RechazoProveedor_ReversoAutomatico() {
        when(transaccionRepository.findByIdempotencyKey("IDEMP-KEY-TEST-001")).thenReturn(Optional.empty());
        when(productoService.obtenerListaProductos()).thenReturn(ProductoListResponse.builder().build());
        when(cuentaRepository.findByNumeroCuentaForUpdate("CTA-1234567890")).thenReturn(Optional.of(cuentaMock));
        when(transaccionRepository.saveAndFlush(any(Transaccion.class))).thenAnswer(i -> i.getArgument(0));

        GestoPagoTxResponse rechazoGp = GestoPagoTxResponse.builder()
                .status(1)
                .message("PRODUCTO NO DISPONIBLE")
                .build();
        when(gestoPagoTxClient.sendTx(anyString(), any())).thenReturn(rechazoGp);

        // Act
        TransaccionResponse response = transaccionService.procesarPagoServicio(requestValido);

        // Assert
        assertEquals(EstatusTransaccion.RECHAZADA, response.getEstatus());
        assertEquals("PRODUCTO NO DISPONIBLE", response.getMensaje());
        // El saldo debió regresar a 1000.00
        assertEquals(new BigDecimal("1000.00"), cuentaMock.getSaldo());
        verify(cuentaRepository, times(2)).save(cuentaMock); // 1er debito, 2do reverso
    }

    @Test
    @DisplayName("Timeout de Gestopago: no reversa saldo, marca en PENDIENTE_CONCILIACION")
    void procesarPagoServicio_TimeoutProveedor_TransaccionPendienteConciliacion() {
        when(transaccionRepository.findByIdempotencyKey("IDEMP-KEY-TEST-001")).thenReturn(Optional.empty());
        when(productoService.obtenerListaProductos()).thenReturn(ProductoListResponse.builder().build());
        when(cuentaRepository.findByNumeroCuentaForUpdate("CTA-1234567890")).thenReturn(Optional.of(cuentaMock));
        when(transaccionRepository.saveAndFlush(any(Transaccion.class))).thenAnswer(i -> i.getArgument(0));

        when(gestoPagoTxClient.sendTx(anyString(), any()))
                .thenThrow(new ServiceTimeoutException("Read timed out"));

        // Act
        TransaccionResponse response = transaccionService.procesarPagoServicio(requestValido);

        // Assert
        assertEquals(EstatusTransaccion.PENDIENTE_CONCILIACION, response.getEstatus());
        assertTrue(response.getMotivoFalla().contains("Tiempo de espera agotado"));
        // Regla de oro: El saldo permanece descontado para evitar fraude/doble gasto
        assertEquals(new BigDecimal("950.00"), cuentaMock.getSaldo());
        verify(cuentaRepository, times(1)).save(cuentaMock); // solo el debito inicial, no reverso ciego
    }

    @Test
    @DisplayName("Error comunicación Gestopago: marca en PENDIENTE_CONCILIACION")
    void procesarPagoServicio_ErrorComunicacion_TransaccionPendienteConciliacion() {
        when(transaccionRepository.findByIdempotencyKey("IDEMP-KEY-TEST-001")).thenReturn(Optional.empty());
        when(productoService.obtenerListaProductos()).thenReturn(ProductoListResponse.builder().build());
        when(cuentaRepository.findByNumeroCuentaForUpdate("CTA-1234567890")).thenReturn(Optional.of(cuentaMock));
        when(transaccionRepository.saveAndFlush(any(Transaccion.class))).thenAnswer(i -> i.getArgument(0));

        when(gestoPagoTxClient.sendTx(anyString(), any()))
                .thenThrow(new ServiceCommunicationException("Connection refused"));

        // Act
        TransaccionResponse response = transaccionService.procesarPagoServicio(requestValido);

        // Assert
        assertEquals(EstatusTransaccion.PENDIENTE_CONCILIACION, response.getEstatus());
        assertEquals(new BigDecimal("950.00"), cuentaMock.getSaldo());
    }

    @Test
    @DisplayName("Conciliación: CONFIRMAR actualiza estatus a COMPLETADA")
    void conciliarTransaccion_Confirmar_Exito() {
        Transaccion tx = Transaccion.builder()
                .id(1L)
                .folio("FOLIO-CONCIL-1")
                .estatus(EstatusTransaccion.PENDIENTE_CONCILIACION)
                .monto(new BigDecimal("50.00"))
                .numeroCuenta("CTA-1234567890")
                .build();

        when(transaccionRepository.findByFolio("FOLIO-CONCIL-1")).thenReturn(Optional.of(tx));

        ConciliarTransaccionRequest concilReq = ConciliarTransaccionRequest.builder()
                .accion("CONFIRMAR")
                .folioProveedor("GP-CONFIRMADO-111")
                .autorizacion("AUTH-CONFIRMADA-222")
                .observaciones("Verificado en portal de Gestopago")
                .build();

        TransaccionResponse resp = transaccionService.conciliarTransaccion("FOLIO-CONCIL-1", concilReq);

        assertEquals(EstatusTransaccion.COMPLETADA, resp.getEstatus());
        assertEquals("GP-CONFIRMADO-111", resp.getFolioProveedor());
        assertEquals("AUTH-CONFIRMADA-222", resp.getAutorizacionProveedor());
    }

    @Test
    @DisplayName("Conciliación: REVERSAR restaura el saldo a la cuenta y actualiza a REVERTIDA")
    void conciliarTransaccion_Reversar_Exito() {
        Transaccion tx = Transaccion.builder()
                .id(1L)
                .folio("FOLIO-CONCIL-2")
                .estatus(EstatusTransaccion.PENDIENTE_CONCILIACION)
                .monto(new BigDecimal("50.00"))
                .numeroCuenta("CTA-1234567890")
                .build();

        cuentaMock.setSaldo(new BigDecimal("950.00"));

        when(transaccionRepository.findByFolio("FOLIO-CONCIL-2")).thenReturn(Optional.of(tx));
        when(cuentaRepository.findByNumeroCuentaForUpdate("CTA-1234567890")).thenReturn(Optional.of(cuentaMock));

        ConciliarTransaccionRequest concilReq = ConciliarTransaccionRequest.builder()
                .accion("REVERSAR")
                .observaciones("Gestopago confirma que no existe folio de transacción")
                .build();

        TransaccionResponse resp = transaccionService.conciliarTransaccion("FOLIO-CONCIL-2", concilReq);

        assertEquals(EstatusTransaccion.REVERTIDA, resp.getEstatus());
        assertEquals(new BigDecimal("1000.00"), cuentaMock.getSaldo()); // saldo restaurado
        verify(cuentaRepository, times(1)).save(cuentaMock);
    }

    @Test
    @DisplayName("Conciliación: lanza BusinessException si la transacción ya está COMPLETADA")
    void conciliarTransaccion_NoRequiereConciliacion_LanzaBusinessException() {
        Transaccion tx = Transaccion.builder()
                .id(1L)
                .folio("FOLIO-COMPLETADO")
                .estatus(EstatusTransaccion.COMPLETADA)
                .build();

        when(transaccionRepository.findByFolio("FOLIO-COMPLETADO")).thenReturn(Optional.of(tx));

        ConciliarTransaccionRequest req = ConciliarTransaccionRequest.builder().accion("CONFIRMAR").build();

        assertThrows(BusinessException.class, () ->
                transaccionService.conciliarTransaccion("FOLIO-COMPLETADO", req)
        );
    }
}
