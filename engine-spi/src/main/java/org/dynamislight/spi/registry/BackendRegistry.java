package org.dynamislight.spi.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import org.dynamislight.api.runtime.EngineApiVersion;
import org.dynamislight.api.runtime.EngineApiVersions;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.spi.EngineBackendInfo;
import org.dynamislight.spi.EngineBackendProvider;

/**
 * A registry for managing and resolving engine backend providers.
 * The {@code BackendRegistry} maintains a collection of backends that implement
 * the {@link EngineBackendProvider} interface, offering metadata and runtime
 * functionality for engine operations. This class provides mechanisms for
 * discovering, listing, and resolving specific backends based on identifiers
 * and version compatibility.
 *
 * Instances of {@code BackendRegistry} are immutable and thread-safe.
 */
public final class BackendRegistry {

    /**
     * A list of registered engine backend providers within the {@code BackendRegistry}.
     * Each provider in this list represents an implementation of the {@link EngineBackendProvider}
     * interface, offering metadata, version compatibility, and runtime capabilities of a specific
     * engine backend.
     *
     * This field is initialized during {@code BackendRegistry} construction and represents
     * an immutable collection of providers. It is primarily used for backend lookup, resolution,
     * and metadata retrieval.
     */
    private final List<EngineBackendProvider> providers;

    /**
     * Constructs a {@code BackendRegistry} instance with the given providers.
     * The {@code providers} parameter is an iterable collection of {@link EngineBackendProvider}
     * implementations. It is used to initialize the registry with an immutable list of
     * backend providers, which can later be used for lookup and resolution purposes.
     *
     * @param providers the collection of {@link EngineBackendProvider} instances to register.
     *                  Must not be {@code null}.
     * @throws NullPointerException if the {@code providers} argument is {@code null}.
     */
    public BackendRegistry(Iterable<EngineBackendProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        List<EngineBackendProvider> copy = new ArrayList<>();
        providers.forEach(copy::add);
        this.providers = List.copyOf(copy);
    }

    /**
     * Discovers available engine backend providers and initializes a new {@link BackendRegistry}
     * with them. The providers are loaded using the {@link ServiceLoader} mechanism, which detects
     * implementations of {@link EngineBackendProvider} on the classpath.
     *
     * @return a {@link BackendRegistry} instance populated with all discovered
     *         {@link EngineBackendProvider} implementations.
     */
    public static BackendRegistry discover() {
        return new BackendRegistry(ServiceLoader.load(EngineBackendProvider.class));
    }

    /**
     * Retrieves the list of registered engine backend providers.
     * Each provider represents an implementation of the {@link EngineBackendProvider}
     * interface, offering metadata and runtime capabilities of a specific engine backend.
     *
     * @return an immutable list of {@link EngineBackendProvider} instances available
     *         in this registry.
     */
    public List<EngineBackendProvider> providers() {
        return providers;
    }

    /**
     * Retrieves a list of metadata information for all registered engine backend providers.
     * Each entry in the list represents an {@link EngineBackendInfo} object containing details
     * such as the backend ID, display name, version, and description of the provider.
     *
     * The metadata is extracted by invoking the {@link EngineBackendProvider#info()} method
     * for each registered provider.
     *
     * @return a list of {@link EngineBackendInfo} instances, providing detailed metadata about
     *         each registered backend provider.
     */
    public List<EngineBackendInfo> backendInfos() {
        return providers.stream().map(EngineBackendProvider::info).toList();
    }

    /**
     * Resolves and retrieves an {@link EngineBackendProvider} based on the specified backend ID
     * and the required host API version. This method ensures that the resolved backend provider
     * matches the given backend ID and is compatible with the host's required API version.
     *
     * @param backendId            the unique identifier of the backend to resolve.
     *                              Must not be {@code null}, empty, or blank.
     * @param hostRequiredVersion  the API version required by the host.
     *                              Must not be {@code null}.
     * @return the resolved {@link EngineBackendProvider} instance that matches the
     *         specified backend ID and is compatible with the provided host API version.
     * @throws EngineException if:
     *                         <ul>
     *                         <li>The {@code backendId} is {@code null}, empty, or blank.</li>
     *                         <li>The {@code hostRequiredVersion} is {@code null}.</li>
     *                         <li>No backend provider matches the specified {@code backendId}.</li>
     *                         <li>Multiple backend providers match the specified {@code backendId}.</li>
     *                         <li>The resolved backend provider does not support the required API version.</li>
     *                         </ul>
     */
    public EngineBackendProvider resolve(String backendId, EngineApiVersion hostRequiredVersion) throws EngineException {
        if (backendId == null || backendId.isBlank()) {
            throw new EngineException(EngineErrorCode.INVALID_ARGUMENT, "backendId is required", true);
        }
        if (hostRequiredVersion == null) {
            throw new EngineException(EngineErrorCode.INVALID_ARGUMENT, "hostRequiredVersion is required", true);
        }

        List<EngineBackendProvider> matches = providers.stream()
                .filter(p -> p.backendId().equalsIgnoreCase(backendId))
                .toList();

        if (matches.isEmpty()) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_NOT_FOUND,
                    "No backend provider found for id: " + backendId,
                    true
            );
        }

        if (matches.size() > 1) {
            throw new EngineException(
                    EngineErrorCode.INTERNAL_ERROR,
                    "Multiple backend providers found for id: " + backendId,
                    false
            );
        }

        EngineBackendProvider provider = matches.getFirst();
        if (!EngineApiVersions.isRuntimeCompatible(hostRequiredVersion, provider.supportedApiVersion())) {
            throw new EngineException(
                    EngineErrorCode.INVALID_ARGUMENT,
                    "Backend " + backendId + " supports API " + provider.supportedApiVersion()
                            + " but host requires " + hostRequiredVersion,
                    true
            );
        }

        return provider;
    }
}
