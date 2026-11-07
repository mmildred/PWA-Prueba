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
import java.util.stream.Collectors;

@Service
@Slf4j
public class TransaccionServiceImpl implements TransaccionService {

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

        // 1. CONTROL DE IDEMPOTENCIA (Prevención estricta de solicitudes duplicadas)
        Optional<Transaccion> txExistente = transaccionRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (txExistente.isPresent()) {
            Transaccion tx = txExistente.get();
            if (tx.getEstatus() == EstatusTransaccion.COMPLETADA) {
                log.info("Solicitud idempotente: La transacción previa ya fue completada exitosamente con folio {}", tx.getFolio());
                return mapToResponse(tx);
            } else if (tx.getEstatus() == EstatusTransaccion.EN_PROCESO || tx.getEstatus() == EstatusTransaccion.PENDIENTE_CONCILIACION) {
                log.warn("Intento de duplicidad: La transacción con clave {} se encuentra en estado {}",
                        request.getIdempotencyKey(), tx.getEstatus());
                throw new TransaccionDuplicadaException(
                        "La transacción con clave de idempotencia '" + request.getIdempotencyKey() +
                                "' se encuentra en estado '" + tx.getEstatus() + "'. No reenvíe la solicitud.");
            } else {
                log.info("Retornando transacción previa en estado {}", tx.getEstatus());
                return mapToResponse(tx);
            }
        }

        // 2. VALIDACIÓN DEL PRODUCTO Y RESTRICCIONES EN EL CATÁLOGO
        String nombreProducto = validarProductoEnCatalogo(request.getIdProducto(), request.getMonto());

        // 3. BLOQUEO PESIMISTA DE LA CUENTA Y VALIDACIÓN DE IDENTIDAD Y SALDO
        Cuenta cuenta = cuentaRepository.findByNumeroCuentaForUpdate(request.getNumeroCuenta())
                .orElseThrow(() -> new BusinessException("La cuenta bancaria '" + request.getNumeroCuenta() + "' no existe en el sistema."));

        if (!"ACTIVA".equalsIgnoreCase(cuenta.getEstatus())) {
            throw new BusinessException("La cuenta " + request.getNumeroCuenta() + " no se encuentra ACTIVA. Estatus actual: " + cuenta.getEstatus());
        }

        if (cuenta.getCliente() == null || !cuenta.getCliente().getId().equals(request.getClienteId())) {
            throw new BusinessException("El cliente con ID " + request.getClienteId() + " no está autorizado para realizar operaciones sobre la cuenta especificada.");
        }

        if (!Boolean.TRUE.equals(cuenta.getCliente().getActivo())) {
            throw new BusinessException("El cliente titular de la cuenta se encuentra inactivo.");
        }

        if (cuenta.getSaldo().compareTo(request.getMonto()) < 0) {
            throw new SaldoInsuficienteException("Saldo insuficiente en la cuenta. Saldo disponible: $" +
                    cuenta.getSaldo() + ", Monto requerido: $" + request.getMonto());
        }

        // 4. DÉBITO PREVENTIVO DE SALDO E INTEGRIDAD TRANSACCIONAL
        BigDecimal saldoAnterior = cuenta.getSaldo();
        BigDecimal saldoPosterior = saldoAnterior.subtract(request.getMonto());
        cuenta.setSaldo(saldoPosterior);
        cuentaRepository.save(cuenta);

        String folio = UUID.randomUUID().toString();
        Transaccion transaccion = Transaccion.builder()
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

        transaccion = transaccionRepository.saveAndFlush(transaccion);
        log.info("Saldo debitado preventivamente: cuenta={}, saldoAnterior={}, saldoPosterior={}, folio={}",
                enmascararCuenta(cuenta.getNumeroCuenta()), saldoAnterior, saldoPosterior, folio);

        // 5. INVOCACIÓN SEGURA AL PROVEEDOR GESTOPAGO (sendTx.do)
        String bearerToken = obtenerBearerTokenSeguro();

        GestoPagoTxRequest txRequest = GestoPagoTxRequest.builder()
                .idServicio(request.getIdServicio())
                .idProducto(request.getIdProducto())
                .monto(request.getMonto())
                .referencia(request.getReferencia())
                .build();

