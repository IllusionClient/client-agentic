package org.codeberg.illusionclient.feature.module.combat;

import org.codeberg.illusionclient.event.api.Listen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.render.Render3DEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.RangeSlider;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.client.Weapons;
import org.codeberg.illusionclient.util.CombatUtils;
import org.codeberg.illusionclient.util.RotationUtils;

@ModuleInfo(name = "Aim Assist", category = Category.COMBAT)
@Description("Moves your crosshair onto players.")
public class AimAssist extends Module {
	private static final float PREDICT_TICKS = 3.0F;
	private static final float RESIST_LIMIT = 15.0F;
	private static final float RESIST_DECAY = 30.0F;
	private static final long RESIST_COOLDOWN = 1000L;
	private static final double TARGETING_MARGIN = 0.1D;
	private final Minecraft minecraft = Minecraft.getInstance();

	@Description("Regular eases in, Linear is steady, Lock-on snaps.")
	@Combo(value = "Mode", options = {"Regular", "Linear", "Lock-on"})
	public String mode = "Regular";

	@Description("Horizontal turn speed.")
	@Slider(value = "Horizontal Speed", min = 0.0F, max = 100.0F)
	public float horizontalSpeed = 30.0F;

	@Description("Vertical turn speed.")
	@Slider(value = "Vertical Speed", min = 0.0F, max = 100.0F)
	public float verticalSpeed = 0.0F;

	@Description("Pulls horizontal aim toward your crosshair. 0 is center.")
	@Slider(value = "Horizontal Multipoint", min = 0.0F, max = 100.0F)
	public float horizontalMultipoint = 25.0F;

	@Description("Pulls vertical aim toward your crosshair. 0 is eye height.")
	@Slider(value = "Vertical Multipoint", min = 0.0F, max = 100.0F)
	public float verticalMultipoint = 0.0F;

	@Description("Aims ahead of moving targets.")
	@Slider(value = "Predict", min = 0.0F, max = 100.0F)
	public float predict = 15.0F;

	@Description("Wanders the aim point around the hitbox.")
	@Slider(value = "Randomization", min = 0.0F, max = 100.0F)
	public float randomization = 50.0F;

	@Description("Single keeps one target, Switch picks the best each frame.")
	@Combo(value = "Target Mode", options = {"Single", "Switch"})
	public String targetMode = "Single";

	@Description("Keeps the target after it leaves your FOV.")
	@Requires(value = "targetMode", is = "Single")
	@Check("Increased FOV While Locked")
	public boolean increasedFov = false;

	@Description("How long to wait for a lost target to come back before picking another.")
	@Slider(value = "Retarget Delay (ms)", min = 0.0F, max = 1000.0F)
	public float retargetDelay = 400.0F;

	@Description("Which target to prefer.")
	@Requires(value = "targetMode", is = "Switch")
	@Combo(value = "Sort By", options = {"Distance", "Health", "Hurt time", "Aim angle"})
	public String sortBy = "Distance";

	@Description("Only aims at players within this distance.")
	@RangeSlider(value = "Range", min = 0.0F, max = 8.0F)
	public float[] range = {0.0F, 8.0F};

	@Description("Aims within the max angle, stops inside the min angle.")
	@RangeSlider(value = "FOV", min = 0.0F, max = 360.0F)
	public float[] fov = {0.0F, 140.0F};

	@Description("Only aims while you are sprinting.")
	@Check("Sprinting")
	public boolean sprinting = false;

	@Description("Only aims while holding a weapon.")
	@Check("Weapons Only")
	public boolean weaponsOnly = false;

	@Description("Only aims while you are clicking.")
	@Check("Mouse Pressed")
	public boolean mousePressed = true;

	@Description("Keeps aiming this long after you let go. 0 stops right away.")
	@Requires("mousePressed")
	@Slider(value = "Click Window (ms)", min = 0.0F, max = 500.0F)
	public float clickWindow = 500.0F;

	@Description("Only aims while you move your mouse, and never further than you do.")
	@Check("Mouse Moved")
	public boolean mouseMoved = true;

	@Description("Drops the target briefly when you aim away.")
	@Check("Stop When Resisting")
	public boolean resisting = true;

	@Description("Stops aiming while you are mining a block.")
	@Check("Disable While Breaking Blocks")
	public boolean breakingBlocks = true;

	@Description("Skips invisible players.")
	@Check("Ignore Invisibles")
	public boolean ignoreInvisibles = true;

	@Description("Skips players you can't see through blocks.")
	@Check("Not Behind Blocks")
	public boolean notBehindBlocks = true;

	private PlayerEntity target;
	private long lastFrame;
	private long lastClick;
	private long lastSeen;
	private long resistedUntil;
	private float resist;
	private float yawRemainder;
	private float pitchRemainder;
	private double noiseSeed;

	private static float shrink(float diff, float deadzone) {
		return Math.copySign(Math.max(0.0F, Math.abs(diff) - deadzone), diff);
	}

	private static int follow(int counts, int mouse) {
		if (Integer.signum(counts) != Integer.signum(mouse)) return 0;
		return Integer.signum(counts) * Math.min(Math.abs(counts), Math.abs(mouse));
	}

	@Override
	public String details() {
		return mode;
	}

	public PlayerEntity target() {
		return target;
	}

	@Override
	public void onDisable() {
		target = null;
	}

	@Listen
	public void onWorldLoad(WorldLoadEvent event) {
		target = null;
	}

