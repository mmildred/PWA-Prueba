package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.ErrorResponse;
import com.proyecto.servicios.model.transaccion.ConciliarTransaccionRequest;
import com.proyecto.servicios.model.transaccion.PagarServicioRequest;
import com.proyecto.servicios.model.transaccion.TransaccionResponse;
import com.proyecto.servicios.service.TransaccionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/transacciones")
@Tag(name = "Transacciones Financieras", description = "Operaciones de pago de servicios, recargas y conciliación con pasarela Gestopago")
@Slf4j
public class TransaccionController {

    private final TransaccionService transaccionService;

    public TransaccionController(TransaccionService transaccionService) {
        this.transaccionService = transaccionService;
    }

    @PostMapping(
            value = "/pagar",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Procesar pago de servicio o recarga",
            description = "Ejecuta el débito preventivo en la cuenta del cliente y consume Gestopago sendTx.do con Bearer Token, garantizando idempotencia, validación de catálogo y consistencia transaccional."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transacción procesada (puede ser COMPLETADA, RECHAZADA o PENDIENTE_CONCILIACION)",
                    content = @Content(schema = @Schema(implementation = TransaccionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Parámetros inválidos o producto/monto fuera de restricciones",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Error de autenticación con el proveedor externo",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cuenta o cliente no encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Transacción duplicada / Clave de idempotencia en proceso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Saldo insuficiente o cuenta/cliente inactivo",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TransaccionResponse> pagarServicio(@Valid @RequestBody PagarServicioRequest request) {
        log.info("REST: Solicitud de pago de servicio recibida: cuenta={}, producto={}, monto={}",
                request.getNumeroCuenta(), request.getIdProducto(), request.getMonto());
        TransaccionResponse response = transaccionService.procesarPagoServicio(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/{folio}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consultar transacción por folio", description = "Recupera los datos y estatus detallado de una transacción mediante su folio único")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transacción localizada",
                    content = @Content(schema = @Schema(implementation = TransaccionResponse.class))),
            @ApiResponse(responseCode = "404", description = "Transacción no encontrada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TransaccionResponse> consultarPorFolio(
            @Parameter(description = "Folio único de la transacción", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable String folio) {
        return ResponseEntity.ok(transaccionService.consultarPorFolio(folio));
    }

    @GetMapping(value = "/cuenta/{numeroCuenta}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consultar transacciones por cuenta", description = "Obtiene el historial de transacciones realizadas con cargo a una cuenta bancaria")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Historial obtenido",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TransaccionResponse.class))))
    })
    public ResponseEntity<List<TransaccionResponse>> consultarPorCuenta(
            @Parameter(description = "Número de cuenta bancaria", example = "CTA-7281928374")
            @PathVariable String numeroCuenta) {
        return ResponseEntity.ok(transaccionService.consultarPorCuenta(numeroCuenta));
    }

    @GetMapping(value = "/cliente/{clienteId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consultar transacciones por cliente", description = "Obtiene el historial de transacciones pertenecientes a un cliente")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Historial de cliente obtenido",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TransaccionResponse.class))))
    })
    public ResponseEntity<List<TransaccionResponse>> consultarPorCliente(
            @Parameter(description = "ID del cliente", example = "1")
            @PathVariable Long clienteId) {
        return ResponseEntity.ok(transaccionService.consultarPorCliente(clienteId));
    }

    @PostMapping(
            value = "/{folio}/conciliar",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Conciliar transacción pendiente",
            description = "Resuelve una transacción en estatus PENDIENTE_CONCILIACION. Permite CONFIRMAR la operación si el proveedor la ejecutó, o REVERSAR el saldo si el proveedor certificó que no se procesó."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transacción conciliada exitosamente",
                    content = @Content(schema = @Schema(implementation = TransaccionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Acción inválida o la transacción no requiere conciliación",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Transacción no encontrada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TransaccionResponse> conciliar(
            @Parameter(description = "Folio único de la transacción", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable String folio,
            @Valid @RequestBody ConciliarTransaccionRequest request) {
        log.info("REST: Solicitud de conciliación recibida: folio={}, accion={}", folio, request.getAccion());
        TransaccionResponse response = transaccionService.conciliarTransaccion(folio, request);
        return ResponseEntity.ok(response);
    }
}
