package io.quarkus.reactivemessaging.http.runtime;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import io.quarkus.reactivemessaging.http.runtime.config.TlsConfig;
import io.quarkus.reactivemessaging.http.runtime.serializers.Serializer;
import io.quarkus.reactivemessaging.http.runtime.serializers.SerializerFactoryBase;
import io.quarkus.tls.TlsConfiguration;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.unchecked.Unchecked;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpRequest;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;

class HttpSink extends AbstractSink {

    private static final Logger log = Logger.getLogger(HttpSink.class);

    private static final String[] SUPPORTED_SCHEMES = { "http:", "https:" };

    private final WebClient client;
    private final String method;
    private final String url;
    private final SerializerFactoryBase serializerFactory;
    private final String serializerName;

    HttpSink(Vertx vertx, String method, String url,
            String serializerName,
            int maxRetries,
            double jitter,
            Optional<Duration> delay,
            Optional<Integer> maxPoolSize,
            Optional<Integer> maxWaitQueueSize,
            SerializerFactoryBase serializerFactory,
            Optional<TlsConfiguration> tlsConfiguration,
            long inflights,
            boolean waitForCompletion,
            HttpVersion protocolVersion) {
        super(log, url, maxRetries, jitter, delay, inflights, waitForCompletion);
        this.method = method;
        this.url = url;
        this.serializerFactory = serializerFactory;
        this.serializerName = serializerName;

        WebClientOptions options = new WebClientOptions();
        maxPoolSize.ifPresent(options::setMaxPoolSize);
        maxWaitQueueSize.ifPresent(options::setMaxWaitQueueSize);

        tlsConfiguration.ifPresent(config -> TlsConfig.configure(options, config));

        options.setProtocolVersion(protocolVersion);
        if (protocolVersion == HttpVersion.HTTP_2 && tlsConfiguration.isPresent()) {
            // Required for HTTP/2. See https://vertx.io/docs/vertx-core/java/#_creating_an_http_client
            options.setUseAlpn(true);
        }

        client = WebClient.create(io.vertx.mutiny.core.Vertx.newInstance(vertx), options);

        if (Arrays.stream(SUPPORTED_SCHEMES).noneMatch(url.toLowerCase()::startsWith)) {
            throw new IllegalArgumentException("Unsupported scheme for the http connector in URL: " + url);
        }
    }

    @Override
    protected Uni<Void> send(Message<?> message) {
        HttpRequest<?> request = toHttpRequest(message);
        return Uni.createFrom().item(message.getPayload())
                .onItem().transform(this::serialize)
                .onItem().transformToUni(buffer -> invoke(request, buffer));
    }

    private <T> Buffer serialize(T payload) {
        Serializer<T> serializer = serializerFactory.getSerializer(serializerName, payload);
        return Buffer.newInstance(serializer.serialize(payload));
    }

    private Uni<Void> invoke(HttpRequest<?> request, Buffer buffer) {
        log.debugf("Invoking request: ", toString(request, buffer));
        return request
                .sendBuffer(buffer)
                .onItem().transform(Unchecked.function(resp -> {
                    if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                        return null;
                    } else {
                        throw new VertxException(
                                "Http request: " + toString(request, buffer) + " failed with response: " + toString(resp));
                    }
                }));
    }

    private String toString(HttpRequest<?> req, Buffer buffer) {
        return "URI:" + req.uri() + " Method:" + req.method() + " Headers: " + req.headers() + " Body: " + buffer;
    }

    private String toString(HttpResponse<?> resp) {
        return "Code: " + resp.statusCode() + " Message: " + resp.statusMessage();
    }

    private HttpRequest<?> toHttpRequest(Message<?> message) {
        try {
            OutgoingHttpMetadata metadata = message.getMetadata(OutgoingHttpMetadata.class).orElse((OutgoingHttpMetadata) null);

            Map<String, String> cloudEventHeaders = HttpCloudEventHelper.getCloudEventHeaders(message);
            Map<String, List<String>> httpHeaders = metadata != null ? metadata.getHeaders() : Collections.emptyMap();
            httpHeaders = safeAddAll(httpHeaders, cloudEventHeaders);

            Map<String, List<String>> query = metadata != null ? metadata.getQuery() : Collections.emptyMap();
            Map<String, String> pathParams = metadata != null ? metadata.getPathParameters() : Collections.emptyMap();

            String url = prepareUrl(pathParams);

            HttpRequest<Buffer> request = createRequest(url);

            addHeaders(request, httpHeaders);

            addQueryParameters(query, request);

            return request;
        } catch (Exception any) {
            log.error("Failed to transform message to http request", any);
            throw any;
        }
    }

    private Map<String, List<String>> safeAddAll(Map<String, List<String>> httpHeaders,
            Map<String, String> cloudEventHeaders) {
        if (cloudEventHeaders.isEmpty()) {
            return httpHeaders;
        } else if (httpHeaders.isEmpty()) {
            return cloudEventHeaders.entrySet().stream()
                    .collect(Collectors.toMap(Entry::getKey, e -> List.<String> of(e.getValue())));
        } else {
            // httpHeaders might be inmutable
            Map<String, List<String>> mergedMap = new HashMap<>(httpHeaders);
            for (Entry<String, String> entry : cloudEventHeaders.entrySet()) {
                mergedMap.put(entry.getKey(), List.of(entry.getValue()));
            }
            return mergedMap;
        }
    }

    private HttpRequest<Buffer> createRequest(String url) {
        return switch (method) {
            case "POST" -> client.postAbs(url);
            case "PUT" -> client.putAbs(url);
            default ->
                throw new IllegalArgumentException("Unsupported HTTP method: " + method + "only PUT and POST are supported");
        };
    }

    private void addQueryParameters(Map<String, List<String>> query, HttpRequest<Buffer> request) {
        for (Map.Entry<String, List<String>> queryParam : query.entrySet()) {
            for (String queryParamValue : queryParam.getValue()) {
                request.addQueryParam(queryParam.getKey(), queryParamValue);
            }
        }
    }

    private void addHeaders(HttpRequest<Buffer> request, Map<String, List<String>> httpHeaders) {
        if (!httpHeaders.isEmpty()) {
            for (Map.Entry<String, List<String>> header : httpHeaders.entrySet()) {
                request.putHeader(header.getKey(), header.getValue());
            }
        }
    }

    private String prepareUrl(Map<String, String> pathParams) {
        String result = url;
        for (Map.Entry<String, String> pathParamEntry : pathParams.entrySet()) {
            String toReplace = String.format("{%s}", pathParamEntry.getKey());
            if (url.contains(toReplace)) {
                result = url.replace(toReplace, pathParamEntry.getValue());
            } else {
                log.warnf("Failed to find %s in the URL that would correspond to the %s path parameter",
                        toReplace, pathParamEntry.getKey());
            }
        }

        return result;
    }
}
