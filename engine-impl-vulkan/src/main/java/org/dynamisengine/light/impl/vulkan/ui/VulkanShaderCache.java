package org.dynamisengine.light.impl.vulkan.ui;

import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.logging.Logger;

import static org.lwjgl.util.shaderc.Shaderc.*;

/**
 * Caches compiled SPIR-V shader bytecode on disk to avoid runtime
 * recompilation after the first run.
 *
 * <p>Cache key: SHA-256 of (shader source + shader stage). If the cached
 * file exists and the hash matches, the SPIR-V is loaded from disk.
 * Otherwise, the shader is compiled via shaderc and the result is cached.
 *
 * <p>Cache location: {@code ~/.dynamis/shader-cache/} by default,
 * configurable via constructor.
 */
public final class VulkanShaderCache {

    private static final Logger LOG = Logger.getLogger(VulkanShaderCache.class.getName());

    private final Path cacheDir;

    public VulkanShaderCache() {
        this(Path.of(System.getProperty("user.home"), ".dynamis", "shader-cache"));
    }

    public VulkanShaderCache(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    /**
     * Load cached SPIR-V or compile from GLSL source.
     *
     * @param name        shader name for logging (e.g. "ui_quad.vert")
     * @param source      GLSL source code
     * @param shaderKind  shaderc shader kind constant
     * @return direct ByteBuffer containing SPIR-V (caller must free with MemoryUtil.memFree)
     */
    public ByteBuffer loadOrCompile(String name, String source, int shaderKind) {
        String hash = hash(source, shaderKind);
        String cacheFileName = name.replace('/', '_') + "." + hash.substring(0, 12) + ".spv";
        Path cachePath = cacheDir.resolve(cacheFileName);

        // Try cache hit
        if (Files.exists(cachePath)) {
            try {
                byte[] bytes = Files.readAllBytes(cachePath);
                ByteBuffer buf = MemoryUtil.memAlloc(bytes.length);
                buf.put(bytes).flip();
                LOG.fine("Shader cache hit: " + cacheFileName);
                return buf;
            } catch (IOException e) {
                LOG.warning("Failed to read cached shader " + cacheFileName + ": " + e.getMessage());
                // Fall through to compile
            }
        }

        // Cache miss — compile
        LOG.info("Shader cache miss, compiling: " + name);
        ByteBuffer spirv = compile(source, name, shaderKind);

        // Write to cache
        try {
            Files.createDirectories(cacheDir);
            byte[] bytes = new byte[spirv.remaining()];
            spirv.get(bytes);
            spirv.flip(); // reset position for caller
            Files.write(cachePath, bytes);
            LOG.fine("Shader cached: " + cacheFileName + " (" + bytes.length + " bytes)");
        } catch (IOException e) {
            LOG.warning("Failed to cache shader " + cacheFileName + ": " + e.getMessage());
        }

        return spirv;
    }

    /**
     * Invalidate all cached shaders.
     */
    public void clearCache() {
        if (!Files.exists(cacheDir)) return;
        try (var stream = Files.list(cacheDir)) {
            stream.filter(p -> p.toString().endsWith(".spv")).forEach(p -> {
                try { Files.delete(p); } catch (IOException ignored) {}
            });
            LOG.info("Shader cache cleared");
        } catch (IOException e) {
            LOG.warning("Failed to clear shader cache: " + e.getMessage());
        }
    }

    // --- Compilation ---

    private ByteBuffer compile(String source, String name, int shaderKind) {
        long compiler = shaderc_compiler_initialize();
        if (compiler == 0) throw new RuntimeException("Failed to initialize shaderc");

        long result = shaderc_compile_into_spv(compiler, source, shaderKind, name, "main", 0);
        if (shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
            String error = shaderc_result_get_error_message(result);
            shaderc_result_release(result);
            shaderc_compiler_release(compiler);
            throw new RuntimeException("Shader compilation failed: " + name + "\n" + error);
        }

        ByteBuffer spirv = shaderc_result_get_bytes(result);
        ByteBuffer copy = MemoryUtil.memAlloc(spirv.remaining());
        copy.put(spirv).flip();

        shaderc_result_release(result);
        shaderc_compiler_release(compiler);
        return copy;
    }

    // --- Hashing ---

    private static String hash(String source, int shaderKind) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(source.getBytes());
            digest.update((byte) (shaderKind >> 24));
            digest.update((byte) (shaderKind >> 16));
            digest.update((byte) (shaderKind >> 8));
            digest.update((byte) shaderKind);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
