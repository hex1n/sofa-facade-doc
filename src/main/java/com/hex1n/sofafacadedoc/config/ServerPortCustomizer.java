package com.hex1n.sofafacadedoc.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

@Component
public class ServerPortCustomizer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
    private final AppConfigLoader loader;

    public ServerPortCustomizer(AppConfigLoader loader) {
        this.loader = loader;
    }

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        String listen = loader.current().server.listen;
        int idx = listen == null ? -1 : listen.lastIndexOf(':');
        if (idx >= 0 && idx + 1 < listen.length()) {
            factory.setPort(Integer.parseInt(listen.substring(idx + 1)));
        }
    }
}

