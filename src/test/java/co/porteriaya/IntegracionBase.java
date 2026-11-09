package co.porteriaya;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base de las pruebas de integracion. El contenedor es estatico y se hereda,
 * asi que todas las clases *IT de la misma ejecucion comparten un unico
 * PostgreSQL en vez de levantar uno por clase.
 *
 * Ojo: aqui NO va @Transactional. Varias pruebas dependen de ver lo que otras
 * transacciones confirmaron, y el test de concurrencia del uso atomico daria
 * un falso verde si la transaccion del test envolviera a los hilos (§15).
 */
@SpringBootTest(properties = {
        "porteria.admin.pin=" + IntegracionBase.PIN_ADMIN,
        // Puerto muerto a proposito: las pruebas no deben depender de si hay un
        // n8n levantado en la maquina, y asi todas corren bajo el escenario
        // "n8n caido" que §10 exige tolerar.
        "porteria.n8n.webhook-url=http://127.0.0.1:1/webhook/pruebas",
        "porteria.n8n.timeout-conexion-segundos=1",
        "porteria.n8n.timeout-lectura-segundos=1"
})
// En la base y no en cada clase: asi todos los *IT comparten un solo contexto
// de Spring y cualquiera puede inyectar el colector cuando lo necesite.
@Import(ColectorDeEventos.class)
public abstract class IntegracionBase {

    /** El .env no existe en las pruebas, asi que el PIN se fija aqui para todas. */
    public static final String PIN_ADMIN = "pin-de-prueba";

    /**
     * Contenedor unico para toda la ejecucion (patron singleton de
     * Testcontainers): se arranca a mano en el bloque static y NO lleva
     * @Container.
     *
     * Con @Container mas @Testcontainers la extension para el contenedor al
     * terminar cada clase, y la siguiente clase *IT se encontraba el puerto
     * cerrado ("Connection refused"). Arrancandolo aqui vive mientras viva la
     * JVM, y Ryuk lo retira al final.
     */
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }
}
