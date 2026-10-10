package com.proyecto.servicios.model.producto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductoListResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("status")
    private Integer status;

    @JsonProperty("message")
    private String message;

    @JsonProperty("productos")
    @Builder.Default
    private List<ProductoDTO> productos = new ArrayList<>();
}
