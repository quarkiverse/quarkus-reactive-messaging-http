package io.quarkus.reactivemessaging.http.runtime.serializers;

import jakarta.json.JsonArray;

import io.vertx.core.buffer.Buffer;

public class JakartaJsonArraySerializer implements Serializer<JsonArray> {
    @Override
    public boolean handles(Object payload) {
        return payload instanceof JsonArray;
    }

    @Override
    public Buffer serialize(JsonArray payload) {
        return Buffer.buffer(payload.toString());
    }
}
