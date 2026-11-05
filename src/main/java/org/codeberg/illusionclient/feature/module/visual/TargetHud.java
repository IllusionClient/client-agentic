package org.codeberg.illusionclient.feature.module.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.model.PlayerModelPart;
import net.minecraft.resource.Identifier;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.player.AttackEvent;
import org.codeberg.illusionclient.event.render.Render2DEvent;
import org.codeberg.illusionclient.event.render.Render3DEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.api.ObsHidden;
import org.codeberg.illusionclient.feature.module.combat.AimAssist;
import org.codeberg.illusionclient.feature.module.combat.KillAura;
import org.codeberg.illusionclient.feature.ui.ClickGui;
import org.codeberg.illusionclient.feature.ui.Draw;
import org.codeberg.illusionclient.util.CombatUtils;
import org.codeberg.illusionclient.util.HiddenLayer;
import org.codeberg.illusionclient.util.render.EspRenderer;
import org.codeberg.illusionclient.util.render.Smoothed;

import java.awt.*;

@Description("Shows the player you're fighting and their health, on screen or above their head.")
@ObsHidden
@ModuleInfo(name = "Target HUD", category = Category.VISUAL)
public class TargetHud extends Module {
	private static final long LINGER_MS = 3000L;
	private static final double HEAD_CLEARANCE = 0.8D;
	private static final int HEAD = 24;
	private static final int PADDING = 6;
	private static final float TEXT_SIZE = 8.0F;
	private static final int BAR_HEIGHT = 4;
	private static final int BAR_GAP = 4;
	private static final int ARMOR = 0xFF4C9BFF;
	private static final int MIN_WIDTH = 100;
	private static final float RADIUS = 4.0F;
	private static final float HEAD_RADIUS = 3.0F;
	private static final float HEALTH_SPEED = 12.0F;
	private static final float SKIN_SIZE = 64.0F;

	private final Minecraft minecraft = Minecraft.getInstance();
	private final float[] screen = new float[2];
	private PlayerEntity lastHit;
	private long lastHitAt;
	private final Smoothed bar = new Smoothed();
	private final Smoothed armor = new Smoothed();
	private PlayerEntity shown, barOwner;

	@Description("Screen stays where you put it, Follow Target floats above the target in the world.")
	@Combo(value = "Position", options = {"Screen", "Follow Target"})
	public String position = "Screen";

	@Description("How far across the screen it sits, in percent. Shows a preview while this menu is open.")
	@Requires(value = "position", is = "Screen")
	@Slider(value = "X", min = 0.0F, max = 100.0F, step = 1.0F)
	public float x = 50.0F;

	@Description("How far down the screen it sits, in percent. Shows a preview while this menu is open.")
	@Requires(value = "position", is = "Screen")
	@Slider(value = "Y", min = 0.0F, max = 100.0F, step = 1.0F)
	public float y = 80.0F;

	@Override
	public void onDisable() {
		reset();
	}

	@Listen
	public void onWorldLoad(WorldLoadEvent event) {
		reset();
	}

	@Listen
	public void onAttack(AttackEvent event) {
		HitResult crosshair = minecraft.crosshairTarget;
		if (crosshair == null || !CombatUtils.isTarget(crosshair.entity)) return;

		lastHit = (PlayerEntity) crosshair.entity;
		lastHitAt = System.currentTimeMillis();
	}

	@Listen
	public void onRender3D(Render3DEvent event) {
		shown = minecraft.world == null || minecraft.player == null ? null : target();
		if (shown == null || !following()) return;

		EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
		float tickDelta = event.partialTicks();
		float x = (float) (shown.prevX + (shown.x - shown.prevX) * tickDelta - dispatcher.cameraX);
		float y = (float) (shown.prevY + (shown.y - shown.prevY) * tickDelta + shown.height + HEAD_CLEARANCE - dispatcher.cameraY);
		float z = (float) (shown.prevZ + (shown.z - shown.prevZ) * tickDelta - dispatcher.cameraZ);
		if (!EspRenderer.project(Frustum.getInstance().clipMatrix, x, y, z, minecraft.width / 2.0F, minecraft.height / 2.0F, screen)) shown = null;
	}

