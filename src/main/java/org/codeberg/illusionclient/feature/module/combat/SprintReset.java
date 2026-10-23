package org.codeberg.illusionclient.feature.module.combat;

import io.github.nevalackin.radbus.Listen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityEventS2CPacket;
import net.minecraft.util.math.Box;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.InputEvent;
import org.codeberg.illusionclient.event.PacketReceiveEvent;
import org.codeberg.illusionclient.event.PacketSendEvent;
import org.codeberg.illusionclient.event.PlayerTickEvent;
import org.codeberg.illusionclient.event.UpdateSprintEvent;
import org.codeberg.illusionclient.event.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.util.CombatUtils;
import org.codeberg.illusionclient.util.MoveUtils;
import org.codeberg.illusionclient.util.input.InputState;

import java.util.Random;

@ModuleInfo(name = "Sprint Reset", category = Category.COMBAT)
@Description("Resets your sprint after a hit so the next one deals full knockback.")
public class SprintReset extends Module {
	private static final int HITTABLE_TICKS = 10;
	private static final int TRADE_TICKS = 10;
	private static final int EQUAL_TRADE_TICKS = 2;
	private static final int SPRINT_UP_TICKS = 2;
	private static final int MIN_STRAFE_TICKS = 5;
	private static final int MAX_STRAFE_TICKS = 12;
	private static final int STRAFE_TICKS = 20;
	private static final int MAX_ZEST_TICKS = 15;
	private static final int ZEST_SEQUENCE = 3;
	private static final double SAFE_DISTANCE = 3.2D;
	private static final double MIN_S_TAP_DISTANCE = 2.8D;
	private static final double MAX_S_TAP_DISTANCE = 3.0D;
	private static final double STRAFE_PROBE = 0.5D;
	private static final double MAX_DROP = 2.0D;
	private static final double TRAP_DISTANCE = 1.0D;
	private static final double MOVING_SPEED = 0.03D;
	private static final double FACING_COS = 0.5D;
	private static final float HISTORY = 0.9F;
	private static final float MAX_CROUCHING = 0.3F;
	private static final float MIN_APPROACHING = 0.5F;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final Random random = new Random();

	@Description("W-Tap lets go of forward, S-Tap holds forward and back together, Sneak sneaks, No Stop only drops sprint, Dynamic picks for you.")
	@Combo(value = "Mode", options = {"W-Tap", "S-Tap", "Sneak", "No Stop", "Dynamic"})
	public String mode = "W-Tap";

	@Description("How long to wait after a hit before resetting.")
	@Requires(value = "mode", is = {"W-Tap", "S-Tap", "Sneak", "No Stop"})
	@Slider(value = "Delay After Attack (ms)", min = 0.0F, max = 500.0F)
	public float delayAfterAttack = 250.0F;

	@Description("How long to stop sprinting for.")
	@Requires(value = "mode", is = {"W-Tap", "S-Tap", "Sneak", "No Stop"})
	@Slider(value = "Stop Duration (ms)", min = 50.0F, max = 500.0F)
	public float stopDuration = 100.0F;

	@Description("How long Dynamic usually stops sprinting for when you're the one pressing the attack.")
	@Requires(value = "mode", is = "Dynamic")
	@Slider(value = "Stop Duration (ms)", min = 50.0F, max = 300.0F)
	public float dynamicStopDuration = 50.0F;

	@Description("Holds forward for two hits and lets go for the third against opponents walking into you.")
	@Requires(value = "mode", is = "Dynamic")
	@Check("Zest Tap")
	public boolean zestTap = false;

	@Description("Strafes randomly left and right while zest tapping, avoiding walls and drops.")
	@Requires("zestTap")
	@Check("Zest Tap Strafe")
	public boolean zestTapStrafe = false;

	@Description("Varies the delay and duration.")
	@Check("Randomize")
	public boolean randomize = true;

	@Description("Waits until you take damage before resetting.")
	@Check("Wait For Damage")
	public boolean waitForDamage = false;

	@Description("Only activates while holding a weapon.")
	@Check("Weapons Only")
	public boolean weaponsOnly = true;

	private enum Tap { FORWARD, BACK, SNEAK, SPRINT }

	private PlayerEntity target;
	private Tap tap;
	private int ticks;
	private int hitTick;
	private int hurtTick = Integer.MIN_VALUE / 2;
	private int start;
	private int minEnd;
	private int maxEnd;
	private double spacing;
	private double gap;
	private boolean waiting;
	private boolean offensive;
	private int zestHits;
	private float crouching;
	private float approaching;
	private int strafe;
	private int strafeTicks;

