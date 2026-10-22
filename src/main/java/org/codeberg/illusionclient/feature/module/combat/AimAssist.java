package org.codeberg.illusionclient.feature.module.combat;

import io.github.nevalackin.radbus.Listen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.Render3DEvent;
import org.codeberg.illusionclient.event.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.RangeSlider;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.util.CombatUtils;
import org.codeberg.illusionclient.util.NoiseUtils;
import org.codeberg.illusionclient.util.RotationUtils;

@ModuleInfo(name = "Aim Assist", category = Category.COMBAT)
@Description("Moves your crosshair onto players.")
public class AimAssist extends Module {
	private static final float PREDICT_TICKS = 3.0F;
	private static final long CLICK_WINDOW = 300L;
	private static final float RESIST_LIMIT = 15.0F;
	private static final float RESIST_DECAY = 30.0F;
	private static final long RESIST_COOLDOWN = 1000L;
	private static final double CLOSE_RANGE = 3.5D;
	private static final double NOISE_PER_DEGREE = 0.03D;
	private static final double TARGETING_MARGIN = 0.1D;
	private static final double EDGE_INSET = 0.05D;
	private final Minecraft minecraft = Minecraft.getInstance();

	@Description("Regular eases in, Linear is steady, Lock-on snaps.")
	@Combo(value = "Mode", options = {"Regular", "Linear", "Lock-on"})
	public String mode = "Regular";

	@Description("Horizontal turn speed.")
	@Slider(value = "Horizontal Speed", min = 0.0F, max = 100.0F)
	public float horizontalSpeed = 30.0F;

	@Description("Vertical turn speed.")
	@Slider(value = "Vertical Speed", min = 0.0F, max = 100.0F)
	public float verticalSpeed = 15.0F;

	@Description("Pulls horizontal aim toward your crosshair. 0 is center.")
	@Slider(value = "Horizontal Multipoint", min = 0.0F, max = 100.0F)
	public float horizontalMultipoint = 0.0F;

	@Description("Pulls vertical aim toward your crosshair. 0 is eye height.")
	@Slider(value = "Vertical Multipoint", min = 0.0F, max = 100.0F)
	public float verticalMultipoint = 0.0F;

	@Description("Aims ahead of moving targets.")
	@Slider(value = "Predict", min = 0.0F, max = 100.0F)
	public float predict = 0.0F;

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

	@Description("Which target to prefer.")
	@Requires(value = "targetMode", is = "Switch")
	@Combo(value = "Sort By", options = {"Distance", "Health", "Hurt time", "Aim angle"})
	public String sortBy = "Distance";

	@Description("Only aims at players within this distance.")
	@RangeSlider(value = "Range", min = 0.0F, max = 8.0F)
	public float[] range = {0.0F, 4.5F};

	@Description("Aims within the max angle, stops inside the min angle.")
	@RangeSlider(value = "FOV", min = 0.0F, max = 360.0F)
	public float[] fov = {0.0F, 90.0F};

	@Description("Only aims while you are sprinting.")
	@Check("Sprinting")
	public boolean sprinting = false;

	@Description("Only aims while you are clicking.")
	@Check("Mouse Pressed")
	public boolean mousePressed = true;

	@Description("Only aims while you move your mouse, and never further than you do.")
	@Check("Mouse Moved")
	public boolean mouseMoved = false;

	@Description("Drops the target briefly when you aim away.")
	@Check("Stop When Resisting")
	public boolean resisting = false;

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
	private PlayerEntity resisted;
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

	private static float step(float diff, float speed, float delta, String mode) {
		return switch (mode) {
			case "Lock-on" -> diff;
			case "Linear" -> {
				float limit = speed * 6.0F * delta * Math.min(1.0F, 0.4F + Math.abs(diff) / 15.0F);
				yield Math.clamp(diff, -limit, limit);
			}
			default -> diff * (1.0F - (float) Math.pow(1.0F - Math.min(1.0F, speed / 100.0F), delta * 20.0F));
		};
	}

	@Override
	public String details() {
		return mode;
	}

	@Override
	public void onDisable() {
		target = resisted = null;
	}

	@Listen
	public void onWorldLoad(WorldLoadEvent event) {
		target = resisted = null;
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

		float[] goal = RotationUtils.rotationTo(aimPoint(event.partialTicks()));
		float yawDiff = MathHelper.wrapDegrees(goal[0] - player.yaw);
		float pitchDiff = goal[1] - player.pitch;

		float scale = RotationUtils.mouseScale();
		float gcd = scale * 0.15F;
		int mouseX = minecraft.mouse.x;
		int mouseY = -minecraft.mouse.y;

		if (resisting && resisted(mouseX * gcd, mouseY * gcd, yawDiff, pitchDiff, delta)) return;

		yawDiff = shrink(yawDiff, fov[0] / 2.0F);
		pitchDiff = shrink(pitchDiff, fov[0] / 2.0F);
		if (yawDiff == 0.0F && pitchDiff == 0.0F) return;

		boolean lockOn = mode.equals("Lock-on");
		if (!lockOn) pitchDiff *= Math.max(0.0F, 1.0F - Math.abs(yawDiff) / NoiseUtils.wander(40.0F, 120.0F, 5.0F, 0));

		float drift = NoiseUtils.wander(0.95F, 1.05F, 60.0F, 1);
		float yawStep = step(yawDiff, horizontalSpeed * drift * NoiseUtils.wander(0.9F, 1.1F, 1.0F, 2), delta, mode);
		float pitchStep = step(pitchDiff, verticalSpeed * drift * NoiseUtils.wander(0.9F, 1.1F, 1.0F, 3), delta, mode);

		float yawTotal = yawStep + yawRemainder;
		yawRemainder = yawTotal % gcd;
		int yawCounts = RotationUtils.mouseCounts(yawTotal, yawDiff, gcd);
		if (mouseMoved) yawCounts = follow(yawCounts, mouseX);

		if (!lockOn && yawCounts != 0) pitchStep += Math.abs(yawCounts) * gcd * NoiseUtils.wander(-0.1F, 0.1F, 1.0F, 4);
		float pitchTotal = pitchStep + pitchRemainder;
		pitchRemainder = pitchTotal % gcd;
		int pitchCounts = RotationUtils.mouseCounts(pitchTotal, pitchDiff, gcd);
		if (mouseMoved) pitchCounts = follow(pitchCounts, mouseY);
		if (yawCounts == 0 && pitchCounts == 0) return;

		player.updateLocalPlayerCamera(yawCounts * scale, -pitchCounts * scale);
		noiseSeed += (Math.abs(yawCounts) + Math.abs(pitchCounts)) * gcd * NOISE_PER_DEGREE;
	}

