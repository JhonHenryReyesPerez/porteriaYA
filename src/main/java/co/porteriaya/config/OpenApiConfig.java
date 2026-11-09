package co.porteriaya.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentacion OpenAPI (§8). Las cabeceras de rol se anaden a todo endpoint. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI porteriaYaOpenApi() {
        return new OpenAPI().info(new Info()
                .title("PorteriaYa v2")
                .version("2.0.0")
                .description("""
                        Porteria digital para conjuntos residenciales con asistente RAG.

                        Roles sin login: X-Rol y X-Actor viajan en cabeceras y se validan en
                        servidor por endpoint. Los endpoints de administrador exigen ademas
                        X-Admin-Pin. Limitacion conocida: las cabeceras son autodeclaradas,
                        asi que el rol no es un control de autenticacion (§16).

                        Codigos de error: 400 validacion, 403 rol o PIN, 404 no existe,
                        409 transicion de estado invalida o usos agotados, 413 subida
                        demasiado grande, 429 limite de frecuencia.
                        """));
    }

    @Bean
    public OperationCustomizer cabecerasDeRol() {
        return (operacion, metodo) -> operacion
                .addParametersItem(cabecera("X-Rol", "RESIDENTE | VIGILANTE | ADMINISTRADOR"))
                .addParametersItem(cabecera("X-Actor", "Quien actua: apartamento, vigilante o administrador"));
    }

    private Parameter cabecera(String nombre, String descripcion) {
        return new Parameter()
                .in("header")
                .name(nombre)
                .description(descripcion)
                .required(false)
                .schema(new StringSchema());
    }
}
