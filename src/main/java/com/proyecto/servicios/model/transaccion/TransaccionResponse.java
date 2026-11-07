package com.proyecto.servicios.model.transaccion;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.proyecto.servicios.entity.transaccion.EstatusTransaccion;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Respuesta detallada de la transacción financiera")
public class TransaccionResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Folio único de la transacción en el sistema", example = "550e8400-e29b-41d4-a716-446655440000")
    private String folio;

    @Schema(description = "Clave de idempotencia asociada", example = "TX-REQ-20261030-001")
    private String idempotencyKey;

    @Schema(description = "Número de cuenta origen (enmascarado)", example = "CTA-******74")
    private String numeroCuenta;

    @Schema(description = "ID del cliente titular", example = "1")
    private Long clienteId;

    @Schema(description = "ID del servicio", example = "TELEFONIA")
    private String idServicio;

    @Schema(description = "ID del producto", example = "PROD-01")
    private String idProducto;

    @Schema(description = "Nombre o descripción del producto", example = "Recarga Telefónica")
    private String nombreProducto;

    @Schema(description = "Monto de la transacción", example = "50.00")
    private BigDecimal monto;

    @Schema(description = "Referencia del servicio (enmascarada)", example = "******5678")
    private String referencia;

    @Schema(description = "Estatus de la transacción", example = "COMPLETADA")
    private EstatusTransaccion estatus;

    @Schema(description = "Saldo disponible previo a la operación", example = "1000.00")
    private BigDecimal saldoAnterior;

    @Schema(description = "Saldo disponible posterior a la operación", example = "950.00")
    private BigDecimal saldoPosterior;

    @Schema(description = "Folio asignado por el proveedor externo", example = "GP-8493021")
    private String folioProveedor;

    @Schema(description = "Código de autorización del proveedor externo", example = "AUTH-738291")
    private String autorizacionProveedor;

    @Schema(description = "Mensaje informativo del proveedor o del sistema", example = "TRANSACCION EXITOSA")
    private String mensaje;

    @Schema(description = "Detalle del motivo en caso de rechazo o timeout para conciliación")
    private String motivoFalla;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "Fecha y hora de registro de la transacción", example = "2026-10-30 16:30:00")
    private LocalDateTime fechaHora;
}
