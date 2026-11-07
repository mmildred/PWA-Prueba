package com.proyecto.servicios.model.transaccion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GestoPagoTxResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("status")
    private Integer status;

    @JsonProperty("message")
    private String message;

    @JsonProperty("folio")
    private String folio;

    @JsonProperty("autorizacion")
    private String autorizacion;

    @JsonProperty("fecha")
    private String fecha;
}
