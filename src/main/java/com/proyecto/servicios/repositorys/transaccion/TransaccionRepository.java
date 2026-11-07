package com.proyecto.servicios.repositorys.transaccion;

import com.proyecto.servicios.entity.transaccion.EstatusTransaccion;
import com.proyecto.servicios.entity.transaccion.Transaccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransaccionRepository extends JpaRepository<Transaccion, Long> {

    Optional<Transaccion> findByFolio(String folio);

    Optional<Transaccion> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    List<Transaccion> findByNumeroCuentaOrderByFechaCreacionDesc(String numeroCuenta);

    List<Transaccion> findByClienteIdOrderByFechaCreacionDesc(Long clienteId);

    List<Transaccion> findByEstatus(EstatusTransaccion estatus);

    List<Transaccion> findByFechaCreacionBetween(LocalDateTime inicio, LocalDateTime fin);
}
