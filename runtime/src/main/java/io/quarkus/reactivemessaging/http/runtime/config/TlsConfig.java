package io.quarkus.reactivemessaging.http.runtime.config;

import java.util.Optional;

import io.quarkus.tls.TlsConfiguration;
import io.quarkus.tls.TlsConfigurationRegistry;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.net.SSLOptions;

public class TlsConfig {

    public static TlsConfiguration lookupConfig(Optional<String> tlsConfigurationName, TlsConfigurationRegistry tlsRegistry,
            String name) {
        TlsConfiguration tlsConfiguration = null;

        // Check if we have a named TLS configuration or a default configuration:
        if (tlsConfigurationName.isPresent()) {
            Optional<TlsConfiguration> maybeConfiguration = tlsRegistry.get(tlsConfigurationName.get());
            if (!maybeConfiguration.isPresent()) {
                throw new IllegalStateException("Unable to find the TLS configuration "
                        + tlsConfigurationName.get() + " for the websocket sink " + name + ".");
            }
            tlsConfiguration = maybeConfiguration.get();
        } else if (tlsRegistry.getDefault().isPresent()) {
            tlsConfiguration = tlsRegistry.getDefault().get();
        }
        return tlsConfiguration;
    }

    public static void configure(HttpClientOptions options, TlsConfiguration tlsConfiguration) {
        if (tlsConfiguration != null) {
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
}
