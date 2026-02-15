package org.dynamislight.spi.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import org.dynamislight.api.EngineApiVersion;
import org.dynamislight.api.EngineApiVersions;
import org.dynamislight.api.EngineErrorCode;
import org.dynamislight.api.EngineException;
import org.dynamislight.spi.EngineBackendInfo;
import org.dynamislight.spi.EngineBackendProvider;

/**
 * Helper for backend discovery and deterministic provider selection.
 */
public final class BackendRegistry {
    private final List<EngineBackendProvider> providers;

    public BackendRegistry(Iterable<EngineBackendProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        List<EngineBackendProvider> copy = new ArrayList<>();
        providers.forEach(copy::add);
        this.providers = List.copyOf(copy);
    }

    public static BackendRegistry discover() {
        return new BackendRegistry(ServiceLoader.load(EngineBackendProvider.class));
    }

    public List<EngineBackendProvider> providers() {
        return providers;
    }

    public List<EngineBackendInfo> backendInfos() {
        return providers.stream().map(EngineBackendProvider::info).toList();
    }

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
