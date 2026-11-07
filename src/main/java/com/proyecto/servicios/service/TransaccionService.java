package com.proyecto.servicios.service;

import com.proyecto.servicios.model.transaccion.ConciliarTransaccionRequest;
import com.proyecto.servicios.model.transaccion.PagarServicioRequest;
import com.proyecto.servicios.model.transaccion.TransaccionResponse;

import java.util.List;

public interface TransaccionService {

    /**
     * Procesa una transacción financiera para pago de servicios / recargas:
     * 1. Valida idempotencia para prevenir duplicados.
     * 2. Bloquea la cuenta pesimísticamente para garantizar consistencia ante concurrencia.
     * 3. Valida titularidad, cliente activo, cuenta activa y saldo suficiente.
     * 4. Valida servicio, producto y montos autorizados contra el catálogo.
     * 5. Realiza el débito preventivo de saldo.
     * 6. Envía la transacción al proveedor Gestopago (sendTx.do) con Bearer Token seguro.
     * 7. En caso de éxito, confirma la transacción.
     * 8. En caso de rechazo del proveedor, ejecuta el reverso automático del saldo.
     * 9. En caso de timeout / error de comunicación, NO reversa el saldo erróneamente:
     *    marca la transacción en PENDIENTE_CONCILIACION para verificación e investigación.
     */
    TransaccionResponse procesarPagoServicio(PagarServicioRequest request);

    /**
     * Consulta una transacción por su folio único del sistema.
     */
    TransaccionResponse consultarPorFolio(String folio);

    /**
     * Consulta el historial de transacciones asociadas a una cuenta bancaria.
     */
    List<TransaccionResponse> consultarPorCuenta(String numeroCuenta);

    /**
     * Consulta el historial de transacciones de un cliente.
     */
    List<TransaccionResponse> consultarPorCliente(Long clienteId);

    /**
     * Concilia una transacción que quedó en estatus PENDIENTE_CONCILIACION.
     * Permite confirmar la operación si el proveedor la ejecutó o reversar el saldo al cliente
     * si se confirmó que nunca se procesó externamente.
     */
    TransaccionResponse conciliarTransaccion(String folio, ConciliarTransaccionRequest request);
}
