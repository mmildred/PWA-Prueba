package com.proyecto.servicios.controller;

import com.proyecto.servicios.entity.onboarding.Cliente;
import com.proyecto.servicios.model.onboarding.ClienteDTO;
import com.proyecto.servicios.service.ClienteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;

    @PostMapping
    public ResponseEntity<Cliente> registrar(@Valid @RequestBody ClienteDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.registrarCliente(dto));
    }

    @GetMapping
    public ResponseEntity<List<Cliente>> obtenerTodos() {
        return ResponseEntity.ok(clienteService.obtenerTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Cliente> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(clienteService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Cliente> actualizar(@PathVariable Long id, @Valid @RequestBody ClienteDTO dto) {
        return ResponseEntity.ok(clienteService.actualizarCliente(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> bajaLogica(@PathVariable Long id) {
        clienteService.bajaLogica(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/curp/{curp}")
    public ResponseEntity<Cliente> buscarPorCurp(@PathVariable String curp) {
        return ResponseEntity.ok(clienteService.buscarPorCurp(curp));
    }

    @GetMapping("/rfc/{rfc}")
    public ResponseEntity<Cliente> buscarPorRfc(@PathVariable String rfc) {
        return ResponseEntity.ok(clienteService.buscarPorRfc(rfc));
    }

    @GetMapping("/correo/{correo}")
    public ResponseEntity<Cliente> buscarPorCorreo(@PathVariable String correo) {
        return ResponseEntity.ok(clienteService.buscarPorCorreo(correo));
    }

    @GetMapping("/activos")
    public ResponseEntity<List<Cliente>> obtenerActivos() {
        return ResponseEntity.ok(clienteService.buscarActivos());
    }

    @GetMapping("/rango-fechas")
    public ResponseEntity<List<Cliente>> obtenerPorFechas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fin) {
        return ResponseEntity.ok(clienteService.buscarPorRangoFechas(inicio, fin));
    }
}