package com.proyecto.servicios.model.transaccion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud para conciliar o resolver transacciones en estatus PENDIENTE_CONCILIACION")
public class ConciliarTransaccionRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "La acción de conciliación es obligatoria (CONFIRMAR, REVERSAR, CONSULTAR)")
    @Pattern(regexp = "^(?i)(CONFIRMAR|REVERSAR|CONSULTAR)$", message = "La acción permitida debe ser CONFIRMAR, REVERSAR o CONSULTAR")
    @Schema(description = "Acción a ejecutar sobre la transacción en duda", example = "CONFIRMAR", allowableValues = {"CONFIRMAR", "REVERSAR", "CONSULTAR"})
    private String accion;

    @Schema(description = "Folio del proveedor si se cuenta con confirmación externa", example = "GP-CONCIL-999")
    private String folioProveedor;

    @Schema(description = "Código de autorización externa asignada por el proveedor", example = "AUTH-CONCIL-111")
    private String autorizacion;

    @Schema(description = "Observaciones o justificación de auditoría para la conciliación", example = "Confirmado exitoso por mesa de control de Gestopago")
    private String observaciones;
}
