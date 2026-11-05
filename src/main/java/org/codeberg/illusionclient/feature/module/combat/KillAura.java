package org.codeberg.illusionclient.feature.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.monster.Monster;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.input.InputEvent;
import org.codeberg.illusionclient.event.player.PickEvent;
import org.codeberg.illusionclient.event.player.PlayerTickEvent;
import org.codeberg.illusionclient.event.render.Render3DEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Section;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.client.Weapons;
import org.codeberg.illusionclient.util.ClickPattern;
import org.codeberg.illusionclient.util.CombatUtils;
import org.codeberg.illusionclient.util.RotationUtils;
import org.codeberg.illusionclient.util.rotation.RotationManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@ModuleInfo(name = "Kill Aura", category = Category.COMBAT, aliases = {"SilentAura", "MultiAura"})
@Description("Aims at nearby targets without moving your camera and attacks them.")
public class KillAura extends Module {
	private static final float MAX_FRAME_MS = 100.0F;
	private static final float SECONDS_PER_TICK = MS_PER_TICK / 1000.0F;
	private static final float LEAD_TICKS = 1.0F;
	private static final float FULL_CIRCLE = 360.0F;
	private static final float RETURNED = 3.0F;
	private static final double TARGETING_MARGIN = 0.1D;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final ClickPattern pattern = new ClickPattern();
	private final Set<LivingEntity> visited = new HashSet<>();

	@Section("Targeting")
	@Description("Targets players.")
	@Check("Attack Players")
	public boolean attackPlayers = true;

	@Description("Targets hostile mobs.")
	@Check("Attack Mobs")
	public boolean attackMobs = false;

	@Description("Single keeps one target, Switch picks the best each tick.")
	@Combo(value = "Target Mode", options = {"Single", "Switch"})
	public String targetMode = "Switch";

	@Description("Stays on each target this long, then moves to the next one in attack range. 0 always picks the best.")
	@Requires(value = "targetMode", is = "Switch")
	@Slider(value = "Switch Delay (ms)", min = 0.0F, max = 1000.0F)
	public float switchDelay = 0.0F;

	@Description("Which target to prefer.")
	@Combo(value = "Sort By", options = {"Distance", "Health", "Hurt time", "Aim angle"})
	public String sortBy = "Distance";

	@Description("Starts aiming at targets within this distance.")
	@Slider(value = "Aim Range", min = 0.0F, max = 8.0F)
	public float aimRange = 4.5F;

	@Description("Attacks targets within this distance.")
	@Slider(value = "Attack Range", min = 0.0F, max = 6.0F)
	public float attackRange = 3.0F;

	@Description("Swings at the air within this distance when a hit can't land.")
	@Slider(value = "Swing Range", min = 0.0F, max = 6.0F)
	public float swingRange = 3.5F;

	@Description("Only targets within this angle of your camera.")
	@Slider(value = "FOV", min = 0.0F, max = FULL_CIRCLE)
	public float fov = FULL_CIRCLE;

	@Section("Aim")
	@Description("Regular eases in, Linear is steady, Lock-on snaps.")
	@Combo(value = "Mode", options = {"Regular", "Linear", "Lock-on"})
	public String mode = "Regular";

	@Description("Horizontal turn speed.")
	@Slider(value = "Horizontal Speed", min = 1.0F, max = 100.0F)
	public float horizontalSpeed = 60.0F;

	@Description("Vertical turn speed.")
	@Slider(value = "Vertical Speed", min = 1.0F, max = 100.0F)
	public float verticalSpeed = 40.0F;

	@Description("Pulls horizontal aim toward the closest edge of the hitbox. 0 is center.")
	@Slider(value = "Horizontal Multipoint", min = 0.0F, max = 100.0F)
	public float horizontalMultipoint = 0.0F;

	@Description("Pulls vertical aim toward the closest edge of the hitbox. 0 is eye height.")
	@Slider(value = "Vertical Multipoint", min = 0.0F, max = 100.0F)
	public float verticalMultipoint = 0.0F;

	@Description("Wanders the aim point around the hitbox.")
	@Slider(value = "Randomization", min = 0.0F, max = 100.0F)
	public float randomization = 50.0F;

