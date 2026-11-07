package com.proyecto.servicios.client;

import com.proyecto.servicios.config.FeignClientConfig;
import com.proyecto.servicios.model.transaccion.GestoPagoTxRequest;
import com.proyecto.servicios.model.transaccion.GestoPagoTxResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "gestoPagoTxClient",
        url = "${gestopago.tx.url:${gestopago.auth.url}}",
        configuration = FeignClientConfig.class
)
public interface GestoPagoTxClient {

    @PostMapping(
            value = "/sistema/service/sendTx.do",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    GestoPagoTxResponse sendTx(
            @RequestHeader("Authorization") String authorization,
            @RequestBody GestoPagoTxRequest request
    );
}
