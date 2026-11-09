package co.porteriaya.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS restringido por propiedad, nunca "*" (§16). */
@Configuration
@RequiredArgsConstructor
public class CorsConfig implements WebMvcConfigurer {

    private final PorteriaProperties propiedades;

    @Override
    public void addCorsMappings(CorsRegistry registro) {
        registro.addMapping("/api/**")
                .allowedOrigins(propiedades.cors().origenes().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PATCH", "DELETE")
                .allowedHeaders("Content-Type", "X-Rol", "X-Actor", "X-Admin-Pin", "X-Porteria-Secret")
                .maxAge(3600);
    }
}
