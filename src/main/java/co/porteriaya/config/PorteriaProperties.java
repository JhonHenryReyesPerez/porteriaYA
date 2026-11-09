package co.porteriaya.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tiempos, umbrales y limites de porteria (§5: sin numeros magicos).
 * Vinculacion por constructor: toda clave debe existir en application.yml.
 */
@ConfigurationProperties(prefix = "porteria")
public record PorteriaProperties(
        String baseUrl,
        Admin admin,
        Cors cors,
        Aprobacion aprobacion,
        Permanencia permanencia,
        Paquete paquete,
        N8n n8n
) {

    /** PIN que exigen los endpoints de administrador (§7). Vive en .env. */
    public record Admin(String pin) {
    }

    public record Cors(List<String> origenes) {
    }

    /** Ventana de respuesta del residente a una visita no anunciada (§6). */
    public record Aprobacion(int timeoutMinutos, int tokenVigenciaMinutos) {
    }

    /** Gracia sobre la duracion esperada antes de PERMANENCIA_EXCEDIDA (§6). */
    public record Permanencia(int graciaMinutos) {
    }

    /** Recordatorios y escalamiento de paquetes (§6). */
    public record Paquete(int primerRecordatorioHoras, int segundoRecordatorioHoras, int escalamientoDias) {
    }

    /** Destino de los eventos y secreto compartido (§10). */
    public record N8n(String webhookUrl, String secret) {
    }
}
