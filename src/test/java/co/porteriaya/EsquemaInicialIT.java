package co.porteriaya;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Red de seguridad del esquema: si alguien rompe la migracion V1 o una de las
 * decisiones que materializa (indice parcial del codigo, usos ilimitados),
 * falla aqui y no tres cortes verticales mas adelante.
 *
 * Necesita Docker: por eso es *IT y corre en ./mvnw verify, no en test.
 */
@SpringBootTest
@Testcontainers
class EsquemaInicialIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final List<String> TABLAS_ESPERADAS = List.of(
            "autorizacion", "autorizado_retiro", "bitacora", "consulta_rag", "documento",
            "ingreso", "no_permitido", "paquete", "residente", "solicitud_no_anunciada",
            "token_aprobacion");

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void laMigracionCreaLasOnceTablasDelContrato() {
        List<String> encontradas = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                 WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                """, String.class);

        assertThat(encontradas).containsAll(TABLAS_ESPERADAS);
    }

    @Test
    void flywayRegistraLaVersionUno() {
        String descripcion = jdbc.queryForObject(
                "SELECT description FROM flyway_schema_history WHERE version = '1'", String.class);

        assertThat(descripcion).isEqualTo("esquema inicial");
    }

    @Test
    void elIndiceDelCodigoEsParcialSobreLasActivas() {
        String definicion = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'ux_autorizacion_codigo_activa'",
                String.class);

        assertThat(definicion)
                .contains("UNIQUE")
                .contains("WHERE")
                .contains("ACTIVA");
    }

    @Test
    void dosAutorizacionesActivasNoPuedenCompartirCodigo() {
        nuevaAutorizacion("110001", "ACTIVA", 1);

        assertThatThrownBy(() -> nuevaAutorizacion("110001", "ACTIVA", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unaAutorizacionCerradaPuedeReusarElCodigoDeUnaActiva() {
        nuevaAutorizacion("110002", "ACTIVA", 1);

        // El indice es parcial, asi que una CANCELADA con el mismo codigo entra
        assertThat(nuevaAutorizacion("110002", "CANCELADA", 1)).isPositive();
    }

    @Test
    void elUpdateAtomicoAgotaUnCodigoDeUnSoloUso() {
        long id = nuevaAutorizacion("110003", "ACTIVA", 1);

        assertThat(consumirUso(id)).isEqualTo(1);
        // Cero filas afectadas es lo que el service traduce a 409 USOS_AGOTADOS (§6)
        assertThat(consumirUso(id)).isZero();
    }

    @Test
    void elUpdateAtomicoNoFrenaLosUsosIlimitados() {
        long id = nuevaAutorizacionIlimitada("110004");

        assertThat(consumirUso(id)).isEqualTo(1);
        assertThat(consumirUso(id)).isEqualTo(1);
        assertThat(consumirUso(id)).isEqualTo(1);

        assertThat(jdbc.queryForObject(
                "SELECT usos_actuales FROM autorizacion WHERE id = ?", Integer.class, id))
                .isEqualTo(3);
    }

    @Test
    void unIngresoNoAnunciadoNoPuedeApuntarAUnaAutorizacion() {
        long residente = nuevoResidente();
        long autorizacion = nuevaAutorizacion("110005", "ACTIVA", 1);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO ingreso (origen, autorizacion_id, residente_id, visitante_nombre,
                                     estado, vigilante, duracion_esperada_min, ingreso_en)
                VALUES ('NO_ANUNCIADA', ?, ?, 'Visitante', 'DENTRO', 'vigilante1', 120, now())
                """, autorizacion, residente))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** El UPDATE atomico exacto del contrato (§6). */
    private int consumirUso(long autorizacionId) {
        return jdbc.update("""
                UPDATE autorizacion SET usos_actuales = usos_actuales + 1
                 WHERE id = ? AND (usos_max IS NULL OR usos_actuales < usos_max)
                """, autorizacionId);
    }

    private long nuevoResidente() {
        int n = SECUENCIA.incrementAndGet();
        return jdbc.queryForObject("""
                INSERT INTO residente (torre, apartamento, nombre, activo, creado_en)
                VALUES ('T', ?, ?, TRUE, now()) RETURNING id
                """, Long.class, String.valueOf(n), "Residente de prueba " + n);
    }

    private long nuevaAutorizacion(String codigo, String estado, int usosMax) {
        return jdbc.queryForObject("""
                INSERT INTO autorizacion (residente_id, tipo, codigo, visitante_nombre, estado,
                                          valida_desde, valida_hasta, usos_max, usos_actuales,
                                          duracion_esperada_min, creado_en)
                VALUES (?, 'VISITA', ?, 'Visitante de prueba', ?,
                        now() - interval '1 hour', now() + interval '1 hour', ?, 0, 120, now())
                RETURNING id
                """, Long.class, nuevoResidente(), codigo, estado, usosMax);
    }

    /** usos_max NULL = ilimitado (servicio recurrente, mudanza). */
    private long nuevaAutorizacionIlimitada(String codigo) {
        return jdbc.queryForObject("""
                INSERT INTO autorizacion (residente_id, tipo, codigo, visitante_nombre, estado,
                                          valida_desde, valida_hasta, dias_semana, hora_desde,
                                          hora_hasta, usos_max, usos_actuales,
                                          duracion_esperada_min, creado_en)
                VALUES (?, 'SERVICIO_RECURRENTE', ?, 'Servicio de prueba', 'ACTIVA',
                        now() - interval '1 day', now() + interval '30 days', '1,3,5',
                        time '07:00', time '12:00', NULL, 0, 300, now())
                RETURNING id
                """, Long.class, nuevoResidente(), codigo);
    }
}
