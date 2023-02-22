package io.quarkus.reactivemessaging.http.sink.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import org.jboss.resteasy.annotations.jaxrs.PathParam;

@ApplicationScoped
@Path("/recorder")
public class HttpEndpoint {
    private List<Request> requests = new ArrayList<>();
    private Map<String, Request> identifiableRequests = new HashMap<>();
    private AtomicInteger initialFailures = new AtomicInteger(0);
    private ReadWriteLock consumptionLock = new ReentrantReadWriteLock();

    @POST
    @Path("{id}")
    public void handleRequestWithIdAndParams(String body,
            @PathParam String id,
            @Context HttpHeaders headers,
            @Context UriInfo uriInfo) {
        identifiableRequests.put(id, new Request(body, headers.getRequestHeaders(), uriInfo.getQueryParameters()));
    }

    @POST
    public Response handlePost(String body) {
        consumptionLock.readLock().lock();
        try {
            if (initialFailures.getAndDecrement() > 0) {
                return Response.status(500).entity("forced failure").build();
            }
            requests.add(new Request(body, null, null));
            return Response.ok().entity("bye").build();
        } finally {
            consumptionLock.readLock().unlock();
        }
    }

    public List<Request> getRequests() {
        return requests;
    }

    public Map<String, Request> getIdentifiableRequests() {
        return identifiableRequests;
    }

    public static class Request {
        String body;
        MultivaluedMap<String, String> headers;
        MultivaluedMap<String, String> queryParameters;

        public Request(String body, MultivaluedMap<String, String> requestHeaders,
                MultivaluedMap<String, String> queryParameters) {
            this.body = body;
            this.queryParameters = queryParameters;
            headers = requestHeaders;
        }

        public String getBody() {
            return body;
        }

        public MultivaluedMap<String, String> getHeaders() {
            return headers;
        }

        public MultivaluedMap<String, String> getQueryParameters() {
            return queryParameters;
        }
    }

    public void setInitialFailures(int initialFailures) {
        this.initialFailures.set(initialFailures);
    }

    public void reset() {
        requests.clear();
        initialFailures.set(0);
        try {
            consumptionLock.writeLock().unlock();
        } catch (RuntimeException ignored) {
        }
        try {
            consumptionLock.readLock().unlock();
        } catch (RuntimeException ignored) {
        }
    }

    @SuppressWarnings("LockAcquiredButNotSafelyReleased")
    public void pause() {
        consumptionLock.writeLock().lock();
    }

    public void release() {
        consumptionLock.writeLock().unlock();
    }
}