	@Description("How your movement follows the rotation. Silent keeps you moving where you look, Strict moves you along the rotation like vanilla, Off ignores it.")
	@Combo(value = "Rotation Strafe", options = {"Silent", "Strict", "Off"})
	public String strafe = "Silent";

	@Section("Clicking")
	@Description("Jitter clicks evenly, Butterfly in bursts of two.")
	@Combo(value = "Click Pattern", options = {"Jitter", "Butterfly"})
	public String clickPattern = "Jitter";

	@Description("How many clicks per second to aim for.")
	@Slider(value = "Target CPS", min = 1.0F, max = 25.0F)
	public float targetCps = 12.0F;

	@Description("Varies click timing to avoid a fixed rhythm.")
	@Check("Randomize")
	public boolean randomize = true;

	@Description("Sometimes slows down briefly, like a tired hand.")
	@Check("Simulate Exhaust")
	public boolean simulateExhaust = true;

	@Section("Conditions")
	@Description("Only works while you hold the attack key.")
	@Check("Require Mouse Down")
	public boolean requireMouseDown = false;

	@Description("Only attacks while holding a weapon.")
	@Check("Weapons Only")
	public boolean weaponsOnly = true;

	@Description("Stops while you are mining a block.")
	@Check("Disable While Breaking Blocks")
	public boolean breakingBlocks = true;

	@Description("Stops while you block, eat or draw a bow.")
	@Check("Disable While Using Items")
	public boolean usingItems = false;

	@Description("Skips invisible targets.")
	@Check("Ignore Invisibles")
	public boolean ignoreInvisibles = true;

	@Description("Skips targets fully behind blocks and won't hit through them.")
	@Check("Not Behind Blocks")
	public boolean notBehindBlocks = true;

	private LivingEntity target;
	private long switchedAt;
	private boolean rotating;
	private int clicks;
	private long lastFrame;
	private double noiseSeed;

	public PlayerEntity target() {
		return target instanceof PlayerEntity player ? player : null;
	}

	@Override
	public String details() {
		return targetMode + " " + number(attackRange);
	}

	@Override
	public void onDisable() {
		reset();
	}

	@Listen
	public void onWorldLoad(WorldLoadEvent event) {
		reset();
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		pattern.tick(IllusionClient.ROTATION.sentYaw(), IllusionClient.ROTATION.sentPitch());
	}

	@Listen
	public void onRender3D(Render3DEvent event) {
		long now = System.nanoTime();
		float delta = Math.min(MAX_FRAME_MS, (now - lastFrame) / 1.0E6F);
		lastFrame = now;

		if (minecraft.player == null || target == null || CombatUtils.distanceTo(target) > Math.max(attackRange, swingRange)) {
			pattern.stop();
			clicks = 0;
			return;
		}
		pattern.advance(delta, null, targetCps, randomize, simulateExhaust, clickPattern.equals("Butterfly"));
		clicks += pattern.presses();
	}

	@Listen
	public void onPick(PickEvent event) {
		int presses = clicks;
		clicks = 0;
		if (presses == 0 || minecraft.player == null || target == null) return;

		if (canHit(RotationUtils.look(IllusionClient.ROTATION.sentYaw(), IllusionClient.ROTATION.sentPitch()))) minecraft.crosshairTarget = new HitResult(target);
		else if (!canSwing()) return;

		IllusionClient.STATS.autoClicks += presses;
		for (; presses > 0; presses--) KeyBinding.click(minecraft.options.attackKey.getKeyCode());
	}

	@Listen
	public void onInput(InputEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || minecraft.world == null) return;

		target = findTarget(player);
		if (target == null && !rotating || IllusionClient.ROTATION.active()) return;

		float yaw = IllusionClient.ROTATION.sentYaw();
		float pitch = IllusionClient.ROTATION.sentPitch();
		float[] goal = target == null ? new float[]{player.yaw, player.pitch} : RotationUtils.rotationTo(RotationUtils.aimPoint(target,
			CombatUtils.predictedShape(target, 1.0F, LEAD_TICKS), horizontalMultipoint, verticalMultipoint, randomization, noiseSeed));
		float yawDiff = MathHelper.wrapDegrees(goal[0] - yaw);
		float pitchDiff = goal[1] - pitch;
		if (target == null && Math.hypot(yawDiff, pitchDiff) <= RETURNED) {
			rotating = false;
			return;
		}

