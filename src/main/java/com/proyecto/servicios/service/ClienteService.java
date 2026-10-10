package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.onboarding.Cliente;
import com.proyecto.servicios.model.onboarding.ClienteDTO;

import java.time.LocalDateTime;
import java.util.List;

public interface ClienteService {

    Cliente registrarCliente(ClienteDTO dto);

    Cliente actualizarCliente(Long id, ClienteDTO dto);

    void bajaLogica(Long id);

    List<Cliente> obtenerTodos();

    Cliente buscarPorId(Long id);

    Cliente buscarPorCurp(String curp);

    Cliente buscarPorRfc(String rfc);

    Cliente buscarPorCorreo(String correo);

    List<Cliente> buscarActivos();

    List<Cliente> buscarPorRangoFechas(LocalDateTime inicio, LocalDateTime fin);
}