	@Listen
	public void onRender2D(Render2DEvent.Post event) {
		if (shown == null) return;

		Draw.accent();
		String name = shown.getName().replaceAll("§.", "");
		int width = Math.max(MIN_WIDTH, HEAD + PADDING * 3 + Draw.width(name, TEXT_SIZE));
		int height = HEAD + PADDING * 2;
		Window window = event.window();
		int left, top;
		if (following()) {
			left = Math.round(screen[0] / window.getScale()) - width / 2;
			top = Math.round(screen[1] / window.getScale()) - height;
		} else {
			left = Math.round((window.getWidth() - width) * x / 100.0F);
			top = Math.round((window.getHeight() - height) * y / 100.0F);
		}
		int contentLeft = left + HEAD + PADDING * 2;
		int right = left + width - PADDING;
		int barBottom = top + height - PADDING;
		float target = Math.clamp(shown.getHealth() / shown.getMaxHealth(), 0.0F, 1.0F);
		if (barOwner != shown) {
			barOwner = shown;
			bar.snap();
			armor.snap();
		}
		float fraction = bar.update(target, HEALTH_SPEED);

		HiddenLayer.begin();
		Draw.rounded(left, top, width, height, RADIUS, Draw.HUD_BACKGROUND);
		int healthTop = barBottom - BAR_HEIGHT * 2 - BAR_GAP;
		Draw.text(name, contentLeft, top + PADDING, Draw.TEXT, TEXT_SIZE);
		bar(contentLeft, right, healthTop, fraction, Color.HSBtoRGB(fraction / 3.0F, 0.9F, 0.9F));
		bar(contentLeft, right, barBottom - BAR_HEIGHT, armor.update(Math.clamp(shown.getArmorProtection() / 20.0F, 0.0F, 1.0F), HEALTH_SPEED), ARMOR);
		Draw.flush();

		Identifier skin = ((ClientPlayerEntity) shown).getSkinTextureLocation();
		Draw.roundedTexture(skin, left + PADDING, top + PADDING, HEAD, HEAD_RADIUS, 8 / SKIN_SIZE, 8 / SKIN_SIZE, 16 / SKIN_SIZE, 16 / SKIN_SIZE, 0xFFFFFFFF);
		if (shown.isModelPartVisible(PlayerModelPart.HAT)) {
			Draw.roundedTexture(skin, left + PADDING, top + PADDING, HEAD, HEAD_RADIUS, 40 / SKIN_SIZE, 8 / SKIN_SIZE, 48 / SKIN_SIZE, 16 / SKIN_SIZE, 0xFFFFFFFF);
		}
		Draw.flush();
		HiddenLayer.end();
	}

	private static void bar(int left, int right, int top, float fraction, int color) {
		float radius = BAR_HEIGHT / 2.0F;
		Draw.rounded(left, top, right - left, BAR_HEIGHT, radius, EspRenderer.OUTLINE);
		int filled = Math.round((right - left) * fraction);
		if (filled > 0) Draw.rounded(left, top, filled, BAR_HEIGHT, radius, color);
	}

	private PlayerEntity target() {
		PlayerEntity aimed = IllusionClient.MODULES.get(KillAura.class).target();
		if (aimed == null) aimed = IllusionClient.MODULES.get(AimAssist.class).target();
		if (aimed != null) return aimed;
		if (lastHit != null && lastHit.isAlive() && System.currentTimeMillis() - lastHitAt < LINGER_MS) return lastHit;
		return ClickGui.isOpen() && !following() ? minecraft.player : null;
	}

	private boolean following() {
		return position.equals("Follow Target");
	}

	private void reset() {
		lastHit = null;
		shown = barOwner = null;
	}
}
