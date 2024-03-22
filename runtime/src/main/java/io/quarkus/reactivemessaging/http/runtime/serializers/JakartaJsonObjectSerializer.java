package io.quarkus.reactivemessaging.http.runtime.serializers;

import jakarta.json.JsonObject;

import io.vertx.core.buffer.Buffer;

public class JakartaJsonObjectSerializer implements Serializer<JsonObject> {
    @Override
    public boolean handles(Object payload) {
        return payload instanceof JsonObject;
    }

    @Override
    public Buffer serialize(JsonObject payload) {
        return Buffer.buffer(payload.toString());
    }
}
