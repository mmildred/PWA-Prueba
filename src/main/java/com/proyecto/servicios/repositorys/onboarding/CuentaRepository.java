package com.proyecto.servicios.repositorys.onboarding;

import com.proyecto.servicios.entity.onboarding.Cuenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    Optional<Cuenta> findByNumeroCuenta(String numeroCuenta);
    Optional<Cuenta> findByClienteId(Long clienteId);
    List<Cuenta> findByEstatus(String estatus);
    boolean existsByNumeroCuenta(String numeroCuenta);
}