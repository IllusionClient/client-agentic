package org.codeberg.illusionclient.util.render;

import net.minecraft.client.Minecraft;
import net.minecraft.resource.Identifier;
import org.codeberg.illusionclient.IllusionClient;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBDrawInstanced;
import org.lwjgl.opengl.ARBInstancedArrays;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

final class InstancedBoxes {
	private static final int CUBE_STRIDE = 28;
	private static final int RECT_STRIDE = 20;
	private static final int CUBE_VERTICES = 36;

	private static int state;
	private static boolean arb;
	private static int cubeProgram, rectProgram, cubeMesh, rectMesh, instanceBuffer;
	private static ByteBuffer cubes = BufferUtils.createByteBuffer(CUBE_STRIDE * 256);
	private static ByteBuffer rects = BufferUtils.createByteBuffer(RECT_STRIDE * 256);

	static boolean available() {
		if (state == 0) state = init() ? 1 : -1;
		return state == 1;
	}

	static void cube(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int color) {
		cubes = reserve(cubes, CUBE_STRIDE);
		cubes.putFloat(minX).putFloat(minY).putFloat(minZ).putFloat(maxX).putFloat(maxY).putFloat(maxZ);
		color(cubes, color);
	}

	static void rect(float left, float top, float right, float bottom, int color) {
		rects = reserve(rects, RECT_STRIDE);
		rects.putFloat(left).putFloat(top).putFloat(right).putFloat(bottom);
		color(rects, color);
	}

	static boolean hasRects() {
		return rects.position() > 0;
	}

	static void drawCubes() {
		int count = cubes.position() / CUBE_STRIDE;
		if (count == 0) return;

		upload(cubes);
		GL20.glUseProgram(cubeProgram);
		instanced(1, 3, GL11.GL_FLOAT, false, CUBE_STRIDE, 0);
		instanced(2, 3, GL11.GL_FLOAT, false, CUBE_STRIDE, 12);
		instanced(3, 4, GL11.GL_UNSIGNED_BYTE, true, CUBE_STRIDE, 24);
		mesh(cubeMesh, 3);
		drawInstanced(GL11.GL_TRIANGLES, CUBE_VERTICES, count);
		finish();
	}

	static void drawRects() {
		int count = rects.position() / RECT_STRIDE;
		if (count == 0) return;

		upload(rects);
		GL20.glUseProgram(rectProgram);
		instanced(1, 4, GL11.GL_FLOAT, false, RECT_STRIDE, 0);
		instanced(3, 4, GL11.GL_UNSIGNED_BYTE, true, RECT_STRIDE, 16);
		mesh(rectMesh, 2);
		drawInstanced(GL11.GL_TRIANGLE_STRIP, 4, count);
		finish();
	}

	private static boolean init() {
		ContextCapabilities capabilities = GLContext.getCapabilities();
		arb = !capabilities.OpenGL33;
		if (arb && !(capabilities.GL_ARB_draw_instanced && capabilities.GL_ARB_instanced_arrays)) return false;

		try {
			cubeProgram = program("esp_cube", "corner", "boxMin", "boxMax", "color");
			rectProgram = program("esp_rect", "corner", "rect", null, "color");
		} catch (IOException | IllegalStateException e) {
			IllusionClient.LOGGER.error("Instanced ESP shaders failed, falling back to batching", e);
			return false;
		}

		FloatBuffer cube = BufferUtils.createFloatBuffer(CUBE_VERTICES * 3);
		float[][] triangles = {{0, 0}, {1, 0}, {1, 1}, {0, 0}, {1, 1}, {0, 1}};
		for (int axis = 0; axis < 3; axis++) {
			for (int side = 0; side < 2; side++) {
				for (float[] corner : triangles) {
					float[] position = new float[3];
					position[axis] = side;
					position[(axis + 1) % 3] = corner[0];
					position[(axis + 2) % 3] = corner[1];
					cube.put(position);
				}
			}
		}
		FloatBuffer rect = BufferUtils.createFloatBuffer(8).put(new float[]{0, 0, 1, 0, 0, 1, 1, 1});

		cubeMesh = staticBuffer(cube.flip());
		rectMesh = staticBuffer(rect.flip());
		instanceBuffer = GL15.glGenBuffers();
		return true;
	}

	private static int program(String name, String... attributes) throws IOException {
		int program = GL20.glCreateProgram();
		GL20.glAttachShader(program, shader(GL20.GL_VERTEX_SHADER, name + ".vsh"));
		GL20.glAttachShader(program, shader(GL20.GL_FRAGMENT_SHADER, name + ".fsh"));
		for (int i = 0; i < attributes.length; i++) {
			if (attributes[i] != null) GL20.glBindAttribLocation(program, i, attributes[i]);
		}
		GL20.glLinkProgram(program);
		if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
			throw new IllegalStateException(GL20.glGetProgramInfoLog(program, 4096));
		return program;
	}

	private static int shader(int type, String file) throws IOException {
		String source;
		try (InputStream stream = Minecraft.getInstance().getResourceManager().getResource(new Identifier("illusion", "shaders/" + file)).asStream()) {
			source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}

		int shader = GL20.glCreateShader(type);
		GL20.glShaderSource(shader, source);
		GL20.glCompileShader(shader);
		if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
			throw new IllegalStateException(GL20.glGetShaderInfoLog(shader, 4096));
		return shader;
	}

	private static int staticBuffer(FloatBuffer data) {
		int buffer = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		return buffer;
	}

	private static void upload(ByteBuffer data) {
		data.flip();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceBuffer);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
		data.clear();
	}

	private static void instanced(int index, int size, int type, boolean normalized, int stride, int offset) {
		GL20.glEnableVertexAttribArray(index);
		GL20.glVertexAttribPointer(index, size, type, normalized, stride, offset);
		divisor(index, 1);
	}

	private static void drawInstanced(int mode, int vertices, int count) {
		if (arb) ARBDrawInstanced.glDrawArraysInstancedARB(mode, 0, vertices, count);
		else GL31.glDrawArraysInstanced(mode, 0, vertices, count);
	}

	private static void divisor(int index, int divisor) {
		if (arb) ARBInstancedArrays.glVertexAttribDivisorARB(index, divisor);
		else GL33.glVertexAttribDivisor(index, divisor);
	}

	private static void mesh(int buffer, int size) {
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
		GL20.glEnableVertexAttribArray(0);
		GL20.glVertexAttribPointer(0, size, GL11.GL_FLOAT, false, 0, 0);
	}

	private static void finish() {
		for (int index = 0; index < 4; index++) {
			divisor(index, 0);
			GL20.glDisableVertexAttribArray(index);
		}
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		GL20.glUseProgram(0);
	}

	private static ByteBuffer reserve(ByteBuffer buffer, int bytes) {
		if (buffer.remaining() >= bytes) return buffer;

		ByteBuffer grown = BufferUtils.createByteBuffer(Math.max(buffer.capacity() * 2, buffer.position() + bytes));
		return grown.put(buffer.flip());
	}

	private static void color(ByteBuffer buffer, int color) {
		buffer.put((byte) (color >> 16)).put((byte) (color >> 8)).put((byte) color).put((byte) (color >>> 24));
	}
}
