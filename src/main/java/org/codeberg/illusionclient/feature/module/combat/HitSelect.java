package org.codeberg.illusionclient.feature.module.combat;

import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.event.player.PickEvent;
import org.codeberg.illusionclient.event.player.PlayerTickEvent;
import org.codeberg.illusionclient.event.player.SwingEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.mixin.LivingEntityAccessor;

import java.util.Random;

@ModuleInfo(name = "Hit Select", category = Category.COMBAT)
@Description("Cancels clicks that wouldn't deal damage.")
public class HitSelect extends Module {
	private static final int NO_PAUSE = 1001;
	private static final int HURT_TICKS = 10;
	private static final double CLOSING_SPEED = 0.03D;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final Random random = new Random();
	private final BlockPos.Mutable cursor = new BlockPos.Mutable();

	@Description("The longest clicks are held back after a hit.")
	@Slider(value = "Pause Duration (ms)", min = 0.0F, max = 1000.0F)
	public float pauseDuration = 500.0F;

	@Description("Burst clicks only when the target can take damage, Criticals also waits until you fall.")
	@Combo(value = "Mode", options = {"Burst", "Criticals"})
	public String mode = "Burst";

	@Description("Holds your first hit on a fresh target for this long.")
	@Requires(value = "mode", is = "Burst")
	@Slider(value = "Wait For First Hit (ms)", min = 0.0F, max = 1000.0F)
	public float waitForFirstHit = 0.0F;

	@Description("Pauses this much longer while you're trading hits without getting hit.")
	@Requires(value = "mode", is = "Burst")
	@Slider(value = "Hit Later In Trades (ms)", min = 0.0F, max = 500.0F)
	public float hitLaterInTrades = 0.0F;

	@Description("Doesn't wait for criticals while you take knockback.")
	@Requires(value = "mode", is = "Criticals")
	@Check("Disable During Knockback")
	public boolean disableDuringKnockback = false;

	@Description("Only waits for criticals after you get hit.")
	@Requires(value = "mode", is = "Criticals")
	@Check("Only While Damaged")
	public boolean onlyWhileDamaged = false;

	@Description("Replays up to this many held back clicks as soon as the target can take damage again.")
	@Slider(value = "Burst Clicks", min = 0.0F, max = 5.0F)
	public float burstClicks = 0.0F;

	@Description("Waits for the server to confirm hits instead of predicting them.")
	@Check("Use Server Attack Time")
	public boolean useServerAttackTime = false;

	@Description("Only cancels clicks while you sprint.")
	@Check("Sprinting")
	public boolean sprinting = false;

	@Description("Only cancels clicks while you hold a weapon.")
	@Check("Holding Weapon")
	public boolean holdingWeapon = false;

	@Description("Still plays the swing animation for cancelled clicks.")
	@Check("Fake Swing")
	public boolean fakeSwing = false;

	@Description("Chance to cancel a click that wouldn't deal damage.")
	@Slider(value = "Cancel Rate In Combat (%)", min = 0.0F, max = 100.0F)
	public float cancelRateInCombat = 100.0F;

	@Description("Chance to cancel a swing at the air.")
	@Slider(value = "Cancel Rate Missed Swings (%)", min = 0.0F, max = 100.0F)
	public float cancelRateMissedSwings = 0.0F;

	private boolean decided;
	private boolean cancelling;
	private int undamagedTicks;
	private int clock = NO_PAUSE;
	private boolean serverReset;
	private int waitingHits;
	private Entity target;
	private double targetDistance = Double.MAX_VALUE;
	private boolean closing;
	private int held;
	private int heldTick;

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
	public void onPick(PickEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		decided = false;
		undamagedTicks = player != null && minecraft.focused && player.damagedTimer == 0 ? undamagedTicks + 1 : 0;
		if (held == 0 || player == null || minecraft.world == null) return;

		HitResult crosshair = minecraft.crosshairTarget;
		if (player.ticks - heldTick > HURT_TICKS || minecraft.screen != null || crosshair == null || crosshair.type != HitResult.Type.ENTITY) {
			held = 0;
			return;
		}
		cancelling = cancel(player);
		decided = true;
		if (cancelling) return;

		for (int clicks = Math.min(held, (int) burstClicks); clicks > 0; clicks--) KeyBinding.click(minecraft.options.attackKey.getKeyCode());
		held = 0;
	}

