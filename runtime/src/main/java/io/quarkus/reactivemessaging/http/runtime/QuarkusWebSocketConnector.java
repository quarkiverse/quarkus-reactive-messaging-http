package io.quarkus.reactivemessaging.http.runtime;

import static io.quarkus.reactivemessaging.http.runtime.QuarkusWebSocketConnector.DEFAULT_JITTER;
import static io.quarkus.reactivemessaging.http.runtime.QuarkusWebSocketConnector.DEFAULT_MAX_INFLIGHT_MESSAGES;
import static io.quarkus.reactivemessaging.http.runtime.QuarkusWebSocketConnector.DEFAULT_WAIT_FOR_COMPLETION;
import static io.smallrye.reactive.messaging.annotations.ConnectorAttribute.Direction.INCOMING;
import static io.smallrye.reactive.messaging.annotations.ConnectorAttribute.Direction.OUTGOING;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Flow;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;

import io.quarkus.reactivemessaging.http.runtime.config.TlsConfig;
import io.quarkus.reactivemessaging.http.runtime.serializers.SerializerFactoryBase;
import io.quarkus.runtime.configuration.DurationConverter;
import io.quarkus.tls.TlsConfiguration;
import io.quarkus.tls.TlsConfigurationRegistry;
import io.smallrye.reactive.messaging.annotations.ConnectorAttribute;
import io.smallrye.reactive.messaging.connector.InboundConnector;
import io.smallrye.reactive.messaging.connector.OutboundConnector;
import io.vertx.core.Vertx;

/**
 * Quarkus-specific reactive messaging connector for web sockets
 */
@Connector(QuarkusWebSocketConnector.NAME)

@ConnectorAttribute(name = "url", type = "string", direction = OUTGOING, description = "The target URL", mandatory = true)
@ConnectorAttribute(name = "serializer", type = "string", direction = OUTGOING, description = "Message serializer")
@ConnectorAttribute(name = "maxRetries", type = "int", direction = OUTGOING, description = "The number of retries to make for sending a message to a remote websocket endpoint. A value greater than 0 is advised. Otherwise, a web socket timeout can result in a dropped message", defaultValue = QuarkusWebSocketConnector.DEFAULT_MAX_ATTEMPTS_STR)
@ConnectorAttribute(name = "jitter", type = "double", direction = OUTGOING, description = "Configures the random factor when using back-off with maxAttempts > 1", defaultValue = DEFAULT_JITTER)
@ConnectorAttribute(name = "delay", type = "string", direction = OUTGOING, description = "Configures a back-off delay between attempts to send a request. A random factor (jitter) is applied to increase the delay when several failures happen.")
@ConnectorAttribute(name = "tlsConfigurationName", type = "string", direction = OUTGOING, description = "Name of the TLS configuration to be used from TLS registry.")
@ConnectorAttribute(name = "maxInflightMessages", type = "int", direction = OUTGOING, description = "The maximum size of a queue holding pending messages, i.e. messages waiting to receive an acknowledgment.", defaultValue = DEFAULT_MAX_INFLIGHT_MESSAGES)
@ConnectorAttribute(name = "waitForCompletion", type = "boolean", direction = OUTGOING, description = "Whether the client waits for the request completion before acknowledging the message", defaultValue = DEFAULT_WAIT_FOR_COMPLETION)

@ConnectorAttribute(name = "path", type = "string", direction = INCOMING, description = "The path of the endpoint", mandatory = true)
@ConnectorAttribute(name = "buffer-size", type = "string", direction = INCOMING, description = "Web socket endpoint buffers messages if a consumer is not able to keep up. This setting specifies the size of the buffer.", defaultValue = QuarkusHttpConnector.DEFAULT_SOURCE_BUFFER_STR)
@ApplicationScoped
public class QuarkusWebSocketConnector implements InboundConnector, OutboundConnector {
    public static final String NAME = "quarkus-websocket";

    static final String DEFAULT_JITTER = "0.5";
    static final String DEFAULT_MAX_ATTEMPTS_STR = "1";
    static final String DEFAULT_MAX_INFLIGHT_MESSAGES = "1";
    static final String DEFAULT_WAIT_FOR_COMPLETION = "true";

    static final String DEFAULT_SOURCE_BUFFER_STR = "8";

    public static final Integer DEFAULT_SOURCE_BUFFER = Integer.valueOf(DEFAULT_SOURCE_BUFFER_STR);

    @Inject
    ReactiveWebSocketHandlerBean handlerBean;

    @Inject
    SerializerFactoryBase serializerFactory;

    @Inject
    Vertx vertx;

    @Inject
    Instance<TlsConfigurationRegistry> tlsRegistry;

    @Override
    public Flow.Publisher<? extends Message<?>> getPublisher(Config configuration) {
        QuarkusWebSocketConnectorIncomingConfiguration config = new QuarkusWebSocketConnectorIncomingConfiguration(
                configuration);
        String path = config.getPath();

        return handlerBean.getProcessor(path);
    }

    @Override
    public Flow.Subscriber<? extends Message<?>> getSubscriber(Config configuration) {
        QuarkusWebSocketConnectorOutgoingConfiguration config = new QuarkusWebSocketConnectorOutgoingConfiguration(
                configuration);
        String serializer = config.getSerializer().orElse(null);
        Optional<Duration> delay = config.getDelay().map(DurationConverter::parseDuration);
        Double jitter = config.getJitter();
        Integer maxRetries = config.getMaxRetries();
        URI url = URI.create(config.getUrl());
        long inflights = config.getMaxInflightMessages();
        boolean waitForCompletion = config.getWaitForCompletion();

        Optional<TlsConfiguration> tlsConfiguration = TlsConfig.lookupConfig(config.getTlsConfigurationName(),
                tlsRegistry.isResolvable() ? Optional.of(tlsRegistry.get()) : Optional.empty());

        return new WebSocketSink(vertx, url, serializer, serializerFactory, maxRetries, delay, jitter, tlsConfiguration,
                inflights, waitForCompletion).sink();
    }
}