	@Override
	public String details() {
		return mode;
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
	public void onPacketSend(PacketSendEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || minecraft.world == null) return;
		if (!(event.packet() instanceof PlayerInteractEntityC2SPacket packet)) return;
		if (packet.getAction() != PlayerInteractEntityC2SPacket.Action.ATTACK) return;
		if (!(packet.getInteractTarget(minecraft.world) instanceof PlayerEntity attacked) || !CombatUtils.isTarget(attacked)) return;
		if (weaponsOnly && !Weapons.isWeapons(player.getItemInHand())) return;
		if (attacked.invulnerableTimer > attacked.invulnerableTicks / 2) return;

		if (attacked != target) {
			target = attacked;
			crouching = approaching = 0.0F;
			zestHits = 0;
		}
		hitTick = ticks;
		waiting = waitForDamage;
		offensive = false;

		if (mode.equals("Dynamic")) {
			planDynamic(player);
			return;
		}
		zestHits = 0;
		if (player.isSprinting()) planFixed();
		else tap = null;
	}

	@Listen
	public void onPacketReceive(PacketReceiveEvent event) {
		Packet<?> received = event.packet();
		if (!(received instanceof EntityEventS2CPacket packet) || packet.getEvent() != CombatUtils.HURT || minecraft.world == null) return;
		if (packet.getEntity(minecraft.world) != minecraft.player) return;

		hurtTick = ticks;
		waiting = false;
		if (offensive && tap != null && ticks < start && ticks - hitTick <= EQUAL_TRADE_TICKS) planDefensive();
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || target == null) return;
		if (target.removed) {
			target = null;
			return;
		}

