package com.hex1n.sofafacadedoc;

import com.hex1n.sofafacadedoc.config.AppConfigLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class SofaFacadeDocApplication {
    public static void main(String[] args) {
        SpringApplication.run(SofaFacadeDocApplication.class, args);
    }

    @Bean
    public AppConfigLoader appConfigLoader() {
        String path = System.getProperty("sofa.doc.config");
        if (path == null || path.trim().isEmpty()) {
            path = System.getenv("SOFA_DOC_CONFIG");
        }
        if (path == null || path.trim().isEmpty()) {
            path = "config.local.yml";
        }
        return new AppConfigLoader(path);
    }
}
