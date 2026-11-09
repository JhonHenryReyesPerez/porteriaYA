package co.porteriaya.demo;

import javax.sql.DataSource;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/**
 * Siembra los datos de demostracion (§12). Idempotente: si ya hay residentes
 * no hace nada, asi que relanzar la app con el perfil demo no duplica filas.
 *
 * Deliberadamente no es data.sql: mezclarlo con Flyway y ddl-auto=validate
 * rompe el arranque por orden de ejecucion.
 */
@Component
@Profile("demo")
@RequiredArgsConstructor
public class SembradorDemo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SembradorDemo.class);
    private static final String GUION = "db/demo/seed-demo.sql";

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        Integer residentes = jdbc.queryForObject("SELECT count(*) FROM residente", Integer.class);
        if (residentes != null && residentes > 0) {
            log.info("Semilla de demo omitida: la base ya tiene {} residentes", residentes);
            return;
        }

        ResourceDatabasePopulator populador = new ResourceDatabasePopulator(new ClassPathResource(GUION));
        populador.setSqlScriptEncoding("UTF-8");
        populador.execute(dataSource);

        log.info("Semilla de demo cargada: {} residentes, {} autorizaciones, {} paquetes",
                jdbc.queryForObject("SELECT count(*) FROM residente", Integer.class),
                jdbc.queryForObject("SELECT count(*) FROM autorizacion", Integer.class),
                jdbc.queryForObject("SELECT count(*) FROM paquete", Integer.class));
    }
}
