-- Convierte todas las columnas CHAR(n) a VARCHAR(n) con su CHECK de longitud.
--
-- Dos motivos:
--
-- 1. CHAR(n) en PostgreSQL rellena con espacios a la derecha. Un doc4 de '123'
--    se guarda como '123 ', y comparar eso con lo que llega de la API produce
--    falsos negativos justo donde mas duele: el cotejo con la lista de no
--    permitidos. La propia documentacion de PostgreSQL desaconseja char(n).
--
-- 2. Hibernate mapea String a varchar, asi que con ddl-auto=validate el
--    arranque fallaba con "wrong column type encountered ... found [bpchar]".
--
-- El TRIM del USING limpia cualquier relleno que la V1 haya dejado ya en la
-- base. Los indices se reconstruyen solos al cambiar el tipo.

ALTER TABLE autorizacion
    ALTER COLUMN codigo TYPE VARCHAR(6) USING trim(codigo),
    ALTER COLUMN visitante_doc4 TYPE VARCHAR(4) USING trim(visitante_doc4);

ALTER TABLE autorizacion
    ADD CONSTRAINT ck_autorizacion_codigo_longitud CHECK (char_length(codigo) = 6),
    ADD CONSTRAINT ck_autorizacion_doc4_longitud
        CHECK (visitante_doc4 IS NULL OR char_length(visitante_doc4) = 4);

ALTER TABLE solicitud_no_anunciada
    ALTER COLUMN visitante_doc4 TYPE VARCHAR(4) USING trim(visitante_doc4);

ALTER TABLE solicitud_no_anunciada
    ADD CONSTRAINT ck_solicitud_doc4_longitud
        CHECK (visitante_doc4 IS NULL OR char_length(visitante_doc4) = 4);

ALTER TABLE ingreso
    ALTER COLUMN visitante_doc4 TYPE VARCHAR(4) USING trim(visitante_doc4);

ALTER TABLE ingreso
    ADD CONSTRAINT ck_ingreso_doc4_longitud
        CHECK (visitante_doc4 IS NULL OR char_length(visitante_doc4) = 4);

ALTER TABLE token_aprobacion
    ALTER COLUMN token_hash TYPE VARCHAR(64) USING trim(token_hash);

ALTER TABLE token_aprobacion
    ADD CONSTRAINT ck_token_hash_longitud CHECK (char_length(token_hash) = 64);

ALTER TABLE paquete
    ALTER COLUMN codigo_retiro TYPE VARCHAR(6) USING trim(codigo_retiro);

ALTER TABLE paquete
    ADD CONSTRAINT ck_paquete_codigo_longitud CHECK (char_length(codigo_retiro) = 6);

ALTER TABLE autorizado_retiro
    ALTER COLUMN doc4 TYPE VARCHAR(4) USING trim(doc4);

ALTER TABLE autorizado_retiro
    ADD CONSTRAINT ck_autorizado_retiro_doc4_longitud
        CHECK (doc4 IS NULL OR char_length(doc4) = 4);

ALTER TABLE no_permitido
    ALTER COLUMN doc4 TYPE VARCHAR(4) USING trim(doc4);

ALTER TABLE no_permitido
    ADD CONSTRAINT ck_no_permitido_doc4_longitud
        CHECK (doc4 IS NULL OR char_length(doc4) = 4);

ALTER TABLE documento
    ALTER COLUMN hash_contenido TYPE VARCHAR(64) USING trim(hash_contenido);

ALTER TABLE documento
    ADD CONSTRAINT ck_documento_hash_longitud CHECK (char_length(hash_contenido) = 64);