		crouching = crouching * HISTORY + (target.isSneaking() ? 1.0F - HISTORY : 0.0F);
		if (target.damagedTimer > 0) return;
		double speed = approachSpeed(player, target);
		approaching = approaching * HISTORY + (speed > MOVING_SPEED ? 1.0F - HISTORY : 0.0F);
	}

	@Listen
	public void onInput(InputEvent event) {
		ticks++;
		LocalClientPlayerEntity player = minecraft.player;
		boolean playing = player != null && minecraft.screen == null;
		boolean resetting = playing && active();
		MoveUtils.setSprintBlocked(resetting);
		InputState client = IllusionClient.INPUT.client;
		if (!resetting) {
			if (playing) strafe(player, client);
			return;
		}

		switch (tap) {
			case FORWARD -> client.forward = false;
			case BACK -> {
				client.back = true;
				if (target != null && CombatUtils.distanceTo(target) < spacing) client.forward = false;
			}
			case SNEAK -> client.sneak = true;
		}
	}

	@Listen
	public void onUpdateSprint(UpdateSprintEvent event) {
		if (MoveUtils.isSprintBlocked() && tap == Tap.SPRINT) event.sprinting = false;
	}

	private boolean active() {
		if (tap == null) return false;
		if (waiting) {
			if (ticks - hitTick > TRADE_TICKS) {
				tap = null;
				return false;
			}
			start++;
			minEnd++;
			maxEnd++;
		}
		if (ticks < start) return false;
		double distance = target == null ? Double.MAX_VALUE : CombatUtils.distanceTo(target);
		boolean opening = distance < spacing && distance >= gap;
		gap = distance;
		if (ticks < minEnd || ticks < maxEnd && (opening || tap == Tap.BACK)) return true;

		tap = null;
		return false;
	}

	private void planFixed() {
		Tap tap = switch (mode) {
			case "S-Tap" -> Tap.BACK;
			case "Sneak" -> Tap.SNEAK;
			case "No Stop" -> Tap.SPRINT;
			default -> Tap.FORWARD;
		};
		int delay = Math.round(delayAfterAttack / MS_PER_TICK);
		int duration = Math.max(1, Math.round(stopDuration / MS_PER_TICK));
		plan(tap, jitter(delay), jitter(duration), 0, 0.0D);
	}

	private void planDynamic(LocalClientPlayerEntity player) {
		if (zestTap && zestable(player)) {
			zestHits = zestHits % ZEST_SEQUENCE + 1;
			if (zestHits == ZEST_SEQUENCE - 1) {
				plan(Tap.FORWARD, 0, MAX_ZEST_TICKS, 0, 0.0D);
			} else {
				tap = null;
			}
			return;
		}
		zestHits = 0;

		if (!player.isSprinting()) {
			tap = null;
		} else if (trapped(player)) {
			double spacing = randomize ? MIN_S_TAP_DISTANCE + random.nextDouble() * (MAX_S_TAP_DISTANCE - MIN_S_TAP_DISTANCE) : MAX_S_TAP_DISTANCE;
			plan(Tap.BACK, 0, 0, HITTABLE_TICKS - SPRINT_UP_TICKS, spacing);
		} else if (hitTick - hurtTick <= EQUAL_TRADE_TICKS) {
			planDefensive();
		} else {
			offensive = true;
			int duration = Math.max(1, Math.round(dynamicStopDuration / MS_PER_TICK));
			plan(Tap.FORWARD, jitter(HITTABLE_TICKS - SPRINT_UP_TICKS - duration - 1), duration, 0, 0.0D);
		}
	}

	private void planDefensive() {
		offensive = false;
		plan(Tap.FORWARD, 0, 1, HITTABLE_TICKS - SPRINT_UP_TICKS, SAFE_DISTANCE);
	}

	private void plan(Tap tap, int delay, int duration, int maxDuration, double spacing) {
		this.tap = tap;
		this.spacing = spacing;
		gap = 0.0D;
		start = hitTick + 1 + delay;
		minEnd = start + duration;
		maxEnd = start + Math.max(duration, maxDuration);
	}

	private int jitter(int ticks) {
		return randomize ? Math.max(0, ticks + random.nextInt(3) - 1) : ticks;
	}

	private void strafe(LocalClientPlayerEntity player, InputState client) {
		if (!mode.equals("Dynamic") || !zestTap || !zestTapStrafe || zestHits == 0 || target == null || ticks - hitTick > STRAFE_TICKS) return;
		if (!client.forward || client.back || client.left || client.right) return;

		if (--strafeTicks <= 0 || strafe != 0 && !clear(player, strafe)) {
			strafe = random.nextBoolean() ? 1 : -1;
			if (!clear(player, strafe)) strafe = clear(player, -strafe) ? -strafe : 0;
			strafeTicks = MIN_STRAFE_TICKS + random.nextInt(MAX_STRAFE_TICKS - MIN_STRAFE_TICKS + 1);
		}
		client.left = strafe > 0;
		client.right = strafe < 0;
	}

	private boolean clear(LocalClientPlayerEntity player, int side) {
		double[] direction = MoveUtils.inputDirection(player.yaw, 1.0F, side);
		Box shape = player.getShape().moved(direction[0] * STRAFE_PROBE, 0.0D, direction[1] * STRAFE_PROBE);
		return minecraft.world.getCollisions(player, shape.moved(0.0D, player.stepHeight, 0.0D)).isEmpty()
			&& !minecraft.world.getCollisions(player, shape.expanded(0.0D, -MAX_DROP, 0.0D)).isEmpty();
	}

	private boolean trapped(LocalClientPlayerEntity player) {
		double dx = target.x - player.x;
		double dz = target.z - player.z;
		double length = Math.hypot(dx, dz);
		if (length < 1.0E-3D) return false;

		double scale = TRAP_DISTANCE / length;
		Box behind = target.getShape().moved(dx * scale, target.stepHeight, dz * scale);
		return !minecraft.world.getCollisions(target, behind).isEmpty();
	}

	private boolean zestable(LocalClientPlayerEntity player) {
		if (target.isSwordBlocking() || crouching > MAX_CROUCHING || approaching < MIN_APPROACHING) return false;
		if (target.damagedTimer <= 0 && approachSpeed(player, target) < -MOVING_SPEED) return false;

		double dx = player.x - target.x;
		double dz = player.z - target.z;
		double length = Math.hypot(dx, dz);
		if (length < 1.0E-3D) return true;

		double yaw = Math.toRadians(target.headYaw);
		return (-Math.sin(yaw) * dx + Math.cos(yaw) * dz) / length >= FACING_COS;
	}

	private static double approachSpeed(LocalClientPlayerEntity player, Entity opponent) {
		double dx = player.x - opponent.x;
		double dz = player.z - opponent.z;
		double length = Math.hypot(dx, dz);
		if (length < 1.0E-3D) return 0.0D;

		return ((opponent.x - opponent.prevX) * dx + (opponent.z - opponent.prevZ) * dz) / length;
	}

	private void reset() {
		target = null;
		tap = null;
		hurtTick = Integer.MIN_VALUE / 2;
		waiting = offensive = false;
		MoveUtils.setSprintBlocked(false);
		zestHits = strafe = strafeTicks = 0;
		crouching = approaching = 0.0F;
	}
}
