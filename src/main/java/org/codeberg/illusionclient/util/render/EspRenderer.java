package org.codeberg.illusionclient.util.render;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.FrustumCuller;
import net.minecraft.client.render.FrustumData;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.BufferUploader;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.util.math.Box;
import org.codeberg.illusionclient.event.render.Render2DEvent;
import org.codeberg.illusionclient.feature.ui.Draw;
import org.codeberg.illusionclient.util.HiddenLayer;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.List;

public final class EspRenderer<T> {
	public static final int OUTLINE = 0x96000000;
	private static final int FILL_ALPHA = 0x50;
	private static final int BOX_VERTICES = 24;
	private static final BufferUploader UPLOADER = new BufferUploader();
	private static final BufferBuilder FACES = new BufferBuilder(16384);
	private static final BufferBuilder LINES = new BufferBuilder(1024);

	private final List<T> targets = new ObjectArrayList<>();
	private final float[] screen = new float[2];
	private float[] bounds = new float[64];
	private FrustumCuller frustum;
	private float[] clip;
	private double cameraX, cameraY, cameraZ;
	private float halfWidth, halfHeight;
	private boolean drawing3D;

	@FunctionalInterface
	public interface Painter<T> {
		void paint(T target, int left, int top, int right, int bottom);
	}

	public static void outline(float left, float top, float right, float bottom, float thickness, int color) {
		thickness = Math.min(thickness, Math.min(right - left, bottom - top) / 2.0F);

		Draw.fill(left, top, left + thickness, bottom, color);
		Draw.fill(right - thickness, top, right, bottom, color);
		Draw.fill(left + thickness, top, right - thickness, top + thickness, color);
		Draw.fill(left + thickness, bottom - thickness, right - thickness, bottom, color);
	}

	public static void box(int left, int top, int right, int bottom, int color) {
		if (instanced()) {
			InstancedBoxes.rect(left, top, right, bottom, color);
			return;
		}

		outline(left, top, right, bottom, 3.0F, OUTLINE);
		outline(left + 1, top + 1, right - 1, bottom - 1, 1.0F, color);
	}

	public static boolean project(float[] clip, float x, float y, float z, float halfWidth, float halfHeight, float[] screen) {
		float w = clip[3] * x + clip[7] * y + clip[11] * z + clip[15];
		if (w <= 0.0F) return false;

		float depth = (clip[2] * x + clip[6] * y + clip[10] * z + clip[14]) / w;
		if (depth < -1.0F || depth >= 1.0F) return false;

		screen[0] = ((clip[0] * x + clip[4] * y + clip[8] * z + clip[12]) / w + 1.0F) * halfWidth;
		screen[1] = (1.0F - (clip[1] * x + clip[5] * y + clip[9] * z + clip[13]) / w) * halfHeight;
		return true;
	}

	public void begin() {
		targets.clear();

		Minecraft minecraft = Minecraft.getInstance();
		EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
		cameraX = dispatcher.cameraX;
		cameraY = dispatcher.cameraY;
		cameraZ = dispatcher.cameraZ;

		FrustumData data = Frustum.getInstance();
		if (frustum == null) frustum = new FrustumCuller(data);
		frustum.prepare(cameraX, cameraY, cameraZ);
		clip = data.clipMatrix;
		halfWidth = minecraft.width / 2.0F;
		halfHeight = minecraft.height / 2.0F;
	}

	public void clear() {
		targets.clear();
	}

	public void add(T target, Box box) {
		if (!frustum.isVisible(box)) return;

		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -1.0F, maxY = -1.0F;
		for (int corner = 0; corner < 8; corner++) {
			float x = (float) (((corner & 1) == 0 ? box.minX : box.maxX) - cameraX);
			float y = (float) (((corner & 2) == 0 ? box.minY : box.maxY) - cameraY);
			float z = (float) (((corner & 4) == 0 ? box.minZ : box.maxZ) - cameraZ);
			if (!project(clip, x, y, z, halfWidth, halfHeight, screen)) continue;

			minX = Math.min(minX, screen[0]);
			minY = Math.min(minY, screen[1]);
			maxX = Math.max(maxX, screen[0]);
			maxY = Math.max(maxY, screen[1]);
		}
		if (maxX < minX) return;

		int index = targets.size() * 4;
		if (index == bounds.length) bounds = Arrays.copyOf(bounds, index * 2);
		bounds[index] = minX;
		bounds[index + 1] = minY;
		bounds[index + 2] = maxX;
		bounds[index + 3] = maxY;
		targets.add(target);
	}

