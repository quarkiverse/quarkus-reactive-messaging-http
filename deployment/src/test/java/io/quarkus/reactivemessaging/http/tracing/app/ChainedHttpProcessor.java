package io.quarkus.reactivemessaging.http.tracing.app;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

@ApplicationScoped
public class ChainedHttpProcessor {

    @Incoming("chained-http-source")
    @Outgoing("chained-http-sink")
    public Message<?> process(Message<?> message) {
        return message;
    }
}