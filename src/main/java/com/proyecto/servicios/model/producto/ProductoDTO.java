package com.proyecto.servicios.model.producto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("id")
    private String id;

    @JsonProperty("sku")
    private String sku;

    @JsonProperty("nombre")
    private String nombre;

    @JsonProperty("descripcion")
    private String descripcion;

    @JsonProperty("precio")
    private BigDecimal precio;

    @JsonProperty("categoria")
    private String categoria;

    @JsonProperty("activo")
    private Boolean activo;
}
