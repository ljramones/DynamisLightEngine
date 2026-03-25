package org.dynamisengine.light.impl.opengl;

import org.dynamisengine.debug.api.draw.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Renders debug draw commands (lines, wireframe boxes) from DynamisDebug.
 *
 * <p>Self-contained: owns its own shader program, VAO, and dynamic VBO.
 * Completely isolated from the main scene rendering pipeline.
 *
 * <p>Supports {@link DepthMode#TESTED} and {@link DepthMode#ALWAYS_VISIBLE}.
 * Rebuilds vertex data each frame from the active command list.
 */
final class OpenGlDebugDrawRenderer {

    private static final int FLOATS_PER_VERTEX = 6; // x,y,z,r,g,b
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * Float.BYTES;
    private static final int INITIAL_VERTEX_CAPACITY = 4096;

    // 12 edges × 2 vertices = 24 vertices per wireframe box
    private static final int BOX_VERTICES = 24;

    private int programId;
    private int vpLocation;
    private int vaoId;
    private int vboId;
    private int vboCapacityBytes;
    private boolean initialized;
    private boolean enabled = true;

    void initialize() {
        programId = createDebugShaderProgram();
        vpLocation = glGetUniformLocation(programId, "u_viewProj");

        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();
        vboCapacityBytes = INITIAL_VERTEX_CAPACITY * BYTES_PER_VERTEX;

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, vboCapacityBytes, GL_DYNAMIC_DRAW);

        // position: location 0
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, BYTES_PER_VERTEX, 0);
        // color: location 1
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, BYTES_PER_VERTEX, 3L * Float.BYTES);

        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        initialized = true;
    }

    void shutdown() {
        if (vboId != 0) { glDeleteBuffers(vboId); vboId = 0; }
        if (vaoId != 0) { glDeleteVertexArrays(vaoId); vaoId = 0; }
        if (programId != 0) { glDeleteProgram(programId); programId = 0; }
        initialized = false;
    }

    boolean isEnabled() { return enabled; }
    void setEnabled(boolean enabled) { this.enabled = enabled; }

    /**
     * Render all debug draw commands for this frame.
     *
     * @param commands active draw commands
     * @param viewProj the combined view-projection matrix (16 floats, column-major)
     */
    void render(List<DebugDrawCommand> commands, float[] viewProj) {
        if (!initialized || !enabled || commands.isEmpty()) return;

        // Split by depth mode
        renderBatch(commands, viewProj, DepthMode.TESTED, true);
        renderBatch(commands, viewProj, DepthMode.ALWAYS_VISIBLE, false);
    }

    private void renderBatch(List<DebugDrawCommand> commands, float[] viewProj,
                              DepthMode mode, boolean depthTest) {
        // Count vertices needed for this batch
        int vertexCount = 0;
        for (var cmd : commands) {
            DepthMode cmdMode = commandDepthMode(cmd);
            if (cmdMode != mode) continue;
            vertexCount += verticesForCommand(cmd);
        }
        if (vertexCount == 0) return;

        // Build vertex data
        ByteBuffer buf = ByteBuffer.allocate(vertexCount * BYTES_PER_VERTEX)
                .order(ByteOrder.nativeOrder());
        for (var cmd : commands) {
            if (commandDepthMode(cmd) != mode) continue;
            writeCommand(cmd, buf);
        }
        buf.flip();

        // Upload
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        int needed = vertexCount * BYTES_PER_VERTEX;
        if (needed > vboCapacityBytes) {
            vboCapacityBytes = needed * 2;
            glBufferData(GL_ARRAY_BUFFER, vboCapacityBytes, GL_DYNAMIC_DRAW);
        }
        glBufferSubData(GL_ARRAY_BUFFER, 0, buf);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        // Draw
        glUseProgram(programId);
        glUniformMatrix4fv(vpLocation, false, viewProj);

        if (depthTest) {
            glEnable(GL_DEPTH_TEST);
        } else {
            glDisable(GL_DEPTH_TEST);
        }

        glBindVertexArray(vaoId);
        glDrawArrays(GL_LINES, 0, vertexCount);
        glBindVertexArray(0);

        // Restore depth test
        glEnable(GL_DEPTH_TEST);
        glUseProgram(0);
    }

    // --- Command → vertex conversion ---

    private int verticesForCommand(DebugDrawCommand cmd) {
        return switch (cmd) {
            case DebugLineCommand _ -> 2;
            case DebugBoxCommand _ -> BOX_VERTICES;
            case DebugSphereCommand _ -> 0; // TODO: implement sphere wireframe
            case DebugTextCommand _ -> 0;   // handled by UI overlay
        };
    }

    private void writeCommand(DebugDrawCommand cmd, ByteBuffer buf) {
        switch (cmd) {
            case DebugLineCommand l -> writeLine(l, buf);
            case DebugBoxCommand b -> writeBox(b, buf);
            case DebugSphereCommand _ -> {} // TODO
            case DebugTextCommand _ -> {}
        }
    }

    private void writeLine(DebugLineCommand l, ByteBuffer buf) {
        putVertex(buf, l.x1(), l.y1(), l.z1(), l.r(), l.g(), l.b());
        putVertex(buf, l.x2(), l.y2(), l.z2(), l.r(), l.g(), l.b());
    }

    private void writeBox(DebugBoxCommand b, ByteBuffer buf) {
        float x0 = b.cx() - b.halfX(), y0 = b.cy() - b.halfY(), z0 = b.cz() - b.halfZ();
        float x1 = b.cx() + b.halfX(), y1 = b.cy() + b.halfY(), z1 = b.cz() + b.halfZ();
        float r = b.r(), g = b.g(), bl = b.b();

        // Bottom face (4 edges)
        edge(buf, x0, y0, z0, x1, y0, z0, r, g, bl);
        edge(buf, x1, y0, z0, x1, y0, z1, r, g, bl);
        edge(buf, x1, y0, z1, x0, y0, z1, r, g, bl);
        edge(buf, x0, y0, z1, x0, y0, z0, r, g, bl);
        // Top face (4 edges)
        edge(buf, x0, y1, z0, x1, y1, z0, r, g, bl);
        edge(buf, x1, y1, z0, x1, y1, z1, r, g, bl);
        edge(buf, x1, y1, z1, x0, y1, z1, r, g, bl);
        edge(buf, x0, y1, z1, x0, y1, z0, r, g, bl);
        // Verticals (4 edges)
        edge(buf, x0, y0, z0, x0, y1, z0, r, g, bl);
        edge(buf, x1, y0, z0, x1, y1, z0, r, g, bl);
        edge(buf, x1, y0, z1, x1, y1, z1, r, g, bl);
        edge(buf, x0, y0, z1, x0, y1, z1, r, g, bl);
    }

    private void edge(ByteBuffer buf, float x0, float y0, float z0,
                       float x1, float y1, float z1, float r, float g, float b) {
        putVertex(buf, x0, y0, z0, r, g, b);
        putVertex(buf, x1, y1, z1, r, g, b);
    }

    private void putVertex(ByteBuffer buf, float x, float y, float z, float r, float g, float b) {
        buf.putFloat(x).putFloat(y).putFloat(z).putFloat(r).putFloat(g).putFloat(b);
    }

    private DepthMode commandDepthMode(DebugDrawCommand cmd) {
        return switch (cmd) {
            case DebugLineCommand l -> l.depthMode();
            case DebugBoxCommand b -> b.depthMode();
            case DebugSphereCommand s -> s.depthMode();
            case DebugTextCommand t -> t.depthMode();
        };
    }

    // --- Shader ---

    private static int createDebugShaderProgram() {
        int vs = compileShader(GL_VERTEX_SHADER, DEBUG_VERTEX_SHADER);
        int fs = compileShader(GL_FRAGMENT_SHADER, DEBUG_FRAGMENT_SHADER);
        int prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);
        if (glGetProgrami(prog, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(prog);
            glDeleteProgram(prog);
            throw new RuntimeException("Debug shader link failed: " + log);
        }
        glDeleteShader(vs);
        glDeleteShader(fs);
        return prog;
    }

    private static int compileShader(int type, String source) {
        int id = glCreateShader(type);
        glShaderSource(id, source);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(id);
            glDeleteShader(id);
            throw new RuntimeException("Debug shader compile failed: " + log);
        }
        return id;
    }

    private static final String DEBUG_VERTEX_SHADER = """
            #version 330 core
            layout(location = 0) in vec3 a_position;
            layout(location = 1) in vec3 a_color;
            uniform mat4 u_viewProj;
            out vec3 v_color;
            void main() {
                v_color = a_color;
                gl_Position = u_viewProj * vec4(a_position, 1.0);
            }
            """;

    private static final String DEBUG_FRAGMENT_SHADER = """
            #version 330 core
            in vec3 v_color;
            out vec4 fragColor;
            void main() {
                fragColor = vec4(v_color, 1.0);
            }
            """;
}