		boolean lockOn = mode.equals("Lock-on");
		if (!lockOn) pitchDiff = RotationUtils.easePitch(pitchDiff, yawDiff);
		float[] steps = RotationUtils.aimSteps(yawDiff, pitchDiff, horizontalSpeed, verticalSpeed, SECONDS_PER_TICK, mode);
		if (!lockOn) steps[1] += RotationUtils.pitchWobble(Math.abs(steps[0]));

		IllusionClient.ROTATION.rotate(yaw + steps[0], pitch + steps[1], RotationManager.Strafe.valueOf(strafe.toUpperCase(Locale.ROOT)));
		rotating = true;
		noiseSeed += (Math.abs(steps[0]) + Math.abs(steps[1])) * RotationUtils.NOISE_PER_DEGREE;
	}

	private boolean canHit(Vec3d look) {
		if (!CombatUtils.isHovered(target, look, attackRange, TARGETING_MARGIN)) return false;
		return !notBehindBlocks || !CombatUtils.isBlocked(target, look, attackRange, TARGETING_MARGIN);
	}

	private boolean canSwing() {
		HitResult crosshair = minecraft.crosshairTarget;
		return CombatUtils.distanceTo(target) <= swingRange && crosshair != null && crosshair.type == HitResult.Type.MISS;
	}

	private LivingEntity findTarget(LocalClientPlayerEntity player) {
		if (minecraft.screen != null || !player.isAlive()) return null;
		if (requireMouseDown && !IllusionClient.INPUT.held(minecraft.options.attackKey)) return null;
		if (weaponsOnly && !Weapons.isHolding(player)) return null;
		if (breakingBlocks && minecraft.interactionManager.isMiningBlock()) return null;
		if (usingItems && player.isUsingItem()) return null;

		long now = System.currentTimeMillis();
		boolean cycling = targetMode.equals("Switch") && switchDelay > 0.0F;
		if (target != null && valid(player, target)) {
			if (targetMode.equals("Single")) return target;
			if (cycling && now - switchedAt < switchDelay && inAttackRange(target)) return target;
		}

		List<LivingEntity> options = new ArrayList<>();
		for (Entity entity : minecraft.world.entities) {
			if (entity instanceof LivingEntity other && valid(player, other)) options.add(other);
		}
		if (options.isEmpty()) return null;

		if (options.stream().anyMatch(this::inAttackRange)) options.removeIf(other -> !inAttackRange(other));
		options.sort(Comparator.comparingDouble(other -> CombatUtils.score(other, sortBy, player.yaw, player.pitch)));
		if (!cycling) return options.getFirst();

		visited.retainAll(options);
		if (visited.containsAll(options)) visited.clear();
		LivingEntity next = options.stream().filter(other -> !visited.contains(other)).findFirst().orElseThrow();
		visited.add(next);
		switchedAt = now;
		return next;
	}

	private boolean inAttackRange(LivingEntity other) {
		return CombatUtils.distanceTo(other) <= attackRange;
	}

	private boolean valid(LocalClientPlayerEntity player, LivingEntity other) {
		if (other == player || !other.isAlive()) return false;
		if (other instanceof PlayerEntity enemy) {
			if (!attackPlayers || enemy.isSpectator() || !CombatUtils.isTarget(enemy)) return false;
		} else if (!attackMobs || !(other instanceof Monster)) {
			return false;
		}
		if (ignoreInvisibles && other.isInvisible()) return false;
		if (CombatUtils.distanceTo(other) > aimRange) return false;
		if (fov < FULL_CIRCLE && RotationUtils.angleTo(other, player.yaw, player.pitch) * 2.0D > fov) return false;
		return !notBehindBlocks || CombatUtils.isVisible(other);
	}

	private void reset() {
		target = null;
		rotating = false;
		clicks = 0;
		visited.clear();
		pattern.stop();
	}
}
