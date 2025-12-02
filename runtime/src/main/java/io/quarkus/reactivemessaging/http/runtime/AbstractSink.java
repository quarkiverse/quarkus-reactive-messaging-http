package io.quarkus.reactivemessaging.http.runtime;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Flow;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.groups.UniRetry;
import io.smallrye.reactive.messaging.providers.helpers.MultiUtils;
import io.smallrye.reactive.messaging.providers.helpers.SenderProcessor;

abstract class AbstractSink {

    private final Flow.Subscriber<? extends Message<?>> subscriber;

    public AbstractSink(Logger log, String url,
            int maxRetries, double jitter, Optional<Duration> delay,
            long inflights, boolean waitForCompletion) {
        if (inflights <= 0) {
            throw new IllegalArgumentException("Inflights must be greater than 0, but was " + inflights);
        }
        SenderProcessor processor = new SenderProcessor(inflights, waitForCompletion, m -> {
            Uni<Void> send = send(m);

            log.debugf("maxRetries: %d for %s", maxRetries, url);
            if (maxRetries > 0) {
                UniRetry<Void> retry = send.onFailure().retry();
                if (delay.isPresent()) {
                    retry = retry.withBackOff(delay.get()).withJitter(jitter);
                }
                send = retry.atMost(maxRetries);
            }

            return send
                    .onItemOrFailure().transformToUni((result, error) -> {
                        if (error != null) {
                            return Uni.createFrom().completionStage(
                                    m.nack(error).thenRun(() -> log.tracef(error, "error responding to %s", url)));
                        }
                        return Uni.createFrom()
                                .completionStage(m.ack().thenRun(() -> log.tracef("responded with success to %s", url)));
                    });
        });
        this.subscriber = MultiUtils.via(processor,
                m -> m.onFailure().invoke(f -> log.warnf("Unable to dispatch message to %s", url)));
    }

    protected abstract Uni<Void> send(Message<?> message);

    Flow.Subscriber<? extends Message<?>> sink() {
        return subscriber;
    }
}
