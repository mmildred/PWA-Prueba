package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.onboarding.Cliente;
import com.proyecto.servicios.entity.onboarding.Cuenta;
import com.proyecto.servicios.entity.onboarding.Domicilio;
import com.proyecto.servicios.exception.BusinessException;
import com.proyecto.servicios.exception.ClienteNoEncontradoException;
import com.proyecto.servicios.model.onboarding.ClienteDTO;
import com.proyecto.servicios.repositorys.onboarding.ClienteRepository;
import com.proyecto.servicios.repositorys.onboarding.CuentaRepository;
import com.proyecto.servicios.service.ClienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class ClienteServiceImpl implements ClienteService {

    private final ClienteRepository clienteRepository;
    private final CuentaRepository cuentaRepository;

    @Override
    @Transactional
    public Cliente registrarCliente(ClienteDTO dto) {
        // Regla: Mayoría de edad (+18 años)
        if (dto.getFechaNacimiento() == null || Period.between(dto.getFechaNacimiento(), LocalDate.now()).getYears() < 18) {
            throw new BusinessException("El cliente debe ser mayor de edad (18 años o más).");
        }

        // Reglas de Unicidad
        if (clienteRepository.existsByCurp(dto.getCurp())) {
            throw new BusinessException("La CURP ya se encuentra registrada en el sistema.");
        }
        if (clienteRepository.existsByRfc(dto.getRfc())) {
            throw new BusinessException("El RFC ya se encuentra registrado en el sistema.");
        }
        if (clienteRepository.existsByCorreoElectronico(dto.getCorreoElectronico())) {
            throw new BusinessException("El correo electrónico ya se encuentra registrado en el sistema.");
        }

        // Mapeo e inserción de Domicilio
        Domicilio domicilio = Domicilio.builder()
                .calle(dto.getDomicilio().getCalle())
                .numeroExterior(dto.getDomicilio().getNumeroExterior())
                .numeroInterior(dto.getDomicilio().getNumeroInterior())
                .colonia(dto.getDomicilio().getColonia())
                .municipio(dto.getDomicilio().getMunicipio())
                .estado(dto.getDomicilio().getEstado())
                .codigoPostal(dto.getDomicilio().getCodigoPostal())
                .pais(dto.getDomicilio().getPais())
                .build();

        // Mapeo e inserción de Cliente
        Cliente cliente = Cliente.builder()
                .nombre(dto.getNombre())
                .segundoNombre(dto.getSegundoNombre())
                .apellidoPaterno(dto.getApellidoPaterno())
                .apellidoMaterno(dto.getApellidoMaterno())
                .fechaNacimiento(dto.getFechaNacimiento())
                .curp(dto.getCurp())
                .rfc(dto.getRfc())
                .sexo(dto.getSexo())
                .nacionalidad(dto.getNacionalidad())
                .estadoCivil(dto.getEstadoCivil())
                .correoElectronico(dto.getCorreoElectronico())
                .telefonoMovil(dto.getTelefonoMovil())
                .telefonoAlternativo(dto.getTelefonoAlternativo())
                .ocupacion(dto.getOcupacion())
                .empresa(dto.getEmpresa())
                .ingresoMensual(dto.getIngresoMensual())
                .domicilio(domicilio)
                .build();

        Cliente clienteGuardado = clienteRepository.save(cliente);

        // Creación Automática de Cuenta asociada
        Cuenta cuenta = Cuenta.builder()
                .numeroCuenta(generarNumeroCuentaUnico())
                .saldo(new BigDecimal("1000.00")) // Saldo inicial por defecto del sistema
                .estatus("ACTIVA")
                .cliente(clienteGuardado)
                .build();

        cuentaRepository.save(cuenta);

        return clienteGuardado;
    }

    @Override
    @Transactional
    public Cliente actualizarCliente(Long id, ClienteDTO dto) {
        Cliente cliente = buscarPorId(id);

        if (Boolean.FALSE.equals(cliente.getActivo())) {
            throw new BusinessException("No se puede actualizar la información de un cliente inactivo.");
        }

        // Actualización de datos personales (Se conservan e inmutan CURP y RFC)
        cliente.setNombre(dto.getNombre());
        cliente.setSegundoNombre(dto.getSegundoNombre());
        cliente.setApellidoPaterno(dto.getApellidoPaterno());
        cliente.setApellidoMaterno(dto.getApellidoMaterno());
        cliente.setFechaNacimiento(dto.getFechaNacimiento());
        cliente.setSexo(dto.getSexo());
        cliente.setNacionalidad(dto.getNacionalidad());
        cliente.setEstadoCivil(dto.getEstadoCivil());

        // Actualización de contacto y laboral
        cliente.setCorreoElectronico(dto.getCorreoElectronico());
        cliente.setTelefonoMovil(dto.getTelefonoMovil());
        cliente.setTelefonoAlternativo(dto.getTelefonoAlternativo());
        cliente.setOcupacion(dto.getOcupacion());
        cliente.setEmpresa(dto.getEmpresa());
        cliente.setIngresoMensual(dto.getIngresoMensual());

        // Actualización de Domicilio
        Domicilio dom = cliente.getDomicilio();
        if (dom != null && dto.getDomicilio() != null) {
            dom.setCalle(dto.getDomicilio().getCalle());
            dom.setNumeroExterior(dto.getDomicilio().getNumeroExterior());
            dom.setNumeroInterior(dto.getDomicilio().getNumeroInterior());
            dom.setColonia(dto.getDomicilio().getColonia());
            dom.setMunicipio(dto.getDomicilio().getMunicipio());
            dom.setEstado(dto.getDomicilio().getEstado());
            dom.setCodigoPostal(dto.getDomicilio().getCodigoPostal());
            dom.setPais(dto.getDomicilio().getPais());
        }

        return clienteRepository.save(cliente);
    }

    @Override
    @Transactional
    public void bajaLogica(Long id) {
        Cliente cliente = buscarPorId(id);
        cliente.setActivo(false);

        // Desactivación lógica de la cuenta asociada
        cuentaRepository.findByClienteId(id).ifPresent(cuenta -> cuenta.setEstatus("INACTIVA"));

        clienteRepository.save(cliente);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Cliente> obtenerTodos() {
        return clienteRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Cliente buscarPorId(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new ClienteNoEncontradoException("Cliente no encontrado con ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Cliente buscarPorCurp(String curp) {
        return clienteRepository.findByCurp(curp)
                .orElseThrow(() -> new ClienteNoEncontradoException("Cliente no encontrado con CURP: " + curp));
    }

    @Override
    @Transactional(readOnly = true)
    public Cliente buscarPorRfc(String rfc) {
        return clienteRepository.findByRfc(rfc)
                .orElseThrow(() -> new ClienteNoEncontradoException("Cliente no encontrado con RFC: " + rfc));
    }

    @Override
    @Transactional(readOnly = true)
    public Cliente buscarPorCorreo(String correo) {
        return clienteRepository.findByCorreoElectronico(correo)
                .orElseThrow(() -> new ClienteNoEncontradoException("Cliente no encontrado con correo: " + correo));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Cliente> buscarActivos() {
        return clienteRepository.findByActivoTrue();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Cliente> buscarPorRangoFechas(LocalDateTime inicio, LocalDateTime fin) {
        return clienteRepository.findByFechaRegistroBetween(inicio, fin);
    }

    private String generarNumeroCuentaUnico() {
        String numCuenta;
        Random random = new Random();
        do {
            long number = (long) (random.nextDouble() * 9000000000L) + 1000000000L;
            numCuenta = "CTA-" + number;
        } while (cuentaRepository.existsByNumeroCuenta(numCuenta));
        return numCuenta;
    }
}