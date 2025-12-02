package io.quarkus.reactivemessaging.http.runtime;

import static io.smallrye.reactive.messaging.annotations.ConnectorAttribute.Direction.INCOMING;
import static io.smallrye.reactive.messaging.annotations.ConnectorAttribute.Direction.INCOMING_AND_OUTGOING;
import static io.smallrye.reactive.messaging.annotations.ConnectorAttribute.Direction.OUTGOING;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Flow;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.jboss.logging.Logger;

import io.quarkus.reactivemessaging.http.runtime.config.TlsConfig;
import io.quarkus.reactivemessaging.http.runtime.serializers.SerializerFactoryBase;
import io.quarkus.runtime.configuration.DurationConverter;
import io.quarkus.tls.TlsConfiguration;
import io.quarkus.tls.TlsConfigurationRegistry;
import io.smallrye.mutiny.Multi;
import io.smallrye.reactive.messaging.annotations.ConnectorAttribute;
import io.smallrye.reactive.messaging.connector.InboundConnector;
import io.smallrye.reactive.messaging.connector.OutboundConnector;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;

/**
 * Quarkus-specific reactive messaging connector for HTTP
 */
@ConnectorAttribute(name = "url", type = "string", direction = OUTGOING, description = "The target URL", mandatory = true)
@ConnectorAttribute(name = "serializer", type = "string", direction = OUTGOING, description = "Message serializer")
@ConnectorAttribute(name = "maxPoolSize", type = "int", direction = OUTGOING, description = "Maximum pool size for connections")
@ConnectorAttribute(name = "maxWaitQueueSize", type = "int", direction = OUTGOING, description = "Maximum requests allowed in the wait queue of the underlying client.  If the value is set to a negative number then the queue will be unbounded")
@ConnectorAttribute(name = "maxRetries", type = "int", direction = OUTGOING, description = "The number of attempts to make for sending a request to a remote endpoint. Must not be less than zero", defaultValue = QuarkusHttpConnector.DEFAULT_MAX_ATTEMPTS_STR)
@ConnectorAttribute(name = "jitter", type = "string", direction = OUTGOING, description = "Configures the random factor when using back-off with maxRetries > 0", defaultValue = QuarkusHttpConnector.DEFAULT_JITTER)
@ConnectorAttribute(name = "delay", type = "string", direction = OUTGOING, description = "Configures a back-off delay between attempts to send a request. A random factor (jitter) is applied to increase the delay when several failures happen.")
@ConnectorAttribute(name = "tlsConfigurationName", type = "string", direction = OUTGOING, description = "Name of the TLS configuration to be used from TLS registry.")
@ConnectorAttribute(name = "maxInflightMessages", type = "int", direction = OUTGOING, description = "The maximum size of a queue holding pending messages, i.e. messages waiting to receive an acknowledgment.", defaultValue = QuarkusHttpConnector.DEFAULT_MAX_INFLIGHT_MESSAGES)
@ConnectorAttribute(name = "waitForCompletion", type = "boolean", direction = OUTGOING, description = "Whether the client waits for the request completion before acknowledging the message", defaultValue = QuarkusHttpConnector.DEFAULT_WAIT_FOR_COMPLETION)
@ConnectorAttribute(name = "protocolVersion", type = "string", direction = OUTGOING, description = "HTTP protocol version.", defaultValue = "HTTP_1_1")

@ConnectorAttribute(name = "method", type = "string", direction = INCOMING_AND_OUTGOING, description = "The HTTP method (either `POST` or `PUT`)", defaultValue = "POST")
@ConnectorAttribute(name = "path", type = "string", direction = INCOMING, description = "The path of the endpoint", mandatory = true)
@ConnectorAttribute(name = "buffer-size", type = "string", direction = INCOMING, description = "HTTP endpoint buffers messages if a consumer is not able to keep up. This setting specifies the size of the buffer.", defaultValue = QuarkusHttpConnector.DEFAULT_SOURCE_BUFFER_STR)
@ConnectorAttribute(name = "broadcast", type = "boolean", direction = INCOMING, description = "Whether the messages should be dispatched to multiple consumers", defaultValue = "false")

