package io.quarkus.reactivemessaging.http.runtime.serializers;

import io.vertx.core.buffer.Buffer;

/**
 * Reactive http connector deserializer.
 * Deserializes given payload from a {@link Buffer}
 *
 *
 * @param <PayloadType> type of the payload to deserialize
 */
public interface Deserializer<PayloadType> {

    /**
     * Deserialize the payload
     *
     * @param payload buffer to deserialize
     * @return deserialized object
     */
    PayloadType deserialize(Buffer payload);

}
