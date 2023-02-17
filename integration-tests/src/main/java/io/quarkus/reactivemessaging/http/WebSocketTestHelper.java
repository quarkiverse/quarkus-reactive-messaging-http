package io.quarkus.reactivemessaging.http;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import io.smallrye.common.vertx.VertxContext;

@ApplicationScoped
@Path("websocket-helper")
public class WebSocketTestHelper {
    private final List<String> messages = new ArrayList<>();

    @Channel("websocket-sink")
    Emitter<String> emitter;

    @Incoming("websocket-source")
    void consume(String message) {
        if (!VertxContext.isOnDuplicatedContext()) {
            throw new IllegalStateException("Expected to be on a duplicated context");
        }
        messages.add(message);
    }

    @POST
    public void add(String message) {
        emitter.send(message);
    }

    @GET
    public String getMessages() {
        return String.join(",", messages);
    }

    @DELETE
    public void clear() {
        messages.clear();
    }
}
