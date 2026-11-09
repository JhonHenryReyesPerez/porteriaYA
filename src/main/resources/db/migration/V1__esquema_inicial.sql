-- PorteriaYa v2 - esquema inicial (§12).
-- Convenciones: timestamptz para todo instante (se mapea a Instant, zona
-- America/Bogota), time para las franjas horarias de recurrencia.
-- Privacidad (§6): del visitante solo se guardan nombre y los ultimos 4
-- digitos del documento. El documento completo NUNCA entra a la base.

-- ---------------------------------------------------------------- residente
CREATE TABLE residente (
    id              BIGSERIAL PRIMARY KEY,
    torre           VARCHAR(10)  NOT NULL,
    apartamento     VARCHAR(10)  NOT NULL,
    nombre          VARCHAR(120) NOT NULL,
    correo          VARCHAR(160),
    telefono        VARCHAR(30),
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_residente_torre_apto UNIQUE (torre, apartamento)
);

COMMENT ON TABLE residente IS 'Un registro por apartamento: alimenta el selector de torre y apartamento (§7)';

-- ------------------------------------------------------------- autorizacion
CREATE TABLE autorizacion (
    id                      BIGSERIAL PRIMARY KEY,
    residente_id            BIGINT       NOT NULL REFERENCES residente (id),
    tipo                    VARCHAR(30)  NOT NULL,
    codigo                  CHAR(6)      NOT NULL,
    visitante_nombre        VARCHAR(120) NOT NULL,
    visitante_doc4          CHAR(4),
    estado                  VARCHAR(20)  NOT NULL,
    valida_desde            TIMESTAMPTZ  NOT NULL,
    valida_hasta            TIMESTAMPTZ  NOT NULL,
    dias_semana             VARCHAR(20),
    hora_desde              TIME,
    hora_hasta              TIME,
    usos_max                INTEGER,
    usos_actuales           INTEGER      NOT NULL DEFAULT 0,
    duracion_esperada_min   INTEGER      NOT NULL,
    creado_en               TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_autorizacion_tipo
        CHECK (tipo IN ('VISITA', 'DOMICILIARIO', 'SERVICIO_RECURRENTE', 'MUDANZA')),
    CONSTRAINT ck_autorizacion_estado
        CHECK (estado IN ('ACTIVA', 'AGOTADA', 'VENCIDA', 'CANCELADA')),
    CONSTRAINT ck_autorizacion_ventana
        CHECK (valida_hasta > valida_desde),
    -- usos_max NULL = usos ilimitados (servicio recurrente, mudanza). §6
    CONSTRAINT ck_autorizacion_usos_max
        CHECK (usos_max IS NULL OR usos_max > 0),
    CONSTRAINT ck_autorizacion_usos_actuales
        CHECK (usos_actuales >= 0),
    CONSTRAINT ck_autorizacion_usos_coherentes
        CHECK (usos_max IS NULL OR usos_actuales <= usos_max),
    -- Recurrencia: o se define la franja completa o ninguna de las dos horas
    CONSTRAINT ck_autorizacion_franja
        CHECK ((hora_desde IS NULL) = (hora_hasta IS NULL)),
    CONSTRAINT ck_autorizacion_duracion
        CHECK (duracion_esperada_min > 0)
);

-- Unicidad del codigo SOLO mientras la autorizacion esta activa (§12):
-- indice parcial, para poder reutilizar codigos de autorizaciones cerradas.
CREATE UNIQUE INDEX ux_autorizacion_codigo_activa
    ON autorizacion (codigo) WHERE estado = 'ACTIVA';

CREATE INDEX ix_autorizacion_residente ON autorizacion (residente_id);
CREATE INDEX ix_autorizacion_estado_ventana ON autorizacion (estado, valida_hasta);

COMMENT ON COLUMN autorizacion.usos_max IS 'NULL = ilimitado. El UPDATE atomico debe llevar (usos_max IS NULL OR usos_actuales < usos_max)';
COMMENT ON COLUMN autorizacion.dias_semana IS 'Recurrencia: dias ISO 1-7 separados por coma. NULL = no recurrente';
COMMENT ON COLUMN autorizacion.visitante_doc4 IS 'Solo los ultimos 4 digitos del documento (Ley 1581 de 2012)';

-- -------------------------------------------------- solicitud no anunciada
CREATE TABLE solicitud_no_anunciada (
    id                  BIGSERIAL PRIMARY KEY,
    residente_id        BIGINT       NOT NULL REFERENCES residente (id),
    visitante_nombre    VARCHAR(120) NOT NULL,
    visitante_doc4      CHAR(4),
    motivo              VARCHAR(200),
    estado              VARCHAR(30)  NOT NULL,
    vigilante           VARCHAR(80)  NOT NULL,
    creado_en           TIMESTAMPTZ  NOT NULL,
    vence_en            TIMESTAMPTZ  NOT NULL,
    resuelto_en         TIMESTAMPTZ,
    ingreso_id          BIGINT,
    CONSTRAINT ck_solicitud_estado CHECK (estado IN (
        'PENDIENTE', 'APROBADA', 'RECHAZADA', 'SIN_RESPUESTA',
        'AUTORIZADA_POR_VIGILANTE', 'DENEGADA')),
    CONSTRAINT ck_solicitud_vence CHECK (vence_en > creado_en)
);