	public void render2D(Render2DEvent event, Painter<T> painter) {
		if (targets.isEmpty()) return;

		HiddenLayer.begin();
		GlStateManager.depthMask(false);
		GlStateManager.pushMatrix();
		double pixel = 1.0D / event.window().getScale();
		GlStateManager.scaled(pixel, pixel, 1.0D);

		for (int i = 0; i < targets.size(); i++) {
			painter.paint(targets.get(i), Math.round(bounds[i * 4]), Math.round(bounds[i * 4 + 1]), Math.round(bounds[i * 4 + 2]), Math.round(bounds[i * 4 + 3]));
		}
		Draw.flush();
		if (InstancedBoxes.hasRects()) {
			GlStateManager.disableTexture();
			GlStateManager.enableBlend();
			GlStateManager.disableAlphaTest();
			GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
			InstancedBoxes.drawRects();
			GlStateManager.enableAlphaTest();
			GlStateManager.enableTexture();
		}

		GlStateManager.popMatrix();
		GlStateManager.depthMask(true);
		GlStateManager.enableBlend();
		HiddenLayer.end();
	}

	public void box3D(Box box, int color) {
		if (!frustum.isVisible(box)) return;

		int fill = color & 0xFFFFFF | FILL_ALPHA << 24;
		if (instanced()) {
			drawing3D = true;
			InstancedBoxes.cube((float) (box.minX - cameraX), (float) (box.minY - cameraY), (float) (box.minZ - cameraZ),
				(float) (box.maxX - cameraX), (float) (box.maxY - cameraY), (float) (box.maxZ - cameraZ), fill);
			return;
		}

		if (drawing3D && (FACES.getVertexCount() + BOX_VERTICES) * FACES.getFormat().getVertexSize() > FACES.getBuffer().capacity()) end3D();
		if (!drawing3D) {
			drawing3D = true;
			FACES.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR);
		}

		double minX = box.minX - cameraX, minY = box.minY - cameraY, minZ = box.minZ - cameraZ;
		double maxX = box.maxX - cameraX, maxY = box.maxY - cameraY, maxZ = box.maxZ - cameraZ;

		face(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, fill);
		face(minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, fill);
		face(minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, fill);
		face(minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, fill);
		face(minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, fill);
		face(maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, fill);
	}

	public void end3D() {
		if (!drawing3D) return;
		drawing3D = false;

		if (instanced()) {
			boolean fog = prepare();
			HiddenLayer.begin();
			InstancedBoxes.drawCubes();
			HiddenLayer.end();
			restore(fog);
		} else {
			draw(FACES);
		}
	}

	public void outline3D(Box box, int color, float width) {
		if (!frustum.isVisible(box)) return;

		double minX = box.minX - cameraX, minY = box.minY - cameraY, minZ = box.minZ - cameraZ;
		double maxX = box.maxX - cameraX, maxY = box.maxY - cameraY, maxZ = box.maxZ - cameraZ;
		LINES.begin(GL11.GL_LINES, DefaultVertexFormat.POSITION_COLOR);
		for (int edge = 0; edge < 4; edge++) {
			double x = (edge & 1) == 0 ? minX : maxX;
			double z = (edge & 2) == 0 ? minZ : maxZ;
			vertex(LINES, x, minY, z, color);
			vertex(LINES, x, maxY, z, color);
			vertex(LINES, minX, (edge & 1) == 0 ? minY : maxY, z, color);
			vertex(LINES, maxX, (edge & 1) == 0 ? minY : maxY, z, color);
			vertex(LINES, x, (edge & 2) == 0 ? minY : maxY, minZ, color);
			vertex(LINES, x, (edge & 2) == 0 ? minY : maxY, maxZ, color);
		}

		GL11.glLineWidth(width);
		draw(LINES);
		GL11.glLineWidth(1.0F);
	}

	private static boolean instanced() {
		return InstancedBoxes.available();
	}

	private static void draw(BufferBuilder buffer) {
		boolean fog = prepare();
		HiddenLayer.begin();
		buffer.end();
		UPLOADER.end(buffer);
		HiddenLayer.end();
		restore(fog);
	}

	private static boolean prepare() {
		boolean fog = GL11.glIsEnabled(GL11.GL_FOG);
		GlStateManager.disableFog();
		GlStateManager.disableTexture();
		GlStateManager.disableCull();
		GlStateManager.disableDepthTest();
		GlStateManager.depthMask(false);
		GlStateManager.enableBlend();
		GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
		return fog;
	}

	private static void restore(boolean fog) {
		GlStateManager.disableBlend();
		GlStateManager.depthMask(true);
		GlStateManager.enableDepthTest();
		GlStateManager.enableCull();
		GlStateManager.enableTexture();
		if (fog) GlStateManager.enableFog();
	}

	private static void face(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, int color) {
		vertex(FACES, x1, y1, z1, color);
		vertex(FACES, x2, y2, z2, color);
		vertex(FACES, x3, y3, z3, color);
		vertex(FACES, x4, y4, z4, color);
	}

	private static void vertex(BufferBuilder buffer, double x, double y, double z, int color) {
		buffer.vertex(x, y, z)
			.color(color >> 16 & 255, color >> 8 & 255, color & 255, color >>> 24)
			.nextVertex();
	}
}