        try {
            log.info("Enviando petición a Gestopago [POST sendTx.do]: folio={}, servicio={}, producto={}, monto={}",
                    folio, request.getIdServicio(), request.getIdProducto(), request.getMonto());

            GestoPagoTxResponse response = gestoPagoTxClient.sendTx(bearerToken, txRequest);

            if (response != null && (response.getStatus() == null || response.getStatus() == 0 || response.getStatus() == 200)) {
                // A) RESPUESTA EXITOSA DEL PROVEEDOR
                transaccion.setEstatus(EstatusTransaccion.COMPLETADA);
                transaccion.setFolioProveedor(response.getFolio() != null ? response.getFolio() : "GP-" + System.currentTimeMillis());
                transaccion.setAutorizacionProveedor(response.getAutorizacion() != null ? response.getAutorizacion() : "AUTH-" + System.currentTimeMillis());
                transaccion.setMensajeProveedor(response.getMessage() != null ? response.getMessage() : "TRANSACCION EXITOSA");
                transaccion.setCodigoRespuestaProveedor(response.getStatus() != null ? String.valueOf(response.getStatus()) : "0");
                transaccionRepository.save(transaccion);

                log.info("Transacción aprobada por Gestopago: folio={}, autorizacion={}",
                        folio, transaccion.getAutorizacionProveedor());
                return mapToResponse(transaccion);

            } else {
                // B) RECHAZO EXPLÍCITO DEL PROVEEDOR -> REVERSO AUTOMÁTICO INMEDIATO
                String mensajeRechazo = response != null && response.getMessage() != null
                        ? response.getMessage() : "TRANSACCION RECHAZADA POR PROVEEDOR";
                log.warn("Rechazo explícito del proveedor: folio={}, mensaje={}. Ejecutando reverso automático del saldo...",
                        folio, mensajeRechazo);

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

        } catch (ServiceTimeoutException | RetryableException e) {
            // C) TIMEOUT DEL SERVICIO EXTERNO -> REGLA CRÍTICA: NO REVERSAR CIEGAMENTE
            log.error("TIMEOUT al comunicar con Gestopago para folio {}. Transacción marcada como PENDIENTE_CONCILIACION: {}",
                    folio, sanitizeLog(e.getMessage()));

            transaccion.setEstatus(EstatusTransaccion.PENDIENTE_CONCILIACION);
            transaccion.setMotivoFalla("Tiempo de espera agotado al comunicar con Gestopago. Transacción retenida para conciliación.");
            transaccionRepository.save(transaccion);

            return mapToResponse(transaccion);

        } catch (ServiceCommunicationException e) {
            // D) ERROR DE COMUNICACIÓN / CAÍDA DE RED -> RETENER PARA CONCILIACIÓN
            log.error("Fallo de comunicación de red al enviar transacción folio {}. Marcada como PENDIENTE_CONCILIACION: {}",
                    folio, sanitizeLog(e.getMessage()));

            transaccion.setEstatus(EstatusTransaccion.PENDIENTE_CONCILIACION);
            transaccion.setMotivoFalla("Fallo de comunicación de red al conectar con Gestopago. Retenida para conciliación.");
            transaccionRepository.save(transaccion);

            return mapToResponse(transaccion);

        } catch (ServiceResponseException e) {
            if (e.getRawStatusCode() >= 400 && e.getRawStatusCode() < 500) {
                // Error sintáctico o parámetros rechazados en el cliente HTTP -> Seguro reversar
                log.warn("Rechazo HTTP 4xx del proveedor para folio {}. Ejecutando reverso de saldo: {}", folio, sanitizeLog(e.getMessage()));
                cuenta.setSaldo(cuenta.getSaldo().add(transaccion.getMonto()));
                cuentaRepository.save(cuenta);

                transaccion.setEstatus(EstatusTransaccion.RECHAZADA);
                transaccion.setSaldoPosterior(cuenta.getSaldo());
                transaccion.setMotivoFalla("Error en parámetros rechazado por el proveedor: " + e.getMessage());
                transaccionRepository.save(transaccion);

                return mapToResponse(transaccion);
            } else {
                // Error 5xx en el servidor remoto -> Puede haberse procesado, marcar para conciliación
                log.error("Error HTTP 5xx de Gestopago para folio {}. Retenida en PENDIENTE_CONCILIACION: {}", folio, sanitizeLog(e.getMessage()));
                transaccion.setEstatus(EstatusTransaccion.PENDIENTE_CONCILIACION);
                transaccion.setMotivoFalla("Error interno 5xx en Gestopago. Retenida para conciliación.");
                transaccionRepository.save(transaccion);

                return mapToResponse(transaccion);
            }

        } catch (Exception e) {
            log.error("Excepción inesperada al procesar integración externa para folio {}: {}", folio, sanitizeLog(e.getMessage()));
            transaccion.setEstatus(EstatusTransaccion.PENDIENTE_CONCILIACION);
            transaccion.setMotivoFalla("Excepción inesperada durante la integración externa. Retenida para conciliación.");
            transaccionRepository.save(transaccion);

            return mapToResponse(transaccion);
        }
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
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransaccionResponse> consultarPorCliente(Long clienteId) {
        return transaccionRepository.findByClienteIdOrderByFechaCreacionDesc(clienteId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
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
                tx.setEstatus(EstatusTransaccion.COMPLETADA);
                tx.setFolioProveedor(request.getFolioProveedor() != null ? request.getFolioProveedor() : "GP-CONCIL-" + System.currentTimeMillis());
                tx.setAutorizacionProveedor(request.getAutorizacion() != null ? request.getAutorizacion() : "AUTH-CONCIL-" + System.currentTimeMillis());
                tx.setMensajeProveedor("CONCILIADA EXITOSA: " + (request.getObservaciones() != null ? request.getObservaciones() : "Confirmada por mesa de control"));
                transaccionRepository.save(tx);
                log.info("Transacción {} conciliada como COMPLETADA", folio);
                break;

            case "REVERSAR":
                // Se confirmó con el proveedor que la transacción nunca se aplicó -> Desembolso al cliente
                Cuenta cuenta = cuentaRepository.findByNumeroCuentaForUpdate(tx.getNumeroCuenta())
                        .orElseThrow(() -> new BusinessException("Cuenta " + tx.getNumeroCuenta() + " no encontrada para reverso"));
                cuenta.setSaldo(cuenta.getSaldo().add(tx.getMonto()));
                cuentaRepository.save(cuenta);

                tx.setEstatus(EstatusTransaccion.REVERTIDA);
                tx.setSaldoPosterior(cuenta.getSaldo());
                tx.setMotivoFalla("Revertida tras conciliación: " + (request.getObservaciones() != null ? request.getObservaciones() : "Sin procesamiento externo"));
                transaccionRepository.save(tx);
                log.info("Transacción {} conciliada como REVERTIDA. Saldo restaurado a la cuenta {}", folio, tx.getNumeroCuenta());
                break;

            case "CONSULTAR":
                log.info("Consulta de conciliación sobre transacción {}", folio);
                break;

            default:
                throw new BusinessException("Acción de conciliación no reconocida: " + request.getAccion());
        }

        return mapToResponse(tx);
    }

    /**
     * Valida la existencia y precio del producto en el catálogo oficial de Gestopago
     */
    private String validarProductoEnCatalogo(String idProducto, BigDecimal monto) {
        try {
            ProductoListResponse catalogo = productoService.obtenerListaProductos();
            if (catalogo != null && catalogo.getProductos() != null && !catalogo.getProductos().isEmpty()) {
                Optional<ProductoDTO> match = catalogo.getProductos().stream()
                        .filter(p -> idProducto.equalsIgnoreCase(p.getId()) || idProducto.equalsIgnoreCase(p.getSku()))
                        .findFirst();

                if (match.isEmpty()) {
                    throw new BusinessException("El producto con ID o SKU '" + idProducto + "' no existe en el catálogo disponible.");
                }

                ProductoDTO prod = match.get();
                if (Boolean.FALSE.equals(prod.getActivo())) {
                    throw new BusinessException("El producto '" + prod.getNombre() + "' (" + idProducto + ") se encuentra inactivo en el catálogo.");
                }

                if (prod.getPrecio() != null && prod.getPrecio().compareTo(BigDecimal.ZERO) > 0) {
                    if (prod.getPrecio().compareTo(monto) != 0) {
                        throw new BusinessException("El monto solicitado ($" + monto +
                                ") no coincide con el valor permitido por el catálogo para este producto ($" + prod.getPrecio() + ").");
                    }
                }
                return prod.getNombre() != null ? prod.getNombre() : "Producto " + idProducto;
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.warn("No fue posible validar catálogo en vivo (modo contingencia): {}", sanitizeLog(e.getMessage()));
        }

        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0 || monto.compareTo(new BigDecimal("50000.00")) > 0) {
            throw new BusinessException("Monto fuera de los límites permitidos ($0.01 - $50,000.00)");
        }
        return "Producto " + idProducto;
    }

    /**
     * Recupera el token Bearer desde configuración segura o almacén activo en BD sin exponerlo
     */
    private String obtenerBearerTokenSeguro() {
        if (configuredToken != null && !configuredToken.trim().isEmpty()) {
            String token = configuredToken.trim();
            return token.regionMatches(true, 0, "Bearer ", 0, 7) ? token : "Bearer " + token;
        }

        return gestoPagoTokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo)
                .map(t -> "Bearer " + t.getToken())
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
        return msg.replaceAll("(?i)(bearer\\s+[a-zA-Z0-9._-]+)", "Bearer ***")
                .replaceAll("(?i)(password|token|secret)=[^&\\s]+", "$1=***");
    }
}