	@Listen
	public void onRender3D(Render3DEvent event) {
		long now = System.nanoTime();
		float delta = Math.min(0.1F, (now - lastFrame) / 1.0E9F);
		lastFrame = now;

		if (IllusionClient.INPUT.held(minecraft.options.attackKey)) lastClick = System.currentTimeMillis();

		LocalClientPlayerEntity player = minecraft.player;
		PlayerEntity previous = target;
		if (player == null || findTarget(player.yaw, player.pitch) == null) {
			yawRemainder = pitchRemainder = resist = 0.0F;
			return;
		}
		if (target != previous) resist = 0.0F;

		float[] goal = RotationUtils.rotationTo(RotationUtils.aimPoint(target,
			CombatUtils.predictedShape(target, event.partialTicks(), predict / 100.0F * PREDICT_TICKS),
			horizontalMultipoint, verticalMultipoint, randomization, noiseSeed));
		float yawDiff = MathHelper.wrapDegrees(goal[0] - player.yaw);
		float pitchDiff = goal[1] - player.pitch;

		float scale = RotationUtils.mouseScale();
		float gcd = scale * 0.15F;
		int mouseX = minecraft.mouse.x;
		int mouseY = -minecraft.mouse.y;

		float yield = 1.0F;
		if (resisting) {
			if (resisted(mouseX * gcd, mouseY * gcd, yawDiff, pitchDiff, delta)) return;
			yield = 1.0F - resist / RESIST_LIMIT;
		}

		yawDiff = shrink(yawDiff, fov[0] / 2.0F);
		pitchDiff = shrink(pitchDiff, fov[0] / 2.0F);
		if (yawDiff == 0.0F && pitchDiff == 0.0F) return;

		boolean lockOn = mode.equals("Lock-on");
		if (!lockOn) pitchDiff = RotationUtils.easePitch(pitchDiff, yawDiff);

		float[] steps = RotationUtils.aimSteps(yawDiff, pitchDiff, horizontalSpeed, verticalSpeed, delta, mode);
		float pitchStep = steps[1] * yield;

		float yawTotal = steps[0] * yield + yawRemainder;
		yawRemainder = yawTotal % gcd;
		int yawCounts = RotationUtils.mouseCounts(yawTotal, yawDiff, gcd);
		if (mouseMoved) yawCounts = follow(yawCounts, mouseX);

		if (!lockOn && yawCounts != 0) pitchStep += RotationUtils.pitchWobble(Math.abs(yawCounts) * gcd);
		float pitchTotal = pitchStep + pitchRemainder;
		pitchRemainder = pitchTotal % gcd;
		int pitchCounts = RotationUtils.mouseCounts(pitchTotal, pitchDiff, gcd);
		if (mouseMoved) pitchCounts = follow(pitchCounts, mouseY);
		if (yawCounts == 0 && pitchCounts == 0) return;

		player.updateLocalPlayerCamera(yawCounts * scale, -pitchCounts * scale);
		noiseSeed += (Math.abs(yawCounts) + Math.abs(pitchCounts)) * gcd * RotationUtils.NOISE_PER_DEGREE;
	}

	private boolean resisted(float mouseYaw, float mousePitch, float yawDiff, float pitchDiff, float delta) {
		float length = (float) Math.hypot(yawDiff, pitchDiff);
		float away = length == 0.0F ? 0.0F : -(mouseYaw * yawDiff + mousePitch * pitchDiff) / length;
		resist = Math.max(0.0F, resist + Math.max(0.0F, away) - RESIST_DECAY * delta);
		long millis = System.currentTimeMillis();
		if (resist >= RESIST_LIMIT) {
			resistedUntil = millis + RESIST_COOLDOWN;
			yawRemainder = pitchRemainder = resist = 0.0F;
		}
		return millis < resistedUntil;
	}

	private PlayerEntity findTarget(float yaw, float pitch) {
		if (!active()) return null;

		long millis = System.currentTimeMillis();
		boolean single = targetMode.equals("Single");
		boolean holding = target != null && valid(target, yaw, pitch, single && increasedFov);
		if (holding) lastSeen = millis;
		if (single && holding) return target;

		PlayerEntity next = CombatUtils.best(player -> valid(player, yaw, pitch, true) && CombatUtils.isHovered(player, range[1], TARGETING_MARGIN), CombatUtils::distanceTo);
		if (next == null) {
			if (!holding && target != null && target.isAlive() && millis - lastSeen < retargetDelay) return null;
			String sort = single ? "Aim angle" : sortBy;
			next = CombatUtils.best(player -> valid(player, yaw, pitch, false), player -> CombatUtils.score(player, sort, yaw, pitch));
		}
		if (next != null) {
			target = next;
			lastSeen = millis;
		}
		return next;
	}

	private boolean active() {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || minecraft.world == null || minecraft.screen != null || !player.isAlive()) return false;
		if (sprinting && !player.isSprinting()) return false;
		if (mousePressed && System.currentTimeMillis() - lastClick > clickWindow) return false;
		if (breakingBlocks && minecraft.interactionManager.isMiningBlock()) return false;
		return !weaponsOnly || Weapons.isHolding(player);
	}

	private boolean valid(PlayerEntity player, float yaw, float pitch, boolean anyFov) {
		if (!player.isAlive() || !CombatUtils.isTarget(player)) return false;
		if (ignoreInvisibles && player.isInvisible()) return false;

		double distance = CombatUtils.distanceTo(player);
		if (distance < range[0] || distance > range[1]) return false;
		if (!anyFov && RotationUtils.angleTo(player, yaw, pitch) * 2.0D > fov[1]) return false;
		return !notBehindBlocks || minecraft.player.canSee(player);
	}
}
