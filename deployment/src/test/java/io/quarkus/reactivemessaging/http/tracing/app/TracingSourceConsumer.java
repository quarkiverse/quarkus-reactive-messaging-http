package io.quarkus.reactivemessaging.http.tracing.app;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import io.opentelemetry.api.trace.Span;
import io.smallrye.reactive.messaging.TracingMetadata;

@ApplicationScoped
public class TracingSourceConsumer {

    @Inject
    ManagedExecutor executor;

    private final List<String> tracingEnabledTraceIds = new ArrayList<>();
    private final List<Boolean> tracingEnabledMetadataPresent = new ArrayList<>();
    private final List<Boolean> tracingDisabledMetadataPresent = new ArrayList<>();
    private final List<String> asyncHopTraceIds = new ArrayList<>();

    @Incoming("tracing-enabled-http-source")
    public CompletionStage<Void> consumeTracingEnabled(Message<?> message) {
        tracingEnabledTraceIds.add(Span.current().getSpanContext().getTraceId());
        tracingEnabledMetadataPresent.add(message.getMetadata(TracingMetadata.class).isPresent());
        return message.ack();
    }

    @Incoming("tracing-disabled-http-source")
    public CompletionStage<Void> consumeTracingDisabled(Message<?> message) {
        tracingDisabledMetadataPresent.add(message.getMetadata(TracingMetadata.class).isPresent());
        return message.ack();
    }

    public List<String> getTracingEnabledTraceIds() {
        return tracingEnabledTraceIds;
    }

    public List<Boolean> getTracingEnabledMetadataPresent() {
        return tracingEnabledMetadataPresent;
    }

    public List<Boolean> getTracingDisabledMetadataPresent() {
        return tracingDisabledMetadataPresent;
    }

    // mirrors a downstream consumer that hands off to a worker thread before touching the message, without
    // ever reading TracingMetadata itself
    @Incoming("async-hop-http-source")
    public CompletionStage<Void> consumeAsyncHop(Message<?> message) {
        return executor
                .runAsync(() -> asyncHopTraceIds.add(Span.current().getSpanContext().getTraceId()))
                .thenCompose(v -> message.ack());
    }

    public List<String> getAsyncHopTraceIds() {
        return asyncHopTraceIds;
    }

    public void clear() {
        tracingEnabledTraceIds.clear();
        tracingEnabledMetadataPresent.clear();
        tracingDisabledMetadataPresent.clear();
        asyncHopTraceIds.clear();
    }
}