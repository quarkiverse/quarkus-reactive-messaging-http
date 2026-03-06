package io.quarkus.reactivemessaging.http.source.app;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class QuotedChannelConsumer {

    private final List<String> messages = new ArrayList<>();

    @Incoming("post.http.source")
    void consume(String payload) {
        messages.add(payload);
    }

    public List<String> getMessages() {
        return messages;
    }

    public void clear() {
        messages.clear();
    }
}
