-- Datos de demo (§12). Lo ejecuta SembradorDemo solo en perfil `demo` y solo
-- si la tabla residente esta vacia, asi que relanzar la app no duplica nada.
--
-- NO es data.sql a proposito: mezclarlo con Flyway y ddl-auto=validate rompe
-- el arranque por orden de ejecucion.
--
-- Las fechas son relativas a now() de la base. El reloj simulado del perfil
-- demo (§9) parte de ahi y lo adelanta POST /api/demo/avanzar.
-- Las banderas de aviso quedan en FALSE a proposito: asi la primera pasada
-- del scheduler tiene trabajo real que hacer durante la demostracion.

-- 30 apartamentos: torres A, B y C con aptos 101 a 110
INSERT INTO residente (torre, apartamento, nombre, correo, telefono, activo, creado_en)
SELECT t.torre,
       a.apto::text,
       'Residente ' || t.torre || '-' || a.apto,
       'residente.' || lower(t.torre) || a.apto || '@example.test',
       '300' || lpad(a.apto::text, 7, '0'),
       TRUE,
       now()
FROM (VALUES ('A'), ('B'), ('C')) AS t(torre),
     generate_series(101, 110) AS a(apto);

-- Un apartamento inactivo, para probar que no se le registran paquetes (§6)
UPDATE residente SET activo = FALSE WHERE torre = 'C' AND apartamento = '110';

-- --------------------------------------------------------- autorizaciones
-- Visita de un solo uso, activa hoy. Es la del guion de demo (§19).
INSERT INTO autorizacion (residente_id, tipo, codigo, visitante_nombre, visitante_doc4,
                          estado, valida_desde, valida_hasta, usos_max, usos_actuales,
                          duracion_esperada_min, creado_en)
SELECT id, 'VISITA', '100001', 'Camilo Rojas', '4821', 'ACTIVA',
       now() - interval '1 hour', now() + interval '8 hours', 1, 0, 120, now()
FROM residente WHERE torre = 'A' AND apartamento = '101';

-- Visita de varios usos, con uno ya consumido
INSERT INTO autorizacion (residente_id, tipo, codigo, visitante_nombre, visitante_doc4,
                          estado, valida_desde, valida_hasta, usos_max, usos_actuales,
                          duracion_esperada_min, creado_en)
SELECT id, 'VISITA', '100002', 'Laura Mendez', '7310', 'ACTIVA',
       now() - interval '2 days', now() + interval '5 days', 3, 1, 180, now()
FROM residente WHERE torre = 'A' AND apartamento = '102';

-- Domiciliario: ventana corta, un solo uso
INSERT INTO autorizacion (residente_id, tipo, codigo, visitante_nombre, visitante_doc4,
                          estado, valida_desde, valida_hasta, usos_max, usos_actuales,
                          duracion_esperada_min, creado_en)
SELECT id, 'DOMICILIARIO', '100003', 'Mensajeria Rapida', '0094', 'ACTIVA',
       now(), now() + interval '3 hours', 1, 0, 30, now()
FROM residente WHERE torre = 'B' AND apartamento = '103';

-- Servicio recurrente: usos ILIMITADOS (usos_max NULL), lunes/miercoles/viernes 07:00-12:00
INSERT INTO autorizacion (residente_id, tipo, codigo, visitante_nombre, visitante_doc4,
                          estado, valida_desde, valida_hasta, dias_semana, hora_desde,
                          hora_hasta, usos_max, usos_actuales, duracion_esperada_min, creado_en)
SELECT id, 'SERVICIO_RECURRENTE', '100004', 'Marta Quintero', '5567', 'ACTIVA',
       now() - interval '10 days', now() + interval '6 months', '1,3,5',
       time '07:00', time '12:00', NULL, 14, 300, now()
FROM residente WHERE torre = 'B' AND apartamento = '104';

-- Mudanza: ilimitada dentro de la franja, tres dias, 08:00-17:00
INSERT INTO autorizacion (residente_id, tipo, codigo, visitante_nombre, visitante_doc4,
                          estado, valida_desde, valida_hasta, dias_semana, hora_desde,
                          hora_hasta, usos_max, usos_actuales, duracion_esperada_min, creado_en)
SELECT id, 'MUDANZA', '100005', 'Trasteos El Dorado', '1122', 'ACTIVA',
       now() - interval '1 day', now() + interval '2 days', '1,2,3,4,5,6,7',
       time '08:00', time '17:00', NULL, 3, 480, now()
FROM residente WHERE torre = 'C' AND apartamento = '105';

-- Tres autorizaciones ya cerradas, una por cada estado final.
-- El indice unico es parcial (solo ACTIVA), asi que estos codigos podrian reusarse.
INSERT INTO autorizacion (residente_id, tipo, codigo, visitante_nombre, visitante_doc4,
                          estado, valida_desde, valida_hasta, usos_max, usos_actuales,
                          duracion_esperada_min, creado_en)
