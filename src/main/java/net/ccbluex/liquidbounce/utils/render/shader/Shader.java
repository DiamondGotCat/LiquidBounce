/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.utils.render.shader;

import net.ccbluex.liquidbounce.utils.MinecraftInstance;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.ARBFragmentShader;
import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.ARBVertexShader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static net.ccbluex.liquidbounce.utils.ClientUtils.LOGGER;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUseProgram;

public abstract class Shader extends MinecraftInstance {
    private int program;

    private Map<String, Integer> uniformsMap;

    public Shader(final String fragmentShader) {
        int vertexShaderID, fragmentShaderID;

        try {
            final InputStream vertexStream = getClass().getResourceAsStream("/assets/minecraft/liquidbounce/shader/vertex.vert");
            vertexShaderID = createShader(IOUtils.toString(vertexStream), ARBVertexShader.GL_VERTEX_SHADER_ARB);
            IOUtils.closeQuietly(vertexStream);

            final InputStream fragmentStream = getClass().getResourceAsStream("/assets/minecraft/liquidbounce/shader/fragment/" + fragmentShader);
            fragmentShaderID = createShader(IOUtils.toString(fragmentStream), ARBFragmentShader.GL_FRAGMENT_SHADER_ARB);
            IOUtils.closeQuietly(fragmentStream);
        }catch(final Exception e) {
            e.printStackTrace();
            return;
        }

        if(vertexShaderID == 0 || fragmentShaderID == 0)
            return;

        program = ARBShaderObjects.glCreateProgramObjectARB();

        if(program == 0)
            return;

        ARBShaderObjects.glAttachObjectARB(program, vertexShaderID);
        ARBShaderObjects.glAttachObjectARB(program, fragmentShaderID);

        ARBShaderObjects.glLinkProgramARB(program);
        ARBShaderObjects.glValidateProgramARB(program);

        LOGGER.info("[Shader] Successfully loaded: " + fragmentShader);
    }

    public Shader(final File fragmentShader) throws IOException {
        int vertexShaderID, fragmentShaderID;

        final InputStream vertexStream = getClass().getResourceAsStream("/assets/minecraft/liquidbounce/shader/vertex.vert");
        vertexShaderID = createShader(IOUtils.toString(vertexStream), ARBVertexShader.GL_VERTEX_SHADER_ARB);
        IOUtils.closeQuietly(vertexStream);

        final InputStream fragmentStream = Files.newInputStream(fragmentShader.toPath());
        fragmentShaderID = createShader(IOUtils.toString(fragmentStream), ARBFragmentShader.GL_FRAGMENT_SHADER_ARB);
        IOUtils.closeQuietly(fragmentStream);

        if(vertexShaderID == 0 || fragmentShaderID == 0)
            return;

        program = ARBShaderObjects.glCreateProgramObjectARB();

        if(program == 0)
            return;

        ARBShaderObjects.glAttachObjectARB(program, vertexShaderID);
        ARBShaderObjects.glAttachObjectARB(program, fragmentShaderID);

        ARBShaderObjects.glLinkProgramARB(program);
        ARBShaderObjects.glValidateProgramARB(program);

        LOGGER.info("[Shader] Successfully loaded: " + fragmentShader.getName());
    }

    public void startShader() {
        glPushMatrix();
        glUseProgram(program);

        if(uniformsMap == null) {
            uniformsMap = new HashMap<>();
            setupUniforms();
        }

        updateUniforms();
    }

    public void stopShader() {
        glUseProgram(0);
        glPopMatrix();
    }

    public abstract void setupUniforms();

    public abstract void updateUniforms();

    private int createShader(String shaderSource, int shaderType) {
        int shader = 0;

        try {
            shader = ARBShaderObjects.glCreateShaderObjectARB(shaderType);

            if(shader == 0)
                return 0;

            ARBShaderObjects.glShaderSourceARB(shader, shaderSource);
            ARBShaderObjects.glCompileShaderARB(shader);

            if(ARBShaderObjects.glGetObjectParameteriARB(shader, ARBShaderObjects.GL_OBJECT_COMPILE_STATUS_ARB) == GL_FALSE)
                throw new RuntimeException("Error creating shader: " + getLogInfo(shader));

            return shader;
        }catch(final Exception e) {
            ARBShaderObjects.glDeleteObjectARB(shader);
            throw e;

        }
    }

    private String getLogInfo(int i) {
        return ARBShaderObjects.glGetInfoLogARB(i, ARBShaderObjects.glGetObjectParameteriARB(i, ARBShaderObjects.GL_OBJECT_INFO_LOG_LENGTH_ARB));
    }

    public void setUniform(final String uniformName, final int location) {
        uniformsMap.put(uniformName, location);
    }

    public void setupUniform(final String uniformName) {
        setUniform(uniformName, glGetUniformLocation(program, uniformName));
    }

    public int getUniform(final String uniformName) {
        return uniformsMap.get(uniformName);
    }

    public int getProgramId() {
        return program;
    }
}
