package com.proyecto.servicios.model.transaccion;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class GestoPagoTxRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("idServicio")
    private String idServicio;

    @JsonProperty("idProducto")
    private String idProducto;

    @JsonProperty("monto")
    private BigDecimal monto;

    @JsonProperty("referencia")
    private String referencia;
}
