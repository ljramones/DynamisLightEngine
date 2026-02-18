package org.dynamislight.impl.vulkan.shader;

import java.nio.ByteBuffer;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import static org.lwjgl.util.shaderc.Shaderc.shaderc_compilation_status_success;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_initialize;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_release;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_initialize;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_release;
import static org.lwjgl.util.shaderc.Shaderc.nshaderc_compile_into_spv;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_bytes;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_compilation_status;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_error_message;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_release;

public final class VulkanShaderCompiler {
    private VulkanShaderCompiler() {
    }

    public static long createShaderModule(VkDevice device, MemoryStack stack, ByteBuffer code) throws EngineException {
        VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                .pCode(code);
        var pShaderModule = stack.longs(VK10.VK_NULL_HANDLE);
        int result = VK10.vkCreateShaderModule(device, moduleInfo, null, pShaderModule);
        if (result != VK10.VK_SUCCESS || pShaderModule.get(0) == VK10.VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateShaderModule failed: " + result, false);
        }
        return pShaderModule.get(0);
    }

    public static ByteBuffer compileGlslToSpv(String source, int shaderKind, String sourceName) throws EngineException {
        long compiler = shaderc_compiler_initialize();
        if (compiler == 0L) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "shaderc_compiler_initialize failed", false);
        }
        long options = shaderc_compile_options_initialize();
        if (options == 0L) {
            shaderc_compiler_release(compiler);
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "shaderc_compile_options_initialize failed", false);
        }
        long result = 0L;
        ByteBuffer sourceUtf8 = null;
        ByteBuffer sourceNameUtf8 = null;
        ByteBuffer entryPointUtf8 = null;
        try {
            // Large shader sources can overflow LWJGL MemoryStack when CharSequence overload is used.
            // Use native-heap UTF8 buffers and explicit source length via nshaderc_* API.
            sourceUtf8 = MemoryUtil.memUTF8(source, true);
            // shaderc expects C-strings for source name and entry point.
            sourceNameUtf8 = MemoryUtil.memUTF8(sourceName == null ? "shader" : sourceName, true);
            entryPointUtf8 = MemoryUtil.memUTF8("main", true);
            result = nshaderc_compile_into_spv(
                    compiler,
                    MemoryUtil.memAddress(sourceUtf8),
                    sourceUtf8.remaining() - 1L,
                    shaderKind,
                    MemoryUtil.memAddress(sourceNameUtf8),
                    MemoryUtil.memAddress(entryPointUtf8),
                    options
            );
            if (result == 0L) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "shaderc_compile_into_spv failed", false);
            }
            int status = shaderc_result_get_compilation_status(result);
            if (status != shaderc_compilation_status_success) {
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "Shader compile failed for " + sourceName + ": " + shaderc_result_get_error_message(result),
                        false
                );
            }
            ByteBuffer bytes = shaderc_result_get_bytes(result);
            ByteBuffer out = ByteBuffer.allocateDirect(bytes.remaining());
            out.put(bytes);
            out.flip();
            return out;
        } finally {
            if (result != 0L) {
                shaderc_result_release(result);
            }
            shaderc_compile_options_release(options);
            shaderc_compiler_release(compiler);
            if (sourceUtf8 != null) {
                MemoryUtil.memFree(sourceUtf8);
            }
            if (sourceNameUtf8 != null) {
                MemoryUtil.memFree(sourceNameUtf8);
            }
            if (entryPointUtf8 != null) {
                MemoryUtil.memFree(entryPointUtf8);
            }
        }
    }
}
