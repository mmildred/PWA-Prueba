package com.proyecto.servicios.repositorys.onboarding;

import com.proyecto.servicios.entity.onboarding.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByCurp(String curp);
    Optional<Cliente> findByRfc(String rfc);
    Optional<Cliente> findByCorreoElectronico(String correoElectronico);

    List<Cliente> findByActivoTrue();
    List<Cliente> findByFechaRegistroBetween(LocalDateTime inicio, LocalDateTime fin);

    boolean existsByCurp(String curp);
    boolean existsByRfc(String rfc);
    boolean existsByCorreoElectronico(String correoElectronico);
}