package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoTxClient;
import com.proyecto.servicios.entity.onboarding.Cuenta;
import com.proyecto.servicios.entity.transaccion.EstatusTransaccion;
import com.proyecto.servicios.entity.transaccion.Transaccion;
import com.proyecto.servicios.exception.*;
import com.proyecto.servicios.model.producto.ProductoDTO;
import com.proyecto.servicios.model.producto.ProductoListResponse;
import com.proyecto.servicios.model.transaccion.ConciliarTransaccionRequest;
import com.proyecto.servicios.model.transaccion.GestoPagoTxRequest;
import com.proyecto.servicios.model.transaccion.GestoPagoTxResponse;
import com.proyecto.servicios.model.transaccion.PagarServicioRequest;
import com.proyecto.servicios.model.transaccion.TransaccionResponse;
import com.proyecto.servicios.repositorys.onboarding.CuentaRepository;
import com.proyecto.servicios.repositorys.transaccion.TransaccionRepository;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.service.ProductoService;
import com.proyecto.servicios.service.TransaccionService;
import feign.RetryableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class TransaccionServiceImpl implements TransaccionService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TransaccionRepository transaccionRepository;
    private final CuentaRepository cuentaRepository;
    private final ProductoService productoService;
    private final GestoPagoTxClient gestoPagoTxClient;
    private final GestoPagoTokenService gestoPagoTokenService;

    @Value("${gestopago.tx.token:${gestopago.productos.token:}}")
    private String configuredToken;

    @Value("${gestopago.auth.id-distribuidor:83}")
    private Integer idDistribuidor;

    @Value("${gestopago.auth.codigo-dispositivo:GPS83-TPV-17}")
    private String codigoDispositivo;

    public TransaccionServiceImpl(
            TransaccionRepository transaccionRepository,
            CuentaRepository cuentaRepository,
            ProductoService productoService,
            GestoPagoTxClient gestoPagoTxClient,
            GestoPagoTokenService gestoPagoTokenService
    ) {
        this.transaccionRepository = transaccionRepository;
        this.cuentaRepository = cuentaRepository;
        this.productoService = productoService;
        this.gestoPagoTxClient = gestoPagoTxClient;
        this.gestoPagoTokenService = gestoPagoTokenService;
    }

    @Override
    @Transactional
    public TransaccionResponse procesarPagoServicio(PagarServicioRequest request) {
        log.info("Iniciando procesamiento de pago de servicio: cuenta={}, clienteId={}, servicio={}, producto={}, monto={}",
                enmascararCuenta(request.getNumeroCuenta()), request.getClienteId(), request.getIdServicio(),
                request.getIdProducto(), request.getMonto());

        // 1. Control de idempotencia
        Optional<TransaccionResponse> respuestaIdempotente = verificarIdempotencia(request.getIdempotencyKey());
        if (respuestaIdempotente.isPresent()) {
            return respuestaIdempotente.get();
        }

        // 2. Validación de catálogo
        String nombreProducto = validarProductoEnCatalogo(request.getIdProducto(), request.getMonto());

        // 3. Bloqueo pesimista y validación
        Cuenta cuenta = validarCuentaYCliente(request.getNumeroCuenta(), request.getClienteId(), request.getMonto());

        // 4. Débito preventivo
        BigDecimal saldoAnterior = cuenta.getSaldo();
        BigDecimal saldoPosterior = saldoAnterior.subtract(request.getMonto());
        cuenta.setSaldo(saldoPosterior);
        cuentaRepository.save(cuenta);

        String folio = UUID.randomUUID().toString();
        Transaccion transaccion = construirTransaccion(request, cuenta, nombreProducto, folio, saldoAnterior, saldoPosterior);
        transaccion = transaccionRepository.saveAndFlush(transaccion);

        log.info("Saldo debitado preventivamente: cuenta={}, saldoAnterior={}, saldoPosterior={}, folio={}",
                enmascararCuenta(cuenta.getNumeroCuenta()), saldoAnterior, saldoPosterior, folio);

        // 5. Invocación a Gestopago
        return ejecutarLlamadaProveedor(transaccion, cuenta, request);
    }

    private Optional<TransaccionResponse> verificarIdempotencia(String idempotencyKey) {
        Optional<Transaccion> txExistente = transaccionRepository.findByIdempotencyKey(idempotencyKey);
        if (txExistente.isEmpty()) {
            return Optional.empty();
        }

        Transaccion tx = txExistente.get();
        if (tx.getEstatus() == EstatusTransaccion.COMPLETADA) {
            log.info("Solicitud idempotente: La transacción previa ya fue completada exitosamente con folio {}", tx.getFolio());
            return Optional.of(mapToResponse(tx));
        }

        if (tx.getEstatus() == EstatusTransaccion.EN_PROCESO || tx.getEstatus() == EstatusTransaccion.PENDIENTE_CONCILIACION) {
            log.warn("Intento de duplicidad: La transacción con clave {} se encuentra en estado {}",
                    idempotencyKey, tx.getEstatus());
            throw new TransaccionDuplicadaException(
                    "La transacción con clave de idempotencia '" + idempotencyKey +
                            "' se encuentra en estado '" + tx.getEstatus() + "'. No reenvíe la solicitud.");
        }

        log.info("Retornando transacción previa en estado {}", tx.getEstatus());
        return Optional.of(mapToResponse(tx));
    }

    private Cuenta validarCuentaYCliente(String numeroCuenta, Long clienteId, BigDecimal monto) {
        Cuenta cuenta = cuentaRepository.findByNumeroCuentaForUpdate(numeroCuenta)
                .orElseThrow(() -> new BusinessException("La cuenta bancaria '" + numeroCuenta + "' no existe en el sistema."));

        if (!"ACTIVA".equalsIgnoreCase(cuenta.getEstatus())) {
            throw new BusinessException("La cuenta " + numeroCuenta + " no se encuentra ACTIVA. Estatus actual: " + cuenta.getEstatus());
        }

        if (cuenta.getCliente() == null || !cuenta.getCliente().getId().equals(clienteId)) {
            throw new BusinessException("El cliente con ID " + clienteId + " no está autorizado para realizar operaciones sobre la cuenta especificada.");
        }

        if (!Boolean.TRUE.equals(cuenta.getCliente().getActivo())) {
            throw new BusinessException("El cliente titular de la cuenta se encuentra inactivo.");
        }

        if (cuenta.getSaldo().compareTo(monto) < 0) {
            throw new SaldoInsuficienteException("Saldo insuficiente en la cuenta. Saldo disponible: $" +
                    cuenta.getSaldo() + ", Monto requerido: $" + monto);
        }

        return cuenta;
    }

    private Transaccion construirTransaccion(PagarServicioRequest request, Cuenta cuenta, String nombreProducto,
                                             String folio, BigDecimal saldoAnterior, BigDecimal saldoPosterior) {
        return Transaccion.builder()
                .folio(folio)
                .idempotencyKey(request.getIdempotencyKey())
                .cuenta(cuenta)
                .numeroCuenta(cuenta.getNumeroCuenta())
                .cliente(cuenta.getCliente())
                .idServicio(request.getIdServicio())
                .idProducto(request.getIdProducto())
                .nombreProducto(nombreProducto)
                .monto(request.getMonto())
                .referencia(request.getReferencia())
                .estatus(EstatusTransaccion.EN_PROCESO)
                .saldoAnterior(saldoAnterior)
                .saldoPosterior(saldoPosterior)
                .build();
    }

    private TransaccionResponse ejecutarLlamadaProveedor(Transaccion transaccion, Cuenta cuenta, PagarServicioRequest request) {
        String bearerToken = obtenerBearerTokenSeguro();
        GestoPagoTxRequest txRequest = GestoPagoTxRequest.builder()
                .idServicio(request.getIdServicio())
                .idProducto(request.getIdProducto())
                .monto(request.getMonto())
                .referencia(request.getReferencia())
                .build();

        try {
            log.info("Enviando petición a Gestopago [POST sendTx.do]: folio={}, servicio={}, producto={}, monto={}",
                    transaccion.getFolio(), request.getIdServicio(), request.getIdProducto(), request.getMonto());

            GestoPagoTxResponse response = gestoPagoTxClient.sendTx(bearerToken, txRequest);
            return procesarRespuestaGestopago(transaccion, cuenta, response);

        } catch (Exception e) {
            return manejarExcepcionIntegracion(transaccion, cuenta, e);
        }
    }

    private TransaccionResponse procesarRespuestaGestopago(Transaccion transaccion, Cuenta cuenta, GestoPagoTxResponse response) {
        boolean esExitosa = response != null && (response.getStatus() == null || response.getStatus() == 0 || response.getStatus() == 200);

        if (esExitosa) {
            transaccion.setEstatus(EstatusTransaccion.COMPLETADA);
            transaccion.setFolioProveedor(response.getFolio() != null ? response.getFolio() : "GP-" + System.currentTimeMillis());
            transaccion.setAutorizacionProveedor(response.getAutorizacion() != null ? response.getAutorizacion() : "AUTH-" + System.currentTimeMillis());
            transaccion.setMensajeProveedor(response.getMessage() != null ? response.getMessage() : "TRANSACCION EXITOSA");
            transaccion.setCodigoRespuestaProveedor(response.getStatus() != null ? String.valueOf(response.getStatus()) : "0");
            transaccionRepository.save(transaccion);

            log.info("Transacción aprobada por Gestopago: folio={}, autorizacion={}",
                    transaccion.getFolio(), transaccion.getAutorizacionProveedor());
            return mapToResponse(transaccion);
        }

        String mensajeRechazo = response != null && response.getMessage() != null
                ? response.getMessage() : "TRANSACCION RECHAZADA POR PROVEEDOR";
        log.warn("Rechazo explícito del proveedor: folio={}, mensaje={}. Ejecutando reverso automático del saldo...",
                transaccion.getFolio(), mensajeRechazo);

        cuenta.setSaldo(cuenta.getSaldo().add(transaccion.getMonto()));
        cuentaRepository.save(cuenta);

        transaccion.setEstatus(EstatusTransaccion.RECHAZADA);
        transaccion.setSaldoPosterior(cuenta.getSaldo());
        transaccion.setMensajeProveedor(mensajeRechazo);
        transaccion.setCodigoRespuestaProveedor(response != null && response.getStatus() != null ? String.valueOf(response.getStatus()) : "1");
        transaccion.setMotivoFalla("Rechazado por proveedor externo: " + mensajeRechazo);
        transaccionRepository.save(transaccion);

        return mapToResponse(transaccion);
    }

    private TransaccionResponse manejarExcepcionIntegracion(Transaccion transaccion, Cuenta cuenta, Exception e) {
        String folio = transaccion.getFolio();

        if (e instanceof ServiceTimeoutException || e instanceof RetryableException) {
            log.error("TIMEOUT al comunicar con Gestopago para folio {}. Transacción marcada como PENDIENTE_CONCILIACION: {}",
                    folio, sanitizeLog(e.getMessage()));
            transaccion.setEstatus(EstatusTransaccion.PENDIENTE_CONCILIACION);
            transaccion.setMotivoFalla("Tiempo de espera agotado al comunicar con Gestopago. Transacción retenida para conciliación.");
            transaccionRepository.save(transaccion);
            return mapToResponse(transaccion);
        }

        if (e instanceof ServiceCommunicationException) {
            log.error("Fallo de comunicación de red al enviar transacción folio {}. Marcada como PENDIENTE_CONCILIACION: {}",
                    folio, sanitizeLog(e.getMessage()));
            transaccion.setEstatus(EstatusTransaccion.PENDIENTE_CONCILIACION);
            transaccion.setMotivoFalla("Fallo de comunicación de red al conectar con Gestopago. Retenida para conciliación.");
            transaccionRepository.save(transaccion);
            return mapToResponse(transaccion);
        }

        if (e instanceof ServiceResponseException sre) {
            if (sre.getRawStatusCode() >= 400 && sre.getRawStatusCode() < 500) {
                log.warn("Rechazo HTTP 4xx del proveedor para folio {}. Ejecutando reverso de saldo: {}", folio, sanitizeLog(e.getMessage()));
                cuenta.setSaldo(cuenta.getSaldo().add(transaccion.getMonto()));
                cuentaRepository.save(cuenta);

                transaccion.setEstatus(EstatusTransaccion.RECHAZADA);
                transaccion.setSaldoPosterior(cuenta.getSaldo());
                transaccion.setMotivoFalla("Error en parámetros rechazado por el proveedor: " + e.getMessage());
                transaccionRepository.save(transaccion);
                return mapToResponse(transaccion);
            }

            log.error("Error HTTP 5xx de Gestopago para folio {}. Retenida en PENDIENTE_CONCILIACION: {}", folio, sanitizeLog(e.getMessage()));
            transaccion.setEstatus(EstatusTransaccion.PENDIENTE_CONCILIACION);
            transaccion.setMotivoFalla("Error interno 5xx en Gestopago. Retenida para conciliación.");
            transaccionRepository.save(transaccion);
            return mapToResponse(transaccion);
        }

        log.error("Excepción inesperada al procesar integración externa para folio {}: {}", folio, sanitizeLog(e.getMessage()));
        transaccion.setEstatus(EstatusTransaccion.PENDIENTE_CONCILIACION);
        transaccion.setMotivoFalla("Excepción inesperada durante la integración externa. Retenida para conciliación.");
        transaccionRepository.save(transaccion);
        return mapToResponse(transaccion);
    }

    @Override
    @Transactional(readOnly = true)
    public TransaccionResponse consultarPorFolio(String folio) {
        Transaccion transaccion = transaccionRepository.findByFolio(folio)
                .orElseThrow(() -> new TransaccionNoEncontradaException("Transacción con folio '" + folio + "' no encontrada."));
        return mapToResponse(transaccion);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransaccionResponse> consultarPorCuenta(String numeroCuenta) {
        return transaccionRepository.findByNumeroCuentaOrderByFechaCreacionDesc(numeroCuenta)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransaccionResponse> consultarPorCliente(Long clienteId) {
        return transaccionRepository.findByClienteIdOrderByFechaCreacionDesc(clienteId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public TransaccionResponse conciliarTransaccion(String folio, ConciliarTransaccionRequest request) {
        log.info("Iniciando conciliación para folio: {}, accion: {}, observaciones: {}",
                folio, request.getAccion(), request.getObservaciones());

        Transaccion tx = transaccionRepository.findByFolio(folio)
                .orElseThrow(() -> new TransaccionNoEncontradaException("Transacción con folio '" + folio + "' no encontrada."));

        if (tx.getEstatus() != EstatusTransaccion.PENDIENTE_CONCILIACION && tx.getEstatus() != EstatusTransaccion.EN_PROCESO) {
            throw new BusinessException("La transacción " + folio + " está en estatus '" + tx.getEstatus() +
                    "' y no requiere conciliación.");
        }

        String accion = request.getAccion().toUpperCase();
        switch (accion) {
            case "CONFIRMAR":
                ejecutarConfirmacionConciliacion(tx, request, folio);
                break;
            case "REVERSAR":
                ejecutarReversoConciliacion(tx, request, folio);
                break;
            case "CONSULTAR":
                log.info("Consulta de conciliación sobre transacción {}", folio);
                break;
            default:
                throw new BusinessException("Acción de conciliación no reconocida: " + request.getAccion());
        }

        return mapToResponse(tx);
    }

    private void ejecutarConfirmacionConciliacion(Transaccion tx, ConciliarTransaccionRequest request, String folio) {
        tx.setEstatus(EstatusTransaccion.COMPLETADA);
        tx.setFolioProveedor(request.getFolioProveedor() != null ? request.getFolioProveedor() : "GP-CONCIL-" + System.currentTimeMillis());
        tx.setAutorizacionProveedor(request.getAutorizacion() != null ? request.getAutorizacion() : "AUTH-CONCIL-" + System.currentTimeMillis());
        tx.setMensajeProveedor("CONCILIADA EXITOSA: " + (request.getObservaciones() != null ? request.getObservaciones() : "Confirmada por mesa de control"));
        transaccionRepository.save(tx);
        log.info("Transacción {} conciliada como COMPLETADA", folio);
    }

    private void ejecutarReversoConciliacion(Transaccion tx, ConciliarTransaccionRequest request, String folio) {
        Cuenta cuenta = cuentaRepository.findByNumeroCuentaForUpdate(tx.getNumeroCuenta())
                .orElseThrow(() -> new BusinessException("Cuenta " + tx.getNumeroCuenta() + " no encontrada para reverso"));
        cuenta.setSaldo(cuenta.getSaldo().add(tx.getMonto()));
        cuentaRepository.save(cuenta);

        tx.setEstatus(EstatusTransaccion.REVERTIDA);
        tx.setSaldoPosterior(cuenta.getSaldo());
        tx.setMotivoFalla("Revertida tras conciliación: " + (request.getObservaciones() != null ? request.getObservaciones() : "Sin procesamiento externo"));
        transaccionRepository.save(tx);
        log.info("Transacción {} conciliada como REVERTIDA. Saldo restaurado a la cuenta {}", folio, tx.getNumeroCuenta());
    }

    private String validarProductoEnCatalogo(String idProducto, BigDecimal monto) {
        try {
            ProductoListResponse catalogo = productoService.obtenerListaProductos();
            if (catalogo != null && catalogo.getProductos() != null && !catalogo.getProductos().isEmpty()) {
                return buscarYValidarProducto(catalogo.getProductos(), idProducto, monto);
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.warn("No fue posible validar catálogo en vivo (modo contingencia): {}", sanitizeLog(e.getMessage()));
        }

        validarRangoMonto(monto);
        return "Producto " + idProducto;
    }

    private String buscarYValidarProducto(List<ProductoDTO> productos, String idProducto, BigDecimal monto) {
        ProductoDTO prod = productos.stream()
                .filter(p -> idProducto.equalsIgnoreCase(p.getId()) || idProducto.equalsIgnoreCase(p.getSku()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("El producto con ID o SKU '" + idProducto + "' no existe en el catálogo disponible."));

        if (Boolean.FALSE.equals(prod.getActivo())) {
            throw new BusinessException("El producto '" + prod.getNombre() + "' (" + idProducto + ") se encuentra inactivo en el catálogo.");
        }

        if (prod.getPrecio() != null && prod.getPrecio().compareTo(BigDecimal.ZERO) > 0 && prod.getPrecio().compareTo(monto) != 0) {
            throw new BusinessException("El monto solicitado ($" + monto +
                    ") no coincide con el valor permitido por el catálogo para este producto ($" + prod.getPrecio() + ").");
        }

        return prod.getNombre() != null ? prod.getNombre() : "Producto " + idProducto;
    }

    private void validarRangoMonto(BigDecimal monto) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0 || monto.compareTo(new BigDecimal("50000.00")) > 0) {
            throw new BusinessException("Monto fuera de los límites permitidos ($0.01 - $50,000.00)");
        }
    }

    private String obtenerBearerTokenSeguro() {
        if (configuredToken != null && !configuredToken.trim().isEmpty()) {
            String token = configuredToken.trim();
            return token.regionMatches(true, 0, BEARER_PREFIX, 0, 7) ? token : BEARER_PREFIX + token;
        }

        return gestoPagoTokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo)
                .map(t -> BEARER_PREFIX + t.getToken())
                .orElseThrow(() -> new ServiceAuthenticationException("No se encontró Bearer Token configurado ni token activo en base de datos"));
    }

    private TransaccionResponse mapToResponse(Transaccion tx) {
        return TransaccionResponse.builder()
                .folio(tx.getFolio())
                .idempotencyKey(tx.getIdempotencyKey())
                .numeroCuenta(enmascararCuenta(tx.getNumeroCuenta()))
                .clienteId(tx.getCliente() != null ? tx.getCliente().getId() : null)
                .idServicio(tx.getIdServicio())
                .idProducto(tx.getIdProducto())
                .nombreProducto(tx.getNombreProducto())
                .monto(tx.getMonto())
                .referencia(enmascararReferencia(tx.getReferencia()))
                .estatus(tx.getEstatus())
                .saldoAnterior(tx.getSaldoAnterior())
                .saldoPosterior(tx.getSaldoPosterior())
                .folioProveedor(tx.getFolioProveedor())
                .autorizacionProveedor(tx.getAutorizacionProveedor())
                .mensaje(tx.getMensajeProveedor())
                .motivoFalla(tx.getMotivoFalla())
                .fechaHora(tx.getFechaCreacion())
                .build();
    }

    private String enmascararCuenta(String numeroCuenta) {
        if (numeroCuenta == null || numeroCuenta.length() < 4) return "****";
        return numeroCuenta.substring(0, Math.min(4, numeroCuenta.length() - 4)) +
                "******" +
                numeroCuenta.substring(numeroCuenta.length() - 4);
    }

    private String enmascararReferencia(String ref) {
        if (ref == null || ref.length() < 4) return "****";
        return "******" + ref.substring(ref.length() - 4);
    }

    private String sanitizeLog(String msg) {
        if (msg == null) return "";
        return msg.replaceAll("(?i)(bearer\\s+[\\w.-]+)", BEARER_PREFIX + "***")
                .replaceAll("(?i)(password|token|secret)=[^&\\s]+", "$1=***");
    }
}