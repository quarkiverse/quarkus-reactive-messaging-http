package io.quarkus.reactivemessaging.http.runtime.serializers;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

/**
 * a base superclass for a SerializerFactory that is generated in build time
 */
public abstract class DeserializerFactoryBase {
    private static final Logger log = Logger.getLogger(DeserializerFactoryBase.class);

    private final Map<String, Deserializer<?>> deserializersByClassName = new HashMap<>();

    protected DeserializerFactoryBase() {
        initAdditionalSerializers();
    }

    /**
     * get a {@link Serializer} of a given (class) name
     *
     * @param name name of the serializer
     * @param payload payload to serialize
     * @param <T> type of the payload
     * @return An optional deserializer
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<Deserializer<T>> getDeserializer(String name) {
        return name != null ? Optional.ofNullable((Deserializer<T>) deserializersByClassName.get(name)) : Optional.empty();
    }

    public void addSerializer(String className, Deserializer<?> serializer) {
        deserializersByClassName.put(className, serializer);
    }

    /**
     * method that initializes additional serializers (used by user's config).
     * Implemented in the generated subclass
     */
    protected abstract void initAdditionalSerializers();

}
