package io.quarkus.reactivemessaging.websocket.source.app;

import io.quarkus.reactivemessaging.http.runtime.RequestMetadata;
import io.quarkus.reactivemessaging.utils.VertxFriendlyLock;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class Consumer {

    private RequestMetadata requestMetadata = null;
    private final List<String> messages = new ArrayList<>();
    private final List<Dto> dtos = new ArrayList<>();

    VertxFriendlyLock lock;

    @Inject
    Consumer(Vertx vertx) {
        lock = new VertxFriendlyLock(vertx);
    }

    @Incoming("my-ws-source-json")
    public void consumeJson(Dto dto) {
        dtos.add(dto);
    }

    @Incoming("my-ws-source")
    public CompletionStage<Void> process(Message<String> message) {
        CompletableFuture<Void> result = new CompletableFuture<>();

        lock.triggerWhenUnlocked(() -> {
            messages.add(message.getPayload());
            message.ack();
            result.complete(null);
        }, 10000);
        return result;
    }

    @Incoming("my-ws-source-buffer-13")
    public CompletionStage<Void> processWithBuffer13(Message<String> message) {
        CompletableFuture<Void> result = new CompletableFuture<>();

        lock.triggerWhenUnlocked(() -> {
            messages.add(message.getPayload());
            message.ack();
            result.complete(null);
        }, 10000);
        return result;
    }

    @Incoming("my-ws-pathparam")
    public CompletionStage<Void> processWithPathParam(Message<String> message) {
        CompletableFuture<Void> result = new CompletableFuture<>();

        lock.triggerWhenUnlocked(() -> {
            messages.add(message.getPayload());
            requestMetadata = message.getMetadata(RequestMetadata.class).get();
            message.ack();
            result.complete(null);
        }, 10000);
        return result;
    }

    public List<String> getMessages() {
        return messages;
    }

    public RequestMetadata getRequestMetadata() {
        return requestMetadata;
    }

    public void pause() {
        lock.lock();
    }

    public void resume() {
        lock.unlock();
    }

    public void clear() {
        messages.clear();
        requestMetadata = null;
        lock.reset();
    }

    public List<Dto> getDtos() {
        return dtos;
    }

    public static class Dto {
        private String field;

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }
    }
}
