package com.proyecto.servicios.controller;

import com.proyecto.servicios.entity.onboarding.Cuenta;
import com.proyecto.servicios.exception.ClienteNoEncontradoException;
import com.proyecto.servicios.repositorys.onboarding.CuentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/cuentas")
@RequiredArgsConstructor
public class CuentaController {

    private final CuentaRepository cuentaRepository;

    @GetMapping("/{numeroCuenta}")
    public ResponseEntity<Cuenta> obtenerPorNumero(@PathVariable String numeroCuenta) {
        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .orElseThrow(() -> new ClienteNoEncontradoException("Cuenta no encontrada con número: " + numeroCuenta));
        return ResponseEntity.ok(cuenta);
    }

    @GetMapping("/{numeroCuenta}/saldo")
    public ResponseEntity<BigDecimal> obtenerSaldo(@PathVariable String numeroCuenta) {
        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .orElseThrow(() -> new ClienteNoEncontradoException("Cuenta no encontrada con número: " + numeroCuenta));
        return ResponseEntity.ok(cuenta.getSaldo());
    }

    @GetMapping("/activas")
    public ResponseEntity<List<Cuenta>> obtenerActivas() {
        return ResponseEntity.ok(cuentaRepository.findByEstatus("ACTIVA"));
    }
}