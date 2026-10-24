package dev.reme.worldseam;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;

import java.nio.IntBuffer;

/**
 * Experimental REAL two-depth screen-space seam compositor.
 *
 * Each pixel is admitted only if the camera ray crosses the physical
 * source-world seam before its nearest source-world depth sample, AND the
 * foreign geometry depth is behind that seam on the correct side.
 * Missing foreign depth never becomes an invented sky or a black fill.
 *
 * This is deliberately opt-in until vanilla translucent depth, weather,
 * Sable sublevels and modded shaders have been verified in a GUI. It is not
 * a substitute for a complete second LevelRenderer or fused Rapier physics.
 */
public final class SeamForeignDepthCompositor {
    private static final Logger LOG = LogUtils.getLogger();
    private static TextureTarget sourceDepthCopy;
    private static int program;
    private static int vao;
    private static long composites;
    private static long failures;

    private static final String VERTEX = """
        #version 150
        out vec2 texCoord;
        void main() {
            vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
            // The oversized triangle covers NDC [-1,1] using p in [0,2].
            // Screen UV = (NDC + 1) / 2 = p, NOT p * 0.5.
            // Halving p samples only the lower-left quarter of both depth
            // textures, misaligning source/foreign depth with the camera ray.
            texCoord = p;
            gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
        }
        """;
    private static final String FRAGMENT = """
        #version 150
        in vec2 texCoord;
        out vec4 fragColor;
        uniform sampler2D foreignColor;
        uniform sampler2D foreignDepth;
        uniform sampler2D sourceDepth;
        uniform mat4 inverseProjectionView;
        uniform float seamCameraY;
        uniform int remoteBelow;

        vec3 reconstruct(vec2 xy, float depth) {
            vec4 point = inverseProjectionView *
                vec4(xy * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
            return point.xyz / point.w;
        }

        void main() {
            float remoteZ = texture(foreignDepth, texCoord).r;
            // The far-plane/sky depth is not a real foreign surface.
            if (remoteZ >= 0.999999) discard;
            vec3 farPoint = reconstruct(texCoord, 1.0);
            if (abs(farPoint.y) < 0.00001) discard;
            float seamFraction = seamCameraY / farPoint.y;
            if (seamFraction <= 0.0 || seamFraction >= 1.0) discard;

            float seamDistance = length(farPoint * seamFraction);
            vec3 remotePoint = reconstruct(texCoord, remoteZ);
            // A remote surface on the camera's side is never composited.
            if (length(remotePoint) <= seamDistance + 0.001) discard;
            if (remoteBelow != 0 && remotePoint.y >= seamCameraY) discard;
            if (remoteBelow == 0 && remotePoint.y <= seamCameraY) discard;

            float sourceZ = texture(sourceDepth, texCoord).r;
            if (sourceZ < 0.999999) {
                vec3 sourcePoint = reconstruct(texCoord, sourceZ);
                if (length(sourcePoint) < seamDistance - 0.001) discard;
            }
            vec4 realForeign = texture(foreignColor, texCoord);
            if (realForeign.a <= 0.001) discard;
            fragColor = vec4(realForeign.rgb, 1.0);
            // Keep the final framebuffer's depth consistent with the real
            // foreign surface selected by the seam-plane ray test.
            gl_FragDepth = remoteZ;
        }
        """;

    private SeamForeignDepthCompositor() {}

