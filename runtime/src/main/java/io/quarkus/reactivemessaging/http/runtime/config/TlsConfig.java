package io.quarkus.reactivemessaging.http.runtime.config;

import java.util.Optional;

import io.quarkus.tls.TlsConfiguration;
import io.quarkus.tls.TlsConfigurationRegistry;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.net.SSLOptions;

public class TlsConfig {

    public static Optional<TlsConfiguration> lookupConfig(Optional<String> tlsConfigurationName,
            Optional<TlsConfigurationRegistry> tlsRegistry) {
        return tlsConfigurationName.map(name -> tlsRegistry.flatMap(t -> t.get(name)))
                .orElseGet(() -> tlsRegistry.flatMap(TlsConfigurationRegistry::getDefault));
    }

    public static void configure(HttpClientOptions options, TlsConfiguration tlsConfiguration) {
        options.setSsl(true);

        if (tlsConfiguration.getTrustStoreOptions() != null) {
            options.setTrustOptions(tlsConfiguration.getTrustStoreOptions());
        }

        // For mTLS:
        if (tlsConfiguration.getKeyStoreOptions() != null) {
            options.setKeyCertOptions(tlsConfiguration.getKeyStoreOptions());
        }

        if (tlsConfiguration.isTrustAll()) {
            options.setTrustAll(true);
        }

        SSLOptions sslOptions = tlsConfiguration.getSSLOptions();
        if (sslOptions != null) {
            options.setSslHandshakeTimeout(sslOptions.getSslHandshakeTimeout());
            options.setSslHandshakeTimeoutUnit(sslOptions.getSslHandshakeTimeoutUnit());
            for (String suite : sslOptions.getEnabledCipherSuites()) {
                options.addEnabledCipherSuite(suite);
            }
            for (io.vertx.core.buffer.Buffer buffer : sslOptions.getCrlValues()) {
                options.addCrlValue(buffer);
            }
            options.setEnabledSecureTransportProtocols(sslOptions.getEnabledSecureTransportProtocols());

        }

    }
}
