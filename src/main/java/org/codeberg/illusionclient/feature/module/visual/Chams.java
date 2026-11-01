package org.codeberg.illusionclient.feature.module.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.ColorPicker;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.api.ObsHidden;
import org.codeberg.illusionclient.feature.module.client.AntiBot;
import org.codeberg.illusionclient.feature.module.client.Teams;
import org.lwjgl.opengl.GL11;

import java.awt.*;

@Description("Renders players through walls. Skips teammates and bots.")
@ObsHidden(ObsHidden.Level.NO)
@ModuleInfo(name = "Chams", category = Category.VISUAL)
public class Chams extends Module {
	private static boolean rendering;

	@Description("Draws players in a flat color instead of their skin.")
	@Check("Colored")
	public boolean colored = false;

	@Description("The color players are drawn in.")
	@Requires("colored")
	@ColorPicker(value = "Color", alpha = true)
	public Color color = new Color(0x74C3FF);

	@Description("Draws players at full brightness, ignoring world light and shading.")
	@Check("Bright")
	public boolean bright = false;

	@Description("Also applies Colored and Bright to your first person hand.")
	@Check("Self")
	public boolean self = false;

	public static boolean shows(Entity entity) {
		return entity instanceof PlayerEntity player && IllusionClient.MODULES.get(Chams.class).isEnabled()
			&& !Teams.isTeammate(player) && !AntiBot.isBot(player);
	}

	public static boolean showsHand() {
		Chams chams = IllusionClient.MODULES.get(Chams.class);
		return chams.isEnabled() && chams.self;
	}

	public static boolean rendering() {
		return rendering;
	}

	public static void begin() {
		rendering = true;
		// maps the player into the nearest 1% of the depth range, so walls can't cover it but its own parts still sort correctly
		GL11.glDepthRange(0.0D, 0.01D);
		if (IllusionClient.MODULES.get(Chams.class).bright) fullBright();
	}

	public static void end(Entity entity, float tickDelta) {
		if (IllusionClient.MODULES.get(Chams.class).bright) restoreLight(entity.getLightLevel(tickDelta));
		GL11.glDepthRange(0.0D, 1.0D);
		rendering = false;
	}

	public static void beginHand() {
		Chams chams = IllusionClient.MODULES.get(Chams.class);
		if (chams.bright) fullBright();
		if (chams.colored) beginColor();
	}

	public static void endHand(ClientPlayerEntity player) {
		Chams chams = IllusionClient.MODULES.get(Chams.class);
		if (chams.colored) endColor();
		if (chams.bright) restoreLight(Minecraft.getInstance().world.getLightColor(new BlockPos(player.x, player.y + player.getEyeHeight(), player.z), 0));
	}

	public static boolean colorsModel() {
		return rendering && IllusionClient.MODULES.get(Chams.class).colored;
	}

	public static void beginColor() {
		Color color = IllusionClient.MODULES.get(Chams.class).color;
		GlStateManager.disableTexture();
		if (color.getAlpha() < 255) {
			GlStateManager.enableBlend();
			GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		}
		GlStateManager.color4f(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, color.getAlpha() / 255.0F);
	}

	public static void endColor() {
		GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
		if (IllusionClient.MODULES.get(Chams.class).color.getAlpha() < 255) GlStateManager.disableBlend();
		GlStateManager.enableTexture();
	}

	private static void fullBright() {
		GlStateManager.disableLighting();
		GLX.multiTexCoord2f(GLX.GL_TEXTURE1, 240.0F, 240.0F);
	}

	private static void restoreLight(int light) {
		GLX.multiTexCoord2f(GLX.GL_TEXTURE1, light & 0xFFFF, light >> 16);
		GlStateManager.enableLighting();
	}
}
