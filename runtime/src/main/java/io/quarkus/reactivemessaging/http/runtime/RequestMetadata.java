package io.quarkus.reactivemessaging.http.runtime;

import java.util.Map;

import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;

/**
 * Common metadata class for incoming http/websockets.
 */
public class RequestMetadata implements PathMetadata {

    private final RoutingContext event;

    RequestMetadata(RoutingContext event) {
        this.event = event;
    }

    public MultiMap getQueryParams() {
        return event.queryParams();
    }

    public Map<String, String> getPathParams() {
        return event.pathParams();
    }

    @Override
    public String getInvokedPath() {
        return event.normalizedPath();
    }

    @Override
    public String getConfiguredPath() {
        return event.currentRoute().getPath();
    }

}
