package io.quarkus.reactivemessaging.http.runtime.config;

public class WebSocketStreamConfig extends StreamConfigBase {
    public WebSocketStreamConfig(String path, int bufferSize, String deserializerName) {
        // tracing is not implemented for the WebSocket connector; always inert
        super(bufferSize, path, deserializerName, false);
    }

    public String path() {
        return path;
    }
}
