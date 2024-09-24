package io.quarkus.reactivemessaging.http;

import io.quarkus.reactivemessaging.http.runtime.serializers.Deserializer;
import io.vertx.core.buffer.Buffer;

public class FirstLowerCaseDeserializer implements Deserializer<String> {

    @Override
    public String deserialize(Buffer payload) {
        StringBuilder sb = new StringBuilder(payload.toString());
        if (sb.length() > 0) {
            sb.setCharAt(0, Character.toLowerCase(sb.charAt(0)));
        }
        return sb.toString();
    }
}