    public static long composites() { return composites; }
    public static long failures() { return failures; }
    /** Live GL ownership, not cumulative invocation count; must clear on disable. */
    public static boolean allocated() {
        return sourceDepthCopy != null || program != 0 || vao != 0;
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("WorldSeam seam shader compile: " + log);
        }
        return shader;
    }

    private static void ensureProgram() {
        if (program != 0) return;
        int vs = compileShader(GL20.GL_VERTEX_SHADER, VERTEX);
        int fs = 0;
        int linked = 0;
        try {
            fs = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
            linked = GL20.glCreateProgram();
            GL20.glAttachShader(linked, vs);
            GL20.glAttachShader(linked, fs);
            GL20.glLinkProgram(linked);
            if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
                throw new IllegalStateException("WorldSeam seam shader link: " +
                    GL20.glGetProgramInfoLog(linked));
            program = linked;
            linked = 0;
            vao = GL30.glGenVertexArrays();
        } finally {
            GL20.glDeleteShader(vs);
            if (fs != 0) GL20.glDeleteShader(fs);
            if (linked != 0) GL20.glDeleteProgram(linked);
        }
    }

    private static void ensureDepthCopy(int width, int height) {
        if (sourceDepthCopy == null) {
            sourceDepthCopy = new TextureTarget(width, height, true, true);
        } else if (sourceDepthCopy.width != width ||
                   sourceDepthCopy.height != height) {
            sourceDepthCopy.resize(width, height, true);
        }
    }

    /**
     * The source depth is copied BEFORE sampling: sampling an attachment of
     * the currently written framebuffer is an undefined OpenGL feedback loop.
     * Only the source COLOR attachment is written by the final composite.
     */
    public static boolean composite(Minecraft mc, int foreignColorId,
                                    int foreignDepthId, Matrix4f modelView,
                                    Matrix4f projection, float seamCameraY,
                                    boolean remoteBelow) {
        RenderSystem.assertOnRenderThread();
        RenderTarget source = mc.getMainRenderTarget();
        if (source == null || source.width <= 0 || source.height <= 0 ||
            source.getDepthTextureId() <= 0 ||
            foreignColorId <= 0 || foreignDepthId <= 0) return false;

        int oldFbo = GlStateManager.getBoundFramebuffer();
        IntBuffer oldViewport = BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, oldViewport);
        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int oldVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int oldActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] oldTextureBindings = new int[3];
        for (int unit = 0; unit < 3; unit++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            oldTextureBindings[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int oldDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        try {
            ensureProgram();
            ensureDepthCopy(source.width, source.height);
            source.bindWrite(false);
            int sourceFbo = GlStateManager.getBoundFramebuffer();
            sourceDepthCopy.bindWrite(false);
            int depthCopyFbo = GlStateManager.getBoundFramebuffer();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, depthCopyFbo);
            GL30.glBlitFramebuffer(0, 0, source.width, source.height,
                0, 0, source.width, source.height,
                GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

            source.bindWrite(true);
            // The shader has already rejected source surfaces in front of
            // the seam. ALWAYS permits replacing the source depth behind it
            // with the matching foreign depth; downstream native draws then
            // see the same surface that the player sees.
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_ALWAYS);
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL20.glUseProgram(program);
            int[] textures = {
                foreignColorId, foreignDepthId, sourceDepthCopy.getDepthTextureId()
            };
            String[] samplers = {"foreignColor", "foreignDepth", "sourceDepth"};
            for (int unit = 0; unit < 3; unit++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[unit]);
                GL20.glUniform1i(GL20.glGetUniformLocation(program, samplers[unit]), unit);
            }
            Matrix4f inverse = new Matrix4f(projection).mul(modelView).invert();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                GL20.glUniformMatrix4fv(
                    GL20.glGetUniformLocation(program, "inverseProjectionView"),
                    false, inverse.get(stack.mallocFloat(16)));
            }
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "seamCameraY"),
                seamCameraY);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "remoteBelow"),
                remoteBelow ? 1 : 0);
            GL30.glBindVertexArray(vao);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            composites++;
            if (composites == 1 || composites % 120 == 0)
                LOG.info("WORLDSEAM_NATIVE_DEPTH_COMPOSITE: passes={}, depthSnapshot=true, seamCameraY={}, remoteBelow={}, finalAcceptance=false",
                    composites, seamCameraY, remoteBelow);
            return true;
        } catch (RuntimeException | Error failure) {
            failures++;
            LOG.error("WorldSeam experimental depth composite failed; leaving native source frame intact", failure);
            return false;
        } finally {
            GL30.glBindVertexArray(oldVao);
            GL20.glUseProgram(oldProgram);
            for (int unit = 0; unit < 3; unit++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTextureBindings[unit]);
            }
            GL13.glActiveTexture(oldActiveTexture);
            if (depthEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST);
            else GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (blendEnabled) GL11.glEnable(GL11.GL_BLEND);
            else GL11.glDisable(GL11.GL_BLEND);
            if (cullEnabled) GL11.glEnable(GL11.GL_CULL_FACE);
            else GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthFunc(oldDepthFunc);
            GL11.glDepthMask(depthMask);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, oldFbo);
            RenderSystem.viewport(oldViewport.get(0), oldViewport.get(1),
                oldViewport.get(2), oldViewport.get(3));
        }
    }

    public static void release() {
        RenderSystem.assertOnRenderThread();
        if (sourceDepthCopy != null) {
            sourceDepthCopy.destroyBuffers();
            sourceDepthCopy = null;
        }
        if (vao != 0) {
            GL30.glDeleteVertexArrays(vao);
            vao = 0;
        }
        if (program != 0) {
            GL20.glDeleteProgram(program);
            program = 0;
        }
    }
}
