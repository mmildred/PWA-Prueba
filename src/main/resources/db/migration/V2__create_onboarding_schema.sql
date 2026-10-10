-- =============================================================================
-- Migración V2: Esquema para Onboarding de Clientes Personas Físicas
-- Aplica mejores prácticas de tipos de datos, índices y restricciones en PostgreSQL
-- =============================================================================

-- 1. Tabla de Domicilios
CREATE TABLE IF NOT EXISTS domicilios (
    id                  BIGSERIAL PRIMARY KEY,
    calle               VARCHAR(100)    NOT NULL,
    numero_exterior     VARCHAR(20)     NOT NULL,
    numero_interior     VARCHAR(20),
    colonia             VARCHAR(100)    NOT NULL,
    municipio           VARCHAR(100)    NOT NULL,
    estado              VARCHAR(50)     NOT NULL,
    codigo_postal       CHAR(5)         NOT NULL,
    pais                VARCHAR(50)     NOT NULL DEFAULT 'México',
    fecha_creacion      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Tabla de Clientes
CREATE TABLE IF NOT EXISTS clientes (
    id                  BIGSERIAL PRIMARY KEY,
    nombre              VARCHAR(50)     NOT NULL,
    segundo_nombre      VARCHAR(50),
    apellido_paterno    VARCHAR(50)     NOT NULL,
    apellido_materno    VARCHAR(50)     NOT NULL,
    fecha_nacimiento    DATE            NOT NULL,
    curp                VARCHAR(18)     NOT NULL,
    rfc                 VARCHAR(13)     NOT NULL,
    sexo                VARCHAR(20)     NOT NULL,
    nacionalidad        VARCHAR(50)     NOT NULL,
    estado_civil        VARCHAR(30)     NOT NULL,
    correo_electronico  VARCHAR(100)    NOT NULL,
    telefono_movil      VARCHAR(10)     NOT NULL,
    telefono_alternativo VARCHAR(10),
    domicilio_id        BIGINT          NOT NULL,
    ocupacion           VARCHAR(80)     NOT NULL,
    empresa             VARCHAR(100)    NOT NULL,
    ingreso_mensual     NUMERIC(15, 2)  NOT NULL CHECK (ingreso_mensual > 0),
    activo              BOOLEAN         NOT NULL DEFAULT TRUE,
    fecha_creacion      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_clientes_curp UNIQUE (curp),
    CONSTRAINT uq_clientes_rfc UNIQUE (rfc),
    CONSTRAINT uq_clientes_correo UNIQUE (correo_electronico),
    CONSTRAINT uq_clientes_domicilio UNIQUE (domicilio_id),
    CONSTRAINT fk_clientes_domicilio FOREIGN KEY (domicilio_id) REFERENCES domicilios(id) ON DELETE RESTRICT
);

-- 3. Tabla de Cuentas Bancarias
CREATE TABLE IF NOT EXISTS cuentas (
    id                  BIGSERIAL PRIMARY KEY,
    numero_cuenta       VARCHAR(20)     NOT NULL,
    cliente_id          BIGINT          NOT NULL,
    saldo               NUMERIC(15, 2)  NOT NULL DEFAULT 0.00 CHECK (saldo >= 0),
    tipo_cuenta         VARCHAR(30)     NOT NULL DEFAULT 'AHORRO',
    estatus             VARCHAR(20)     NOT NULL DEFAULT 'ACTIVA',
    fecha_apertura      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cuentas_numero UNIQUE (numero_cuenta),
    CONSTRAINT fk_cuentas_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(id) ON DELETE CASCADE
);

-- 4. Índices para optimización de consultas
CREATE INDEX IF NOT EXISTS idx_clientes_curp ON clientes(curp);
CREATE INDEX IF NOT EXISTS idx_clientes_rfc ON clientes(rfc);
CREATE INDEX IF NOT EXISTS idx_clientes_correo ON clientes(correo_electronico);
CREATE INDEX IF NOT EXISTS idx_clientes_activo ON clientes(activo);
CREATE INDEX IF NOT EXISTS idx_clientes_fecha_creacion ON clientes(fecha_creacion);

CREATE INDEX IF NOT EXISTS idx_cuentas_numero ON cuentas(numero_cuenta);
CREATE INDEX IF NOT EXISTS idx_cuentas_cliente_id ON cuentas(cliente_id);
CREATE INDEX IF NOT EXISTS idx_cuentas_estatus ON cuentas(estatus);

CREATE INDEX IF NOT EXISTS idx_domicilios_cp ON domicilios(codigo_postal);
