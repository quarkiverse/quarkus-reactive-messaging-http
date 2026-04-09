package io.quarkus.reactivemessaging.http.runtime;

import java.util.Collection;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkus.reactivemessaging.http.runtime.config.HttpStreamConfig;
import io.quarkus.reactivemessaging.http.runtime.config.ReactiveHttpConfig;
import io.quarkus.reactivemessaging.http.runtime.serializers.DeserializerFactoryBase;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

/**
 * a bean that handles incoming http requests
 */
@Singleton
public class ReactiveHttpHandlerBean extends ReactiveHandlerBeanBase<HttpStreamConfig, HttpMessage<?>> {

    private static final Logger log = Logger.getLogger(ReactiveHttpHandlerBean.class);

    @Inject
    ReactiveHttpConfig config;

    @Inject
    DeserializerFactoryBase deserializerFactory;

    Multi<HttpMessage<?>> getProcessor(String path, HttpMethod method) {
        return processors.get(key(path, method)).getProcessor();
    }

    @Override
    protected Collection<HttpStreamConfig> configs() {
        return config.getHttpConfigs();
    }

    @Override
    protected String key(HttpStreamConfig streamConfig) {
        return key(streamConfig.path, streamConfig.method);
    }

    @Override
    protected String key(RoutingContext context) {
        return key(context.currentRoute().getPath(), context.request().method());
    }

    @Override
    protected String description(HttpStreamConfig streamConfig) {
        return String.format("path: %s, method %s", streamConfig.path, streamConfig.method);
    }

    @Override
    protected void handleRequest(RoutingContext event, MultiEmitter<? super HttpMessage<?>> emitter,
            StrictQueueSizeGuard guard, String path, String deserializerName) {
        if (emitter == null) {
            onUnexpectedError(event, null,
                    "No consumer subscribed for messages sent to Reactive Messaging HTTP endpoint on path: " + path);
        } else if (guard.prepareToEmit()) {
            try {
                guard.putInQueue(() -> statusEvent(event));
                emitter.emit(new HttpMessage<>(
                        deserializerFactory.getDeserializer(deserializerName)
                                .map(d -> d.deserialize(event.body().buffer()))
                                .orElse(event.body().buffer()),
                        new IncomingHttpMetadata(event),
                        () -> {
                            ackEvent(event);
                        },
                        error -> onUnexpectedError(event, error, "Failed to process message")));
            } catch (Exception any) {
                guard.dequeue();
                onUnexpectedError(event, any, "Emitting message failed");
            }
        } else {
            nackEvent(event);
        }
    }

    private void onUnexpectedError(RoutingContext event, Throwable error, String message) {
        nackEvent(event);
        log.error(message + (error != null ? ": " + error.getMessage() : ""));
        log.debug(message, error);
    }

    protected void ackEvent(RoutingContext event) {
        synchronized (event.response()) {
            if (!event.response().ended()) {
                if (event.response().getStatusCode() == 200) {
                    event.response().setStatusCode(202);
                }
                event.response().end("ACK");
            }
        }
    }

    protected void nackEvent(RoutingContext event) {
        synchronized (event.response()) {
            if (!event.response().ended()) {
                if (event.response().getStatusCode() == 200) {
                    event.response().setStatusCode(503);
                }
                event.response().end("NACK");
            }
        }
    }

    protected void statusEvent(RoutingContext event) {
        synchronized (event.response()) {
            if (!event.response().ended()) {
                if (event.response().getStatusCode() == 200) {
                    event.response().setStatusCode(202);
                }
                if (!event.response().isChunked()) {
                    event.response().setChunked(true);
                }
                event.response().write("");
            }
        }
    }

    private String key(String path, HttpMethod method) {
        return String.format("%s:%s", path, method);
    }
}
