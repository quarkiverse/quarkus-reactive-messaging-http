package io.quarkus.reactivemessaging.http.sink.app;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

@ApplicationScoped
public class HttpProcessingEmitter {

    @Channel("my-http-sink")
    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 2000)
    Emitter<Object> emitter;

    public void emitObject(Message<Object> message) {
        emitter.send(message);
    }

    public boolean hasRequests() {
        return emitter.hasRequests();
    }
}
