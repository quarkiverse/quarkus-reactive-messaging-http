package io.quarkus.reactivemessaging.http.sink.app;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

@ApplicationScoped
@Path("/processing-endpoint")
public class HttpProcessingEndpoint {

    private List<SseEventSink> clients = new CopyOnWriteArrayList<>();
    private ReadWriteLock consumptionLock = new ReentrantReadWriteLock();

    @Context
    Sse sse;

    @POST
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void handlePost(@Context SseEventSink sink) {
        clients.add(sink);

        try {
            consumptionLock.readLock().lock();
        } finally {
            consumptionLock.readLock().unlock();
        }

        sink.send(sse.newEvent("Hello"));
    }

    public int getSize() {
        return clients.size();
    }

    public void ackAll() {
        for (SseEventSink client : clients) {
            client.send(sse.newEvent("ACK"));
        }
    }

    public void nackAll() {
        for (SseEventSink client : clients) {
            client.send(sse.newEvent("NACK"));
        }
    }

    public void closeStreams() {
        for (SseEventSink client : clients) {
            if (!client.isClosed()) {
                client.close();
            }
        }
    }

    public void clearList() {
        clients.clear();
    }

    @SuppressWarnings("LockAcquiredButNotSafelyReleased")
    public void pause() {
        consumptionLock.writeLock().lock();
    }

    public void resume() {
        consumptionLock.writeLock().unlock();
    }

}
