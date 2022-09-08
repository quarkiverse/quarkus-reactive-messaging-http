package io.quarkus.reactivemessaging.http.runtime;

import java.net.URI;
import java.time.DateTimeException;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.smallrye.reactive.messaging.ce.CloudEventMetadata;
import io.smallrye.reactive.messaging.ce.DefaultCloudEventMetadataBuilder;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;

public class HttpCloudEventHelper {
    private static final Logger logger = LoggerFactory.getLogger(HttpCloudEventHelper.class);

    private static final String[] CE_PREFIXES = { "ce-", "ce_" };

    public static Optional<CloudEventMetadata<?>> getBinaryCloudEvent(IncomingHttpMetadata metadata) {
        DefaultCloudEventMetadataBuilder<?> builder = new DefaultCloudEventMetadataBuilder<>();
        boolean hasCloudMeta = false;
        for (Entry<String, String> entry : metadata.getHeaders().entries()) {
            hasCloudMeta |= getCEAttribute(builder, entry.getKey(), entry.getValue());
        }
        return hasCloudMeta ? Optional.of(builder.build()) : Optional.empty();
    }

    public static Map<String, String> getCloudEventHeaders(Message<?> message) {
        return message.getMetadata(OutgoingCloudEventMetadata.class).map(HttpCloudEventHelper::getCloudEventHeaders)
                .orElse(Collections.emptyMap());
    }

    private static Map<String, String> getCloudEventHeaders(OutgoingCloudEventMetadata<?> metadata) {
        Map<String, String> map = new HashMap<>();
        metadata.getDataContentType().ifPresent(v -> map.put(header(CloudEventMetadata.CE_ATTRIBUTE_DATA_CONTENT_TYPE), v));
        metadata.getDataSchema().ifPresent(v -> map.put(header(CloudEventMetadata.CE_ATTRIBUTE_DATA_SCHEMA), v.toString()));
        metadata.getSubject().ifPresent(v -> map.put(header(CloudEventMetadata.CE_ATTRIBUTE_SUBJECT), v));
        metadata.getTimeStamp().ifPresent(v -> map.put(header(CloudEventMetadata.CE_ATTRIBUTE_TIME), v.toString()));
        map.put(header(CloudEventMetadata.CE_ATTRIBUTE_ID), metadata.getId());
        if (metadata.getSource() != null) {
            map.put(header(CloudEventMetadata.CE_ATTRIBUTE_SOURCE), metadata.getSource().toString());
        }
        map.put(header(CloudEventMetadata.CE_ATTRIBUTE_SPEC_VERSION), metadata.getSpecVersion());
        map.put(header(CloudEventMetadata.CE_ATTRIBUTE_TYPE), metadata.getType());
        metadata.getExtensions().forEach((k, v) -> map.put(header(k), v.toString()));
        return map;
    }

    private static String header(String key) {
        return CE_PREFIXES[0] + key;
    }

    private static boolean getCEAttribute(DefaultCloudEventMetadataBuilder<?> builder, String key, String value) {
        final String lowerCase = key.toLowerCase();
        boolean sucessfulSet = false;
        for (String prefix : CE_PREFIXES) {
            if (lowerCase.startsWith(prefix)) {
                try {
                    String extractKey = lowerCase.substring(prefix.length());
                    switch (extractKey) {
                        case CloudEventMetadata.CE_ATTRIBUTE_ID:
                            builder.withId(value);
                            break;
                        case CloudEventMetadata.CE_ATTRIBUTE_SOURCE:
                            builder.withSource(URI.create(value));
                            break;
                        case CloudEventMetadata.CE_ATTRIBUTE_SPEC_VERSION:
                            builder.withSpecVersion(value);
                            break;
                        case CloudEventMetadata.CE_ATTRIBUTE_SUBJECT:
                            builder.withSubject(value);
                            break;
                        case CloudEventMetadata.CE_ATTRIBUTE_TIME:
                            builder.withTimestamp(ZonedDateTime.parse(value));
                            break;
                        case CloudEventMetadata.CE_ATTRIBUTE_TYPE:
                            builder.withType(value);
                            break;
                        case CloudEventMetadata.CE_ATTRIBUTE_DATA_CONTENT_TYPE:
                            builder.withDataContentType(value);
                            break;
                        case CloudEventMetadata.CE_ATTRIBUTE_DATA_SCHEMA:
                            builder.withDataSchema(URI.create(value));
                            break;
                        default:
                            logger.info("Unrecognized CE attribute {}, assuming extension", key);
                            builder.withExtension(extractKey, value);
                    }
                    sucessfulSet = true;
                } catch (IllegalArgumentException | DateTimeException ex) {
                    logger.error("Error setting value {} for attribute {}", key, value, ex);
                }
            }
        }
        return sucessfulSet;
    }

    private HttpCloudEventHelper() {
    }
}