CREATE INDEX ix_solicitud_estado_creado ON solicitud_no_anunciada (estado, creado_en);

COMMENT ON COLUMN solicitud_no_anunciada.ingreso_id IS 'Nulo hasta que la solicitud se aprueba: el Ingreso solo existe si hubo aprobacion (§6)';

-- ---------------------------------------------------------------- ingreso
CREATE TABLE ingreso (
    id                      BIGSERIAL PRIMARY KEY,
    origen                  VARCHAR(20)  NOT NULL,
    autorizacion_id         BIGINT       REFERENCES autorizacion (id),
    residente_id            BIGINT       NOT NULL REFERENCES residente (id),
    visitante_nombre        VARCHAR(120) NOT NULL,
    visitante_doc4          CHAR(4),
    estado                  VARCHAR(25)  NOT NULL,
    vigilante               VARCHAR(80)  NOT NULL,
    duracion_esperada_min   INTEGER      NOT NULL,
    permanencia_avisada     BOOLEAN      NOT NULL DEFAULT FALSE,
    ingreso_en              TIMESTAMPTZ  NOT NULL,
    salida_en               TIMESTAMPTZ,
    CONSTRAINT ck_ingreso_origen CHECK (origen IN ('AUTORIZACION', 'NO_ANUNCIADA')),
    CONSTRAINT ck_ingreso_estado CHECK (estado IN ('DENTRO', 'SALIO', 'SALIDA_NO_REGISTRADA')),
    -- La autorizacion existe si y solo si el ingreso viene de un codigo
    CONSTRAINT ck_ingreso_autorizacion_coherente CHECK (
        (origen = 'AUTORIZACION' AND autorizacion_id IS NOT NULL)
        OR (origen = 'NO_ANUNCIADA' AND autorizacion_id IS NULL)),
    CONSTRAINT ck_ingreso_salida CHECK (salida_en IS NULL OR salida_en >= ingreso_en)
);

CREATE INDEX ix_ingreso_estado ON ingreso (estado);
CREATE INDEX ix_ingreso_autorizacion ON ingreso (autorizacion_id);
CREATE INDEX ix_ingreso_residente_fecha ON ingreso (residente_id, ingreso_en);

ALTER TABLE solicitud_no_anunciada
    ADD CONSTRAINT fk_solicitud_ingreso FOREIGN KEY (ingreso_id) REFERENCES ingreso (id);

COMMENT ON COLUMN ingreso.permanencia_avisada IS 'Bandera de PERMANENCIA_EXCEDIDA: garantiza que el aviso se emite una sola vez (§6)';
COMMENT ON COLUMN ingreso.duracion_esperada_min IS 'Copiada de la autorizacion al ingresar: cambiarla despues no altera ingresos ya registrados';

-- ------------------------------------------------------- token de aprobacion
CREATE TABLE token_aprobacion (
    id              BIGSERIAL PRIMARY KEY,
    token_hash      CHAR(64)    NOT NULL,
    solicitud_id    BIGINT      NOT NULL REFERENCES solicitud_no_anunciada (id),
    accion          VARCHAR(20),
    creado_en       TIMESTAMPTZ NOT NULL,
    vence_en        TIMESTAMPTZ NOT NULL,
    usado_en        TIMESTAMPTZ,
    CONSTRAINT ux_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_token_vence CHECK (vence_en > creado_en)
);

CREATE INDEX ix_token_solicitud ON token_aprobacion (solicitud_id);

COMMENT ON TABLE token_aprobacion IS 'Token de un solo uso con vencimiento. Se guarda el SHA-256, nunca el token en claro (§6)';

-- ---------------------------------------------------------------- paquete
CREATE TABLE paquete (
    id              BIGSERIAL PRIMARY KEY,
    residente_id    BIGINT       NOT NULL REFERENCES residente (id),
    descripcion     VARCHAR(200) NOT NULL,
    remitente       VARCHAR(120),
    codigo_retiro   CHAR(6)      NOT NULL,
    estado          VARCHAR(20)  NOT NULL,
    vigilante       VARCHAR(80)  NOT NULL,
    recibido_en     TIMESTAMPTZ  NOT NULL,
    entregado_en    TIMESTAMPTZ,
    entregado_a     VARCHAR(120),
    aviso_24h       BOOLEAN      NOT NULL DEFAULT FALSE,
    aviso_72h       BOOLEAN      NOT NULL DEFAULT FALSE,
    escalado        BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_paquete_estado CHECK (estado IN ('RECIBIDO', 'ENTREGADO', 'DEVUELTO')),
    CONSTRAINT ck_paquete_entrega CHECK (entregado_en IS NULL OR entregado_en >= recibido_en)
);

