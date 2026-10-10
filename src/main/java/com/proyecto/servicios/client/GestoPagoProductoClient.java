package com.proyecto.servicios.client;

import com.proyecto.servicios.config.FeignClientConfig;
import com.proyecto.servicios.model.producto.ProductoListResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "gestoPagoProductoClient",
        url = "${gestopago.productos.url}",
        configuration = FeignClientConfig.class
)
public interface GestoPagoProductoClient {

    @GetMapping(
            value = "/sistema/service/getProductList.do",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    ProductoListResponse getProductList(
            @RequestHeader("Authorization") String authorization
    );
}
