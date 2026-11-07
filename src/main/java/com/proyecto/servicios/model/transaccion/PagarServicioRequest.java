package com.proyecto.servicios.model.transaccion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud para pago de servicio o recarga mediante débito a cuenta bancaria")
public class PagarServicioRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "El número de cuenta es obligatorio")
    @Pattern(regexp = "^(CTA-)?[0-9]{10,20}$", message = "El formato del número de cuenta no es válido (ej. CTA-1029384756 o dígitos numéricos)")
    @Schema(description = "Número de cuenta bancaria del cliente origen", example = "CTA-7281928374")
    private String numeroCuenta;

    @NotNull(message = "El ID del cliente es obligatorio")
    @Schema(description = "Identificador único del cliente titular", example = "1")
    private Long clienteId;

    @NotBlank(message = "El ID del servicio es obligatorio")
    @Schema(description = "Identificador del servicio externo en catálogo (ej. TELEFONIA, CFE, AGUA)", example = "TELEFONIA")
    private String idServicio;

    @NotBlank(message = "El ID del producto es obligatorio")
    @Schema(description = "Identificador o SKU del producto en catálogo", example = "PROD-01")
    private String idProducto;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto a pagar debe ser mayor a cero")
    @Schema(description = "Monto de la operación", example = "50.00")
    private BigDecimal monto;

    @NotBlank(message = "La referencia de pago es obligatoria")
    @Size(min = 4, max = 50, message = "La referencia debe tener entre 4 y 50 caracteres")
    @Schema(description = "Referencia del servicio (número telefónico, referencia de recibo)", example = "5512345678")
    private String referencia;

    @NotBlank(message = "La clave de idempotencia es obligatoria")
    @Size(min = 8, max = 100, message = "La clave de idempotencia debe tener entre 8 y 100 caracteres")
    @Schema(description = "Identificador único de la petición para prevenir cargos duplicados", example = "TX-REQ-20261030-001")
    private String idempotencyKey;
}