CREATE INDEX ix_paquete_estado_recibido ON paquete (estado, recibido_en);
CREATE INDEX ix_paquete_residente ON paquete (residente_id);

COMMENT ON COLUMN paquete.aviso_24h IS 'Una bandera por aviso para que el recordatorio no se repita (§6)';

-- ------------------------------------------------------- autorizado_retiro
CREATE TABLE autorizado_retiro (
    id              BIGSERIAL PRIMARY KEY,
    residente_id    BIGINT       NOT NULL REFERENCES residente (id),
    nombre          VARCHAR(120) NOT NULL,
    doc4            CHAR(4),
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_autorizado_retiro UNIQUE (residente_id, nombre)
);

-- ----------------------------------------------------------- no_permitido
CREATE TABLE no_permitido (
    id              BIGSERIAL PRIMARY KEY,
    nombre          VARCHAR(120) NOT NULL,
    doc4            CHAR(4),
    motivo          VARCHAR(200),
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    registrado_por  VARCHAR(80)  NOT NULL,
    creado_en       TIMESTAMPTZ  NOT NULL
);

-- Indices funcionales: sirven para cualquiera de las variantes de coincidencia
-- que quedan por decidir en §20.1
CREATE INDEX ix_no_permitido_nombre ON no_permitido (lower(nombre)) WHERE activo;
CREATE INDEX ix_no_permitido_doc4 ON no_permitido (doc4) WHERE activo;

-- --------------------------------------------------------------- bitacora
-- Append-only (§6): sin columna de actualizacion, sin endpoints PUT/PATCH/DELETE
-- y sin @Setter publico en la entidad. No se edita ni se borra desde la API.
CREATE TABLE bitacora (
    id          BIGSERIAL PRIMARY KEY,
    fecha       TIMESTAMPTZ  NOT NULL,
    actor       VARCHAR(80)  NOT NULL,
    rol         VARCHAR(20)  NOT NULL,
    entidad     VARCHAR(40)  NOT NULL,
    entidad_id  BIGINT,
    accion      VARCHAR(60)  NOT NULL,
    detalle     VARCHAR(500)
);

CREATE INDEX ix_bitacora_fecha_actor_entidad ON bitacora (fecha, actor, entidad);

-- -------------------------------------------------------------- documento
CREATE TABLE documento (
    id              BIGSERIAL PRIMARY KEY,
    titulo          VARCHAR(200) NOT NULL,
    tipo            VARCHAR(20)  NOT NULL,
    audiencia       VARCHAR(40)  NOT NULL,
    version         VARCHAR(20)  NOT NULL,
    nombre_archivo  VARCHAR(255) NOT NULL,
    hash_contenido  CHAR(64)     NOT NULL,
    estado          VARCHAR(20)  NOT NULL,
    motivo_error    VARCHAR(500),
    chunks          INTEGER      NOT NULL DEFAULT 0,
    subido_en       TIMESTAMPTZ  NOT NULL,
    indexado_en     TIMESTAMPTZ,
    CONSTRAINT ck_documento_tipo CHECK (tipo IN ('REGLAMENTO', 'PROTOCOLO', 'CIRCULAR', 'FAQ')),
    CONSTRAINT ck_documento_estado CHECK (estado IN ('SUBIDO', 'INDEXANDO', 'INDEXADO', 'ERROR')),
    CONSTRAINT ck_documento_chunks CHECK (chunks >= 0)
);

CREATE INDEX ix_documento_hash ON documento (hash_contenido);
CREATE INDEX ix_documento_estado ON documento (estado);

COMMENT ON COLUMN documento.audiencia IS 'Conjunto: TODOS, o lista separada por coma de RESIDENTE/VIGILANTE/ADMINISTRADOR (§11)';

-- ------------------------------------------------------------ consulta_rag
CREATE TABLE consulta_rag (
    id                  BIGSERIAL PRIMARY KEY,
    pregunta            VARCHAR(1000) NOT NULL,
    rol                 VARCHAR(20)   NOT NULL,
    actor               VARCHAR(80)   NOT NULL,
    respuesta           TEXT,
    fuentes             VARCHAR(1000),
    mejor_similitud     NUMERIC(5, 4),
    sin_respuesta       BOOLEAN       NOT NULL DEFAULT FALSE,
    latencia_ms         INTEGER,
    feedback            SMALLINT,
    creado_en           TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_consulta_feedback CHECK (feedback IS NULL OR feedback IN (-1, 1))
);

CREATE INDEX ix_consulta_sin_respuesta ON consulta_rag (sin_respuesta, creado_en);

COMMENT ON COLUMN consulta_rag.pregunta IS 'Sanitizada antes de guardar: sin nombres, documentos, correos ni telefonos (§11)';
