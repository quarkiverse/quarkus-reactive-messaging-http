package io.quarkus.reactivemessaging.http.runtime;

/**
 * Metadata for a request path.
 *
 * Invoked/Configured path may be different when using PathParameters.
 */
public interface PathMetadata {
    /**
     * Get the path used to invoke this request, eg /users/fred if the path was configured as /users/:userid
     *
     * @return
     */
    public String getInvokedPath();

    /**
     * Get the path configured for this request, eg /users/:userid for a req using PathParams
     *
     * @return
     */
    public String getConfiguredPath();
}
