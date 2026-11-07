-- =============================================================================
-- SCRIPT DDL: ESQUEMA DE TRANSACCIONES FINANCIERAS (POSTGRESQL)
-- Integración con Pasarela Gestopago (sendTx.do)
-- Entregable de Base de Datos - Proyecto Integrador
-- =============================================================================

CREATE TABLE IF NOT EXISTS transacciones (
    id                          BIGSERIAL PRIMARY KEY,
    folio                       VARCHAR(36)     NOT NULL,
    idempotency_key             VARCHAR(100)    NOT NULL,
    cuenta_id                   BIGINT          NOT NULL,
    numero_cuenta               VARCHAR(20)     NOT NULL,
    cliente_id                  BIGINT          NOT NULL,
    id_servicio                 VARCHAR(50)     NOT NULL,
    id_producto                 VARCHAR(50)     NOT NULL,
    nombre_producto             VARCHAR(100),
    monto                       NUMERIC(15, 2)  NOT NULL CHECK (monto > 0),
    referencia                  VARCHAR(100)    NOT NULL,
    estatus                     VARCHAR(30)     NOT NULL,
    saldo_anterior              NUMERIC(15, 2)  NOT NULL,
    saldo_posterior             NUMERIC(15, 2)  NOT NULL,
    folio_proveedor             VARCHAR(100),
    autorizacion_proveedor      VARCHAR(100),
    mensaje_proveedor           VARCHAR(255),
    codigo_respuesta_proveedor  VARCHAR(50),
    motivo_falla                VARCHAR(255),
    fecha_creacion              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_transacciones_folio UNIQUE (folio),
    CONSTRAINT uq_transacciones_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_transacciones_cuenta FOREIGN KEY (cuenta_id) REFERENCES cuentas(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transacciones_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(id) ON DELETE RESTRICT
);

-- Índices de consulta frecuente y conciliación
CREATE INDEX IF NOT EXISTS idx_transacciones_folio ON transacciones(folio);
CREATE INDEX IF NOT EXISTS idx_transacciones_idempotency ON transacciones(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_transacciones_cuenta ON transacciones(cuenta_id);
CREATE INDEX IF NOT EXISTS idx_transacciones_num_cuenta ON transacciones(numero_cuenta);
CREATE INDEX IF NOT EXISTS idx_transacciones_cliente ON transacciones(cliente_id);
CREATE INDEX IF NOT EXISTS idx_transacciones_estatus ON transacciones(estatus);
CREATE INDEX IF NOT EXISTS idx_transacciones_fecha ON transacciones(fecha_creacion);