	private boolean resisted(float mouseYaw, float mousePitch, float yawDiff, float pitchDiff, float delta) {
		float length = (float) Math.hypot(yawDiff, pitchDiff);
		float away = length == 0.0F ? 0.0F : -(mouseYaw * yawDiff + mousePitch * pitchDiff) / length;
		resist = Math.max(0.0F, resist + Math.max(0.0F, away) - RESIST_DECAY * delta);
		if (resist < RESIST_LIMIT) return false;

		resisted = target;
		resistedUntil = System.currentTimeMillis() + RESIST_COOLDOWN;
		target = null;
		yawRemainder = pitchRemainder = resist = 0.0F;
		return true;
	}

	private PlayerEntity findTarget(float yaw, float pitch) {
		if (!active()) return target = null;

		boolean single = targetMode.equals("Single");
		if (single && target != null && valid(target, yaw, pitch, increasedFov)) return target;

		PlayerEntity hovered = CombatUtils.best(player -> valid(player, yaw, pitch, true) && CombatUtils.isHovered(player, range[1], TARGETING_MARGIN), CombatUtils::distanceTo);
		if (hovered != null) return target = hovered;

		String sort = single ? "Aim angle" : sortBy;
		return target = CombatUtils.best(player -> valid(player, yaw, pitch, false), player -> score(player, sort, yaw, pitch));
	}

	private boolean active() {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || minecraft.world == null || minecraft.screen != null || !player.isAlive()) return false;
		if (sprinting && !player.isSprinting()) return false;
		if (mousePressed && System.currentTimeMillis() - lastClick > CLICK_WINDOW) return false;
		if (breakingBlocks && minecraft.interactionManager.isMiningBlock()) return false;
		return Weapons.isWeapons(player.getItemInHand());
	}

	private boolean valid(PlayerEntity player, float yaw, float pitch, boolean anyFov) {
		if (!player.isAlive() || !CombatUtils.isTarget(player)) return false;
		if (ignoreInvisibles && player.isInvisible()) return false;
		if (player == resisted && System.currentTimeMillis() < resistedUntil) return false;

		double distance = CombatUtils.distanceTo(player);
		if (distance < range[0] || distance > range[1]) return false;
		if (!anyFov && RotationUtils.angleTo(player, yaw, pitch) * 2.0D > fov[1]) return false;
		return !notBehindBlocks || minecraft.player.canSee(player);
	}

	private double score(PlayerEntity player, String sort, float yaw, float pitch) {
		return switch (sort) {
			case "Health" -> player.getHealth();
			case "Hurt time" -> player.damagedTimer;
			case "Aim angle" -> RotationUtils.angleTo(player, yaw, pitch);
			default -> CombatUtils.distanceTo(player);
		};
	}

	private Vec3d aimPoint(float tickDelta) {
		Box box = CombatUtils.predictedShape(target, tickDelta, predict / 100.0F * PREDICT_TICKS);
		Vec3d eye = minecraft.player.getEyePosition(1.0F);
		float near = (float) Math.min(1.0D, CombatUtils.distanceTo(box) / CLOSE_RANGE);
		float spread = randomization / 100.0F * (0.1F + 0.9F * near);
		float multipoint = horizontalMultipoint * near;
		double x = axis(eye.x, (box.minX + box.maxX) / 2.0D, box.minX, box.maxX, multipoint, spread * 0.4F, NoiseUtils.noise(noiseSeed));
		double y = axis(eye.y, box.minY + target.getEyeHeight(), box.minY, box.maxY, verticalMultipoint, spread * 0.3F, NoiseUtils.noise(noiseSeed + 10000.0D));
		double z = axis(eye.z, (box.minZ + box.maxZ) / 2.0D, box.minZ, box.maxZ, multipoint, spread * 0.4F, NoiseUtils.noise(noiseSeed + 20000.0D));
		return new Vec3d(x, y, z);
	}

	private static double axis(double eye, double center, double min, double max, float multipoint, float spread, float noise) {
		double size = max - min;
		double inset = Math.min(EDGE_INSET, size / 2.0D);
		double reach = Math.min(size * spread, size / 2.0D - inset);
		double closest = Math.clamp(eye, min + inset + reach, max - inset - reach);
		double point = center + (closest - center) * multipoint / 100.0F + reach * noise;
		return Math.clamp(point, min + inset, max - inset);
	}
}
