package io.quarkus.reactivemessaging.http.runtime;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

import io.smallrye.reactive.messaging.TracingMetadata;
import io.smallrye.reactive.messaging.providers.locals.LocalContextMetadata;

/**
 * used by http source
 *
 * @param <T> payload type
 */
class HttpMessage<T> implements Message<T> {

    private final T payload;
    private final Runnable successHandler;
    private final Consumer<Throwable> failureHandler;
    private final Metadata metadata;

    HttpMessage(T payload, IncomingHttpMetadata requestMetadata, TracingMetadata tracingMetadata,
            LocalContextMetadata localContextMetadata, Runnable successHandler, Consumer<Throwable> failureHandler) {
        this.payload = payload;
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
        Metadata baseMetadata = HttpCloudEventHelper.getBinaryCloudEvent(requestMetadata)
                .map(m -> Metadata.of(requestMetadata, m))
                .orElse(Metadata.of(requestMetadata));
        if (tracingMetadata != null) {
            baseMetadata = baseMetadata.with(tracingMetadata);
        }
        if (localContextMetadata != null) {
            baseMetadata = baseMetadata.with(localContextMetadata);
        }
        this.metadata = baseMetadata;
    }

    @Override
    public T getPayload() {
        return payload;
    }

    @Override
    public Metadata getMetadata() {
        return metadata;
    }

    @Override
    public Supplier<CompletionStage<Void>> getAck() {
        return () -> {
            successHandler.run();
            return CompletableFuture.completedFuture(null);
        };
    }

    @Override
    public Function<Throwable, CompletionStage<Void>> getNack() {
        return error -> {
            failureHandler.accept(error);
            return CompletableFuture.completedFuture(null);
        };
    }
}
