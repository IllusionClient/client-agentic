package org.codeberg.illusionclient.feature.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.player.PickEvent;
import org.codeberg.illusionclient.event.player.PlayerTickEvent;
import org.codeberg.illusionclient.event.player.SwingEvent;
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
import org.codeberg.illusionclient.util.CombatUtils;
import org.codeberg.illusionclient.util.SimulatedPlayer;
import org.codeberg.illusionclient.util.lag.LagManager;
import org.codeberg.illusionclient.util.rotation.RotationManager;

import java.util.Random;

@ModuleInfo(name = "Knockback Displace", category = Category.COMBAT)
@Description("Deals knockback at a different angle by turning for one tick before you hit.")
public class KnockbackDisplace extends Module {
	private static final float YAW_JITTER = 10.0F;
	private static final float MIN_PITCH = 10.0F;
	private static final float PITCH_JITTER = 10.0F;
	private static final float FACING_AWAY = 100.0F;
	private static final float SWEEP_STEP = 15.0F;
	private static final int SIMULATED_TICKS = 20;
	private static final int NO_HAZARD = Integer.MAX_VALUE;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final Random random = new Random();
	private final LagManager.Lag lag = IllusionClient.LAG.createOutbound();

	@Section("General")
	@Description("Which way the target gets knocked. Strafe follows your A and D keys, Auto picks a side that drops the target into the void, a pit or lava.")
	@Combo(value = "Direction", options = {"Left", "Right", "Random", "Strafe", "Strafe (Inverted)", "Auto"})
	public String direction = "Left";

	@Description("Uses the last strafe key you pressed while neither is held.")
	@Requires(value = "direction", is = {"Strafe", "Strafe (Inverted)"})
	@Check("Use Last Strafe Direction")
	public boolean useLastStrafeDirection = false;

	@Description("How far the knockback turns from where you look. Auto turns up to this far and leaves your hit alone when it finds no drop.")
	@Slider(value = "Angle", min = 0.0F, max = 180.0F)
	public float angle = 90.0F;

	@Description("How deep a drop has to be for Auto to knock the target into it.")
	@Requires(value = "direction", is = "Auto")
	@Slider(value = "Minimum Drop (blocks)", min = 1.0F, max = 10.0F)
	public float minimumDrop = 3.0F;

	@Description("Time between displaced hits.")
	@Slider(value = "Cooldown (ms)", min = 0.0F, max = 2000.0F)
	public float cooldown = 250.0F;

	@Description("Holds your packets during the turn so other players don't see it.")
	@Check("Hide Rotation From Players")
	public boolean hideRotation = false;

	@Section("Conditions")
	@Description("Only activates while you aren't taking knockback.")
	@Check("Not Taking Knockback")
	public boolean notTakingKnockback = false;

	@Description("Only activates while holding a weapon.")
	@Check("Holding Weapon")
	public boolean holdingWeapon = false;

	@Description("Only activates while your held item has the Knockback enchantment.")
	@Check("Knockback Enchantment")
	public boolean knockbackEnchantment = false;

	@Description("Only activates while the target is facing away from you.")
	@Check("Target Facing Away")
	public boolean targetFacingAway = false;

	private boolean flicked;
	private boolean attacking;
	private int strafe;
	private long readyAt;

	@Override
	public String details() {
		return number(angle) + "°";
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
		LocalClientPlayerEntity player = minecraft.player;
		if (player != null) {
			int sideways = (int) Math.signum(player.input.movementSideways);
			if (sideways != 0 || !useLastStrafeDirection) strafe = sideways;
		}
		if (!attacking) return;

		attacking = false;
		lag.release();
	}

	@Listen
	public void onPick(PickEvent event) {
		if (!flicked) return;

		flicked = false;
		attacking = true;
		KeyBinding.click(minecraft.options.attackKey.getKeyCode());
	}

	@Listen
	public void onSwing(SwingEvent event) {
		if (flicked) {
			event.cancelled = true;
			return;
		}
		if (event.cancelled || attacking) return;

		LocalClientPlayerEntity player = minecraft.player;
		HitResult crosshair = minecraft.crosshairTarget;
		if (player == null || crosshair == null || !(crosshair.entity instanceof PlayerEntity target) || !eligible(player, target)) return;

		float offset = offset(player, target);
		if (offset == 0.0F) return;

		float pitch = (MIN_PITCH + random.nextFloat() * PITCH_JITTER) * (random.nextBoolean() ? 1.0F : -1.0F);
		IllusionClient.ROTATION.rotate(player.yaw + offset, player.pitch + pitch, RotationManager.Strafe.STRICT);
		if (hideRotation) lag.hold(Long.MAX_VALUE);
		readyAt = System.currentTimeMillis() + (long) cooldown;
		flicked = true;
		event.cancelled = true;
	}

	private boolean eligible(LocalClientPlayerEntity player, PlayerEntity target) {
		if (System.currentTimeMillis() < readyAt || IllusionClient.ROTATION.active()) return false;
		if (!target.isAlive() || !CombatUtils.isTarget(target) || !IllusionClient.COMBAT.canDamage(target)) return false;
		if (notTakingKnockback && player.damagedTimer > 0) return false;
		if (holdingWeapon && !Weapons.isHolding(player)) return false;
		if (targetFacingAway && Math.abs(MathHelper.wrapDegrees(target.yaw - player.yaw)) >= FACING_AWAY) return false;

		boolean enchanted = EnchantmentHelper.getKnockbackLevel(player) > 0;
		return enchanted || !knockbackEnchantment && player.isSprinting();
	}

	private float offset(LocalClientPlayerEntity player, PlayerEntity target) {
		if (direction.equals("Auto")) return hazardOffset(player, target);

		float turn = Math.max(angle - random.nextFloat() * YAW_JITTER, 0.0F);
		return switch (direction) {
			case "Left" -> -turn;
			case "Right" -> turn;
			case "Random" -> random.nextBoolean() ? turn : -turn;
			case "Strafe" -> strafe * turn;
			default -> -strafe * turn;
		};
	}

	private float hazardOffset(LocalClientPlayerEntity player, PlayerEntity target) {
		float best = 0.0F;
		int soonest = NO_HAZARD;
		for (float offset = 0.0F; offset <= angle; offset += SWEEP_STEP) {
			for (float signed : new float[]{offset, -offset}) {
				int ticks = ticksToHazard(player, target, player.yaw + signed);
				if (ticks < soonest) {
					soonest = ticks;
					best = signed;
				}
			}
		}
		return best;
	}

	private int ticksToHazard(LocalClientPlayerEntity player, PlayerEntity target, float yaw) {
		Vec3d knockback = CombatUtils.predictKnockback(target, player, yaw);
		SimulatedPlayer simulated = SimulatedPlayer.of(player);
		simulated.shape = target.getShape();
		simulated.velocityX = knockback.x;
		simulated.velocityY = knockback.y;
		simulated.velocityZ = knockback.z;
		simulated.forward = 0.0F;
		simulated.sideways = 0.0F;
		simulated.jumping = false;
		simulated.sneaking = false;
		simulated.sprinting = false;
		simulated.inCobweb = false;
		simulated.onGround = target.onGround;

		for (int tick = 1; tick <= SIMULATED_TICKS; tick++) {
			simulated.tick();
			if (simulated.isInLava()) return tick;
			if (simulated.inWater) return NO_HAZARD;
			if (target.y - simulated.y() >= minimumDrop) return tick;
		}
		return NO_HAZARD;
	}

	private void reset() {
		flicked = false;
		attacking = false;
		strafe = 0;
		readyAt = 0L;
		lag.release();
	}
}
