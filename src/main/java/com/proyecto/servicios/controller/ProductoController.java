package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.ErrorResponse;
import com.proyecto.servicios.model.producto.ProductoListResponse;
import com.proyecto.servicios.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/productos")
@Tag(name = "Productos", description = "Operaciones de consulta sobre el catálogo de productos externos")
@Slf4j
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtener lista de productos", description = "Consulta el listado de productos consumiendo el servicio externo con Bearer Token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Productos obtenidos exitosamente",
                    content = @Content(schema = @Schema(implementation = ProductoListResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado / Error de token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Respuesta no exitosa del servicio externo",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Servicio externo no disponible / Error de comunicación",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "504", description = "Tiempo de espera agotado al consultar el servicio",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProductoListResponse> obtenerProductos() {
        log.info("Endpoint [GET /productos] invocado");
        ProductoListResponse response = productoService.obtenerListaProductos();
        return ResponseEntity.ok(response);
    }
}
