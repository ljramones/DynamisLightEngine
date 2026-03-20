package org.dynamisengine.light.api.scene;

/**
 * Ready-made post-processing configurations.
 */
public final class PostProcessPresets {
    private PostProcessPresets() {}

    /** Minimal: tonemap only. Fastest. */
    public static PostProcessDesc minimal() {
        return PostProcessBuilder.create().tonemap(true, 1.0f, 2.2f).build();
    }

    /** Performance: tonemap + light bloom. */
    public static PostProcessDesc performance() {
        return PostProcessBuilder.create()
                .tonemap(true, 1.0f, 2.2f)
                .bloom(true, 1.0f, 0.2f)
                .build();
    }

    /** Balanced: tonemap + bloom + SSAO. Good default. */
    public static PostProcessDesc balanced() {
        return PostProcessBuilder.create()
                .tonemap(true, 1.0f, 2.2f)
                .bloom(true, 0.8f, 0.3f)
                .ssao(true, 0.5f)
                .build();
    }

    /** Quality: tonemap + bloom + SSAO + TAA. */
    public static PostProcessDesc quality() {
        return PostProcessBuilder.create()
                .tonemap(true, 1.0f, 2.2f)
                .bloom(true, 0.8f, 0.3f)
                .ssao(true, 0.6f, 1.2f, 0.02f, 1.5f)
                .taa(true, 0.1f, true)
                .build();
    }

    /** Cinematic: high exposure, strong bloom, full SSAO + TAA. */
    public static PostProcessDesc cinematic() {
        return PostProcessBuilder.create()
                .tonemap(true, 1.2f, 2.2f)
                .bloom(true, 0.6f, 0.5f)
                .ssao(true, 0.7f, 1.5f, 0.015f, 2.0f)
                .taa(true, 0.08f, true)
                .build();
    }

    /** Disabled: all post-processing off. */
    public static PostProcessDesc disabled() {
        return PostProcessBuilder.create().enabled(false).build();
    }
}
