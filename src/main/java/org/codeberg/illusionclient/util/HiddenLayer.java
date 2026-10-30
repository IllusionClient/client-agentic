package org.codeberg.illusionclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.feature.module.client.ObsProof;
import org.codeberg.illusionclient.feature.ui.ClickGui;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

public final class HiddenLayer {
	private static final int[] MATRICES = {GL11.GL_PROJECTION, GL11.GL_MODELVIEW, GL11.GL_TEXTURE};
	private static final long SWAP_TIMEOUT = 1_000_000_000L;

	private static boolean active, drawing, pending;
	private static int framebuffer, texture, width, height;
	private static long pendingSince;

	private HiddenLayer() {
	}

	public static void setActive(boolean active) {
		HiddenLayer.active = active;
		if (active) pending = false;
	}

	public static boolean drawing() {
		return drawing;
	}

	public static void begin() {
		if (!active) return;
		if (!SwapHook.install()) {
			IllusionClient.MODULES.get(ObsProof.class).setEnabled(false);
			return;
		}
		if (pending && System.nanoTime() - pendingSince > SWAP_TIMEOUT) {
			IllusionClient.LOGGER.error("Buffer swap hook never fired, drawing hidden elements normally");
			IllusionClient.MODULES.get(ObsProof.class).setEnabled(false);
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (framebuffer == 0) {
			framebuffer = GL30.glGenFramebuffers();
			texture = GL11.glGenTextures();
		}

		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
		if (width != minecraft.width || height != minecraft.height) {
			width = minecraft.width;
			height = minecraft.height;

			GlStateManager.bindTexture(texture);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
			GlStateManager.bindTexture(0);
			GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
		}

		if (!pending) {
			pending = true;
			pendingSince = System.nanoTime();
			GlStateManager.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
			GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT);
		}

		drawing = true;
		GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
	}

	public static void end() {
		if (!drawing) return;
		drawing = false;

		GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
		if (GLX.useFbo()) Minecraft.getInstance().getRenderTarget().bindWrite(false);
		else GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
	}

	static void composite() {
		if (!pending) return;
		pending = false;
		if (IllusionClient.MODULES.get(ObsProof.class).hideInScreen && Minecraft.getInstance().screen != null && !ClickGui.isOpen()) return;

		try {
			draw();
		} catch (Throwable throwable) {
			active = false;
			IllusionClient.LOGGER.error("Failed to composite hidden layer, disabling it", throwable);
		}
	}

	private static void draw() {
		int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
		int unit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		int bound = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		GL20.glUseProgram(0);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		for (int matrix : MATRICES) {
			GL11.glMatrixMode(matrix);
			GL11.glPushMatrix();
			GL11.glLoadIdentity();
		}

		GL11.glViewport(0, 0, width, height);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_FOG);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_SCISSOR_TEST);
		GL11.glColorMask(true, true, true, true);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
		GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_REPLACE);

		GL11.glBegin(GL11.GL_QUADS);
		GL11.glTexCoord2f(0.0F, 0.0F);
		GL11.glVertex2f(-1.0F, -1.0F);
		GL11.glTexCoord2f(1.0F, 0.0F);
		GL11.glVertex2f(1.0F, -1.0F);
		GL11.glTexCoord2f(1.0F, 1.0F);
		GL11.glVertex2f(1.0F, 1.0F);
		GL11.glTexCoord2f(0.0F, 1.0F);
		GL11.glVertex2f(-1.0F, 1.0F);
		GL11.glEnd();

		for (int matrix : MATRICES) {
			GL11.glMatrixMode(matrix);
			GL11.glPopMatrix();
		}
		GL11.glPopAttrib();
		GL13.glActiveTexture(unit);
		GL20.glUseProgram(program);
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, bound);
	}
}
