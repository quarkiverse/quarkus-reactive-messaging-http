package io.quarkus.reactivemessaging.http.runtime.config;

import java.util.Optional;

import io.quarkus.tls.TlsConfiguration;
import io.quarkus.tls.TlsConfigurationRegistry;
import io.quarkus.tls.runtime.config.TlsConfigUtils;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.WebSocketClientOptions;

public class TlsConfig {

    public static Optional<TlsConfiguration> lookupConfig(Optional<String> tlsConfigurationName,
            Optional<TlsConfigurationRegistry> tlsRegistry) {
        return tlsConfigurationName.map(name -> tlsRegistry.flatMap(t -> t.get(name)))
                .orElseGet(() -> tlsRegistry.flatMap(TlsConfigurationRegistry::getDefault));
    }

    public static void configure(HttpClientOptions options, TlsConfiguration tlsConfiguration) {
        TlsConfigUtils.configure(options, tlsConfiguration);
    }

    public static void configure(WebSocketClientOptions options, TlsConfiguration tlsConfiguration) {
        TlsConfigUtils.configure(options, tlsConfiguration);
    }
}