SELECT id, 'VISITA', '100006', 'Andres Pulido', '6604', 'VENCIDA',
       now() - interval '10 days', now() - interval '9 days', 1, 0, 120, now() - interval '10 days'
FROM residente WHERE torre = 'A' AND apartamento = '106';

INSERT INTO autorizacion (residente_id, tipo, codigo, visitante_nombre, visitante_doc4,
                          estado, valida_desde, valida_hasta, usos_max, usos_actuales,
                          duracion_esperada_min, creado_en)
SELECT id, 'VISITA', '100007', 'Sofia Herrera', '3390', 'CANCELADA',
       now() - interval '1 day', now() + interval '3 days', 2, 0, 120, now() - interval '1 day'
FROM residente WHERE torre = 'A' AND apartamento = '107';

INSERT INTO autorizacion (residente_id, tipo, codigo, visitante_nombre, visitante_doc4,
                          estado, valida_desde, valida_hasta, usos_max, usos_actuales,
                          duracion_esperada_min, creado_en)
SELECT id, 'VISITA', '100008', 'Diego Salcedo', '7745', 'AGOTADA',
       now() - interval '3 days', now() + interval '1 day', 2, 2, 120, now() - interval '3 days'
FROM residente WHERE torre = 'B' AND apartamento = '108';

-- --------------------------------------------------------------- paquetes
-- Distintas antigüedades para que recordatorios y escalamiento tengan material
INSERT INTO paquete (residente_id, descripcion, remitente, codigo_retiro, estado,
                     vigilante, recibido_en)
SELECT id, 'Caja mediana', 'Tienda en linea', '200001', 'RECIBIDO',
       'vigilante1', now() - interval '2 hours'
FROM residente WHERE torre = 'A' AND apartamento = '101';

INSERT INTO paquete (residente_id, descripcion, remitente, codigo_retiro, estado,
                     vigilante, recibido_en)
SELECT id, 'Sobre de documentos', 'Notaria 12', '200002', 'RECIBIDO',
       'vigilante1', now() - interval '30 hours'
FROM residente WHERE torre = 'A' AND apartamento = '102';

INSERT INTO paquete (residente_id, descripcion, remitente, codigo_retiro, estado,
                     vigilante, recibido_en)
SELECT id, 'Bolsa de farmacia', 'Droguerias Unidas', '200003', 'RECIBIDO',
       'vigilante2', now() - interval '4 days'
FROM residente WHERE torre = 'B' AND apartamento = '103';

-- Mas de 7 dias: candidato a PAQUETE_ESCALADO en la primera pasada
INSERT INTO paquete (residente_id, descripcion, remitente, codigo_retiro, estado,
                     vigilante, recibido_en)
SELECT id, 'Caja grande sin reclamar', 'Importaciones del Norte', '200004', 'RECIBIDO',
       'vigilante2', now() - interval '8 days'
FROM residente WHERE torre = 'B' AND apartamento = '104';

INSERT INTO paquete (residente_id, descripcion, remitente, codigo_retiro, estado,
                     vigilante, recibido_en, entregado_en, entregado_a,
                     aviso_24h, aviso_72h)
SELECT id, 'Paquete ya entregado', 'Tienda en linea', '200005', 'ENTREGADO',
       'vigilante1', now() - interval '5 days', now() - interval '4 days',
       'Residente C-105', TRUE, FALSE
FROM residente WHERE torre = 'C' AND apartamento = '105';

-- ------------------------------------------------- autorizados a retirar
INSERT INTO autorizado_retiro (residente_id, nombre, doc4, activo, creado_en)
SELECT id, 'Gloria Ramirez', '8812', TRUE, now()
FROM residente WHERE torre = 'A' AND apartamento = '101';

INSERT INTO autorizado_retiro (residente_id, nombre, doc4, activo, creado_en)
SELECT id, 'Hernan Cifuentes', '2245', TRUE, now()
FROM residente WHERE torre = 'B' AND apartamento = '103';

-- ------------------------------------------------------ persona no permitida
-- La del guion de demo: intentar su ingreso debe disparar PERSONA_NO_PERMITIDA
INSERT INTO no_permitido (nombre, doc4, motivo, activo, registrado_por, creado_en)
VALUES ('Ivan Castano', '9931', 'Agresion al personal de vigilancia', TRUE, 'admin1', now());

-- --------------------------------------------------------------- bitacora
INSERT INTO bitacora (fecha, actor, rol, entidad, entidad_id, accion, detalle)
VALUES (now(), 'sistema', 'ADMINISTRADOR', 'DEMO', NULL, 'SEMILLA_CARGADA',
        'Datos de demostracion sembrados');
