package io.quarkus.reactivemessaging.http.sink.app;

import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
public class QuotedChannelHttpEmitter {

    @Channel("custom.http.sink")
    Emitter<String> emitter;

    public CompletionStage<Void> emit(String payload) {
        return emitter.send(payload);
    }
}
