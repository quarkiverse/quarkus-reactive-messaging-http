package io.quarkus.reactivemessaging.http.source.app;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import io.smallrye.reactive.messaging.annotations.Blocking;

@ApplicationScoped
public class HttpProcessingConsumer {

    private final List<Message<String>> messages = new ArrayList<>();

    private ReadWriteLock consumptionLock = new ReentrantReadWriteLock();

    HttpProcessingConsumer() {
    }

    @Incoming("my-http-consumer")
    @Blocking
    public CompletionStage<Void> handlePost(Message<String> message) {
        CompletableFuture<Void> result = new CompletableFuture<>();

        consumptionLock.readLock().lock();

        try {
            messages.add(message);
            result.complete(null);
        } finally {
            consumptionLock.readLock().unlock();
        }

        return result;
    }

    public void ackAll() {
        for (Message<String> message : messages) {
            message.ack();
        }
    }

    public void nackAll() {
        for (Message<String> message : messages) {
            message.nack(new RuntimeException("Message nacked"));
        }
    }

    public int getSize() {
        return messages.size();
    }

    public void pause() {
        consumptionLock.writeLock().lock();
    }

    public void resume() {
        consumptionLock.writeLock().unlock();
    }

    public void clear() {
        messages.clear();
    }

}
