package io.quarkus.reactivemessaging.http.tracing.app;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;

@ApplicationScoped
@Path("/tracing-recorder")
public class TraceCapturingEndpoint {

    private final List<String> traceparentHeaders = new ArrayList<>();

    @POST
    public void handle(String body, @Context HttpHeaders headers) {
        traceparentHeaders.add(headers.getHeaderString("traceparent"));
    }

    public List<String> getTraceparentHeaders() {
        return traceparentHeaders;
    }

    public void clear() {
        traceparentHeaders.clear();
    }
}