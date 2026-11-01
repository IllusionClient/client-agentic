package org.codeberg.illusionclient.feature.module.visual;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.render.Render2DEvent;
import org.codeberg.illusionclient.event.render.Render3DEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.ColorPicker;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.RangeSlider;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Section;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.api.ObsHidden;
import org.codeberg.illusionclient.feature.module.client.AntiBot;
import org.codeberg.illusionclient.feature.module.client.Tags;
import org.codeberg.illusionclient.feature.module.client.Teams;
import org.codeberg.illusionclient.feature.ui.Draw;
import org.codeberg.illusionclient.util.HiddenLayer;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.Comparator;
import java.util.List;

@Description("Points at other players around your crosshair, even ones outside your view.")
@ObsHidden
@ModuleInfo(name = "Pointers", category = Category.VISUAL)
public class Pointers extends Module {
	private final Minecraft minecraft = Minecraft.getInstance();
	private final List<PlayerEntity> targets = new ObjectArrayList<>();
	private float tickDelta;

	@Description("Players farther than this get no pointer.")
	@Slider(value = "Range", min = 8.0F, max = 256.0F, step = 1.0F)
	public float range = 128.0F;

	@Description("Players within this angle of where you look get no pointer. 0 points at everyone.")
	@Slider(value = "Ignore Within FOV", min = 0.0F, max = 360.0F, step = 1.0F)
	public float ignoreFov = 90.0F;

	@Description("Skips teammates and friends.")
	@Check("Hide Friendlies")
	public boolean hideFriendlies = false;

	@Section("Display")
	@Description("2D keeps the ring round, 3D lays it flat like the ground for a sense of depth.")
	@Combo(value = "Style", options = {"2D", "3D"})
	public String style = "2D";

	@Description("Accent uses the client accent for enemies and a muted tone for teammates and friends, Name Tag uses the player's name tag color, Manual uses fixed colors.")
	@Combo(value = "Colors", options = {"Accent", "Name Tag", "Manual"})
	public String colors = "Accent";

	@Description("The color of enemies.")
	@Requires(value = "colors", is = "Manual")
	@ColorPicker("Enemy")
	public Color enemy = new Color(0xF44336);

	@Description("The color of teammates and friends.")
	@Requires(value = "colors", is = "Manual")
	@ColorPicker("Friendly")
	public Color friendly = new Color(0x74C3FF);

	@Description("Distances counted as near and far. Farther pointers fade and shrink.")
	@RangeSlider(value = "Transition Between", min = 0.0F, max = 256.0F)
	public float[] transition = {10.0F, 50.0F};

	@Description("The size of the pointers.")
	@Slider(value = "Scale", min = 0.5F, max = 3.0F)
	public float scale = 1.0F;

	@Description("How far from the crosshair the pointers sit.")
	@Slider(value = "Radius", min = 20.0F, max = 150.0F, step = 1.0F)
	public float radius = 50.0F;

	@Description("Pushes pointers for faraway players further from the crosshair.")
	@Check("Distance-Based Radius")
	public boolean distanceRadius = false;

	@Listen
	public void onRender3D(Render3DEvent event) {
		tickDelta = event.partialTicks();
	}

	@Listen
	public void onRender2D(Render2DEvent.Pre event) {
		LocalClientPlayerEntity self = minecraft.player;
		if (self == null || minecraft.world == null) return;

		double selfX = self.prevX + (self.x - self.prevX) * tickDelta;
		double selfZ = self.prevZ + (self.z - self.prevZ) * tickDelta;
		float yaw = self.lastYaw + (self.yaw - self.lastYaw) * tickDelta;
		float centerX = event.window().getWidth() / 2.0F;
		float centerY = event.window().getHeight() / 2.0F;
		float squash = style.equals("3D") ? 0.5F : 1.0F;
		if (colors.equals("Accent")) Draw.accent();

		targets.clear();
		targets.addAll(minecraft.world.players);
		targets.sort(Comparator.comparingDouble(player -> -self.distanceTo(player)));
		boolean drawn = false;
		for (PlayerEntity player : targets) {
			if (player == self || !player.isAlive() || AntiBot.isBot(player)) continue;

			boolean friend = Teams.isTeammate(player) || Tags.isIgnored(player);
			if (friend && hideFriendlies) continue;

			double dx = player.prevX + (player.x - player.prevX) * tickDelta - selfX;
			double dz = player.prevZ + (player.z - player.prevZ) * tickDelta - selfZ;
			float distance = self.distanceTo(player);
			if (distance > range) continue;

			double angle = Math.atan2(dz, dx) - Math.toRadians(yaw + 90.0F);
			if (Math.abs(MathHelper.wrapDegrees((float) Math.toDegrees(angle))) < ignoreFov / 2.0F) continue;

			if (!drawn) {
				HiddenLayer.begin();
				drawn = true;
			}
			float t = Math.clamp((distance - transition[0]) / Math.max(transition[1] - transition[0], 0.01F), 0.0F, 1.0F);
			float offset = distanceRadius ? radius * (1.0F + t) : radius;
			float sin = (float) Math.sin(angle);
			float cos = (float) -Math.cos(angle) * squash;
			float length = (float) Math.hypot(sin, cos);
			float size = 6.0F * scale * (1.0F - 0.3F * t);
			Draw.pointer(centerX + sin * offset, centerY + cos * offset, sin / length, cos / length, size, fade(color(player, friend), 1.0F - 0.65F * t));
		}
		targets.clear();
		if (!drawn) return;

		Draw.flush();
		GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
		GlStateManager.enableBlend();
		HiddenLayer.end();
	}

	private int color(PlayerEntity player, boolean friend) {
		return switch (colors) {
			case "Name Tag" -> Esp.teamColor(player, Draw.TEXT);
			case "Manual" -> (friend ? friendly : enemy).getRGB();
			default -> friend ? Draw.MUTED : Draw.PRIMARY;
		};
	}

	private static int fade(int color, float opacity) {
		return Math.round((color >>> 24) * opacity) << 24 | color & 0xFFFFFF;
	}
}
