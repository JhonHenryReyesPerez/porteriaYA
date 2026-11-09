package co.porteriaya;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PorteriaYaApplication {

    public static void main(String[] args) {
        SpringApplication.run(PorteriaYaApplication.class, args);
    }
}
