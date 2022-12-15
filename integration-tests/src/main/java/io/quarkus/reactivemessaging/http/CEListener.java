package io.quarkus.reactivemessaging.http;

import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

import io.smallrye.common.vertx.VertxContext;
import io.smallrye.reactive.messaging.ce.CloudEventMetadata;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadataBuilder;

@ApplicationScoped
public class CEListener {

    @Inject
    @Channel("ceresource")
    Emitter<String> emitter;

    @Incoming("celistener")
    CompletionStage<Void> listener(Message<?> message) {
        if (!VertxContext.isOnDuplicatedContext()) {
            throw new IllegalStateException("Expected to be on a duplicated context");
        }
        message.getMetadata(CloudEventMetadata.class).map(m -> Message.of("aBody", Metadata.of(convert(m))))
                .ifPresent(this::send);
        return message.ack();
    }

    private void send(Message<String> m) {
        emitter.send(m);
    }

    private OutgoingCloudEventMetadata<?> convert(CloudEventMetadata<?> m) {
        OutgoingCloudEventMetadataBuilder<?> builder = new OutgoingCloudEventMetadataBuilder<>();
        m.getDataSchema().ifPresent(builder::withDataSchema);
        m.getDataContentType().ifPresent(builder::withDataContentType);
        m.getSubject().ifPresent(builder::withSubject);
        m.getTimeStamp().ifPresent(builder::withTimestamp);
        builder.withId(m.getId());
        builder.withSource(m.getSource());
        builder.withType(m.getType());
        builder.withSpecVersion(m.getSpecVersion());
        builder.withExtensions(m.getExtensions());
        return builder.build();
    }
}