@Connector(QuarkusHttpConnector.NAME)
@ApplicationScoped
public class QuarkusHttpConnector implements InboundConnector, OutboundConnector {
    private static final Logger log = Logger.getLogger(QuarkusHttpConnector.class);

    static final String DEFAULT_JITTER = "0.5";
    static final String DEFAULT_MAX_ATTEMPTS_STR = "0";
    static final String DEFAULT_MAX_INFLIGHT_MESSAGES = "1";
    static final String DEFAULT_WAIT_FOR_COMPLETION = "true";

    static final String DEFAULT_SOURCE_BUFFER_STR = "8";

    public static final Integer DEFAULT_SOURCE_BUFFER = Integer.valueOf(DEFAULT_SOURCE_BUFFER_STR);

    public static final String NAME = "quarkus-http";

    @Inject
    ReactiveHttpHandlerBean handlerBean;

    @Inject
    Vertx vertx;

    @Inject
    SerializerFactoryBase serializerFactory;

    @Inject
    Instance<TlsConfigurationRegistry> tlsRegistry;

    @Override
    public Flow.Publisher<? extends Message<?>> getPublisher(Config configuration) {
        QuarkusHttpConnectorIncomingConfiguration config = new QuarkusHttpConnectorIncomingConfiguration(configuration);
        String methodAsString = config.getMethod();
        HttpMethod method = getMethod(methodAsString);

        Multi<HttpMessage<?>> processor = handlerBean.getProcessor(config.getPath(), method);
        boolean broadcast = config.getBroadcast();
        if (broadcast) {
            return processor.broadcast().toAllSubscribers();
        } else {
            return processor;
        }
    }

    private HttpMethod getMethod(String methodAsString) {
        try {
            return HttpMethod.valueOf(methodAsString);
        } catch (IllegalArgumentException e) {
            String error = "Unsupported HTTP method: " + methodAsString + ". The supported methods are: "
                    + HttpMethod.values();
            log.warn(error, e);
            throw new IllegalArgumentException(error);
        }
    }

    private HttpVersion getProtocolVersion(String versionAsString) {
        try {
            return HttpVersion.valueOf(versionAsString);
        } catch (IllegalArgumentException e) {
            String error = "Unsupported HTTP protocol version: " + versionAsString + ". The supported versions are: "
                    + String.join(", ", Arrays.stream(HttpVersion.values()).map(HttpVersion::name).toList());
            log.warn(error, e);
            throw new IllegalArgumentException(error);
        }
    }

    @Override
    public Flow.Subscriber<? extends Message<?>> getSubscriber(Config configuration) {
        QuarkusHttpConnectorOutgoingConfiguration config = new QuarkusHttpConnectorOutgoingConfiguration(configuration);
        String url = config.getUrl();
        String method = getMethod(config.getMethod()).name();
        String serializer = config.getSerializer().orElse(null);
        Optional<String> maybeDelay = config.getDelay();

        Optional<Duration> delay = maybeDelay.map(DurationConverter::parseDuration);

        String jitterAsString = config.getJitter();
        Integer maxRetries = config.getMaxRetries();

        Optional<Integer> maxPoolSize = config.getMaxPoolSize();
        Optional<Integer> maxWaitQueueSize = config.getMaxWaitQueueSize();
        long inflights = config.getMaxInflightMessages();
        boolean waitForCompletion = config.getWaitForCompletion();
        HttpVersion protocolVersion = getProtocolVersion(config.getProtocolVersion());

        double jitter;
        try {
            jitter = Double.valueOf(jitterAsString);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format("Failed to parse jitter value '%s' to a double.", jitterAsString));
        }

        Optional<TlsConfiguration> tlsConfiguration = TlsConfig.lookupConfig(config.getTlsConfigurationName(),
                tlsRegistry.isResolvable() ? Optional.of(tlsRegistry.get()) : Optional.empty());
        return new HttpSink(vertx, method, url, serializer, maxRetries, jitter, delay, maxPoolSize, maxWaitQueueSize,
                serializerFactory, tlsConfiguration, inflights, waitForCompletion, protocolVersion).sink();
    }

}
