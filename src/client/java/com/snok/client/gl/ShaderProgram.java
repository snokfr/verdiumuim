package com.snok.client.gl;

import com.snok.log.PerfLog;
import org.lwjgl.opengl.GL43;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL20.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20.glGetShaderInfoLog;

/**
 * Minimal GLSL program wrapper with debug-logged compile steps.
 */
public final class ShaderProgram implements AutoCloseable {
	private final int program;

	public ShaderProgram(String name, String vertSrc, String fragSrc) {
		PerfLog.info(PerfLog.Cat.SHADERS, "compiling %s: vertex stage", name);
		int vs = compile(name, GL_VERTEX_SHADER, vertSrc);
		PerfLog.info(PerfLog.Cat.SHADERS, "compiling %s: fragment stage", name);
		int fs = compile(name, GL_FRAGMENT_SHADER, fragSrc);

		PerfLog.info(PerfLog.Cat.SHADERS, "linking %s", name);
		program = glCreateProgram();
		glAttachShader(program, vs);
		glAttachShader(program, fs);
		glLinkProgram(program);
		if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
			String log = glGetProgramInfoLog(program);
			glDeleteProgram(program);
			throw new IllegalStateException("Link failed for " + name + ": " + log);
		}
		glDeleteShader(vs);
		glDeleteShader(fs);
		PerfLog.info(PerfLog.Cat.SHADERS, "%s linked ok (program %d)", name, program);
	}

	private static int compile(String name, int type, String src) {
		int shader = glCreateShader(type);
		glShaderSource(shader, src);
		glCompileShader(shader);
		if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
			String log = glGetShaderInfoLog(shader);
			glDeleteShader(shader);
			throw new IllegalStateException("Compile failed for " + name + " (" + typeName(type) + "): " + log);
		}
		return shader;
	}

	private static String typeName(int type) {
		return type == GL_VERTEX_SHADER ? "vert" : "frag";
	}

	public void use() {
		glUseProgram(program);
	}

	public int uniform(String name) {
		return glGetUniformLocation(program, name);
	}

	public void setMat4(String name, FloatBuffer m) {
		glUniformMatrix4fv(uniform(name), false, m);
	}

	public void set3f(String name, float x, float y, float z) {
		glUniform3f(uniform(name), x, y, z);
	}

	public void set1f(String name, float v) {
		glUniform1f(uniform(name), v);
	}

	public void set1i(String name, int v) {
		glUniform1i(uniform(name), v);
	}

	@Override
	public void close() {
		glDeleteProgram(program);
	}
}
