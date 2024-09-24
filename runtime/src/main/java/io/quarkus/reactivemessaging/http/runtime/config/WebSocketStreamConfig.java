package io.quarkus.reactivemessaging.http.runtime.config;

public class WebSocketStreamConfig extends StreamConfigBase {
    public WebSocketStreamConfig(String path, int bufferSize, String deserializerName) {
        super(bufferSize, path, deserializerName);
    }

    public String path() {
        return path;
    }
}