	@Listen
	public void onSwing(SwingEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || minecraft.world == null) return;

		if (!decided) {
			cancelling = cancel(player);
			decided = true;
		}
		if (!cancelling) return;

		HitResult crosshair = minecraft.crosshairTarget;
		if (burstClicks >= 1.0F && crosshair != null && crosshair.type == HitResult.Type.ENTITY) {
			held++;
			heldTick = player.ticks;
		}
		event.cancelled = true;
		if (fakeSwing && player.getItemInUse() == null) swingLocally(player);
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || minecraft.world == null) return;

		if (target != null && target.removed) target = null;
		boolean changed = false;
		HitResult crosshair = minecraft.crosshairTarget;
		if (crosshair != null && crosshair.entity instanceof LivingEntity hovered) {
			changed = target == null || hovered.getNetworkId() != target.getNetworkId();
			target = hovered;
		}

		double distance = target == null ? Double.MAX_VALUE : Math.hypot(target.x - player.x, target.z - player.z);
		closing = !changed && targetDistance - distance > CLOSING_SPEED;
		targetDistance = distance;

		updateClock();
	}

	private boolean cancel(LocalClientPlayerEntity player) {
		if (sprinting && !player.isSprinting()) return false;
		if (holdingWeapon && !Weapons.isHolding(player)) return false;

		HitResult crosshair = minecraft.crosshairTarget;
		if (crosshair == null) return false;
		if (crosshair.type == HitResult.Type.MISS) return roll(cancelRateMissedSwings);
		if (crosshair.type != HitResult.Type.ENTITY || crosshair.entity == null) return false;

		if (!useServerAttackTime) clock = Math.clamp(IllusionClient.COMBAT.hurtTime(crosshair.entity) - 1L, 0, NO_PAUSE);
		int pause = roundRandomly(pauseDuration / MS_PER_TICK);

		if (mode.equals("Criticals")) {
			boolean closer = (!player.onGround || player.velocityY > 0.0D) && closing;
			boolean cycle = clock < 2 || clock > 8 || useServerAttackTime;
			if (!player.onGround && player.fallDistance > 0.0F && cycle) return false;
			if (closer && (!cycle || disableDuringKnockback)) return false;
			if (minecraft.world.getBlockState(cursor.set(MathHelper.floor(player.x), MathHelper.floor(player.y), MathHelper.floor(player.z))).getBlock() != Blocks.AIR) return false;
			if (!player.onGround && player.velocityY > 0.0D && !(onlyWhileDamaged && player.damagedTimer == 0)) return true;
		} else {
			boolean zesting = IllusionClient.MODULES.get(SprintReset.class).zestTapping();
			if (closing && !zesting) return false;
			if (zesting || clock < 15) waitingHits = 0;
			else if (++waitingHits <= ticks(waitForFirstHit)) return true;

			if (undamagedTicks < 10 && !zesting) pause += ticks(hitLaterInTrades);
			if (serverReset) {
				serverReset = false;
				return false;
			}
		}
		return clock + 1 < pause && roll(cancelRateInCombat);
	}

	private void updateClock() {
		if (!useServerAttackTime) return;

		if (target != null && target.isAlive() && ((LivingEntity) target).damagedTimer == HURT_TICKS) {
			clock = 0;
			serverReset = true;
		} else {
			tickClock();
		}
	}

	private void tickClock() {
		clock = Math.min(clock + 1, NO_PAUSE);
	}

	private int roundRandomly(float value) {
		int whole = (int) value;
		return random.nextFloat() < value - whole ? whole + 1 : whole;
	}

	private boolean roll(float percent) {
		return random.nextFloat() * 100.0F < percent;
	}

	private static void swingLocally(LocalClientPlayerEntity player) {
		int duration = ((LivingEntityAccessor) player).illusion$getArmSwingDuration();
		if (player.armSwinging && player.armSwingingTicks >= 0 && player.armSwingingTicks < duration / 2) return;
		player.armSwingingTicks = -1;
		player.armSwinging = true;
	}

	private void reset() {
		decided = false;
		undamagedTicks = 0;
		clock = NO_PAUSE;
		serverReset = false;
		waitingHits = 0;
		target = null;
		targetDistance = Double.MAX_VALUE;
		closing = false;
		held = 0;
	}
}
