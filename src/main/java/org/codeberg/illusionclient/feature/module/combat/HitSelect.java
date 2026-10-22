package org.codeberg.illusionclient.feature.module.combat;

import io.github.nevalackin.radbus.Listen;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityEventS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.event.PacketReceiveEvent;
import org.codeberg.illusionclient.event.PacketSendEvent;
import org.codeberg.illusionclient.event.PlayerTickEvent;
import org.codeberg.illusionclient.event.SwingEvent;
import org.codeberg.illusionclient.event.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.mixin.LivingEntityAccessor;
import org.codeberg.illusionclient.util.CombatUtils;

import java.util.Random;

@ModuleInfo(name = "Hit Select", category = Category.COMBAT)
@Description("Cancels clicks that wouldn't deal damage.")
public class HitSelect extends Module {
	private static final long ENGAGE_TIMEOUT_MS = 3000L;
	private static final int KNOCKBACK_GRACE_TICKS = 2;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final Random random = new Random();
	private final Reference2LongMap<Entity> lastHit = new Reference2LongOpenHashMap<>();

	@Description("The longest clicks are held back after a hit.")
	@Slider(value = "Pause Duration (ms)", min = 0.0F, max = 1000.0F)
	public float pauseDuration = 500.0F;

	@Description("Burst clicks only when the target can take damage, Criticals also waits until you fall.")
	@Combo(value = "Mode", options = {"Burst", "Criticals"})
	public String mode = "Burst";

	@Description("Holds your first hit until you get hit or this time passes.")
	@Requires(value = "mode", is = "Burst")
	@Slider(value = "Wait For First Hit (ms)", min = 0.0F, max = 1000.0F)
	public float waitForFirstHit = 0.0F;

	@Description("Holds later hits in a trade until you get hit or this time passes.")
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

	@Description("Waits for the server to confirm hits instead of predicting them.")
	@Check("Use Server Attack Time")
	public boolean useServerAttackTime = false;

	@Description("Still plays the swing animation for cancelled clicks.")
	@Check("Fake Swing")
	public boolean fakeSwing = false;

	@Description("Chance to cancel a click that wouldn't deal damage.")
	@Slider(value = "Cancel Rate In Combat (%)", min = 0.0F, max = 100.0F)
	public float cancelRateInCombat = 100.0F;

	@Description("Chance to cancel a swing at the air.")
	@Slider(value = "Cancel Rate Missed Swings (%)", min = 0.0F, max = 100.0F)
	public float cancelRateMissedSwings = 0.0F;

	private Entity engaged;
	private long engagedAt;
	private long lastAttempt;
	private long lastHurt;
	private long critHoldStart = -1L;
	private boolean takingKnockback;
	private int knockbackGrace;

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
	public void onSwing(SwingEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		HitResult target = minecraft.crosshairTarget;
		if (player == null || target == null) return;

		boolean cancel = switch (target.type) {
			case MISS -> roll(cancelRateMissedSwings);
			case ENTITY -> target.entity instanceof LivingEntity && holdAttack(player, target.entity);
			default -> false;
		};
		if (!cancel) return;

		event.cancelled = true;
		if (fakeSwing) swingLocally(player);
	}

	@Listen
	public void onPacketSend(PacketSendEvent event) {
		if (useServerAttackTime || minecraft.world == null) return;
		if (!(event.packet() instanceof PlayerInteractEntityC2SPacket packet)) return;
		if (packet.getAction() != PlayerInteractEntityC2SPacket.Action.ATTACK) return;

		Entity attacked = packet.getInteractTarget(minecraft.world);
		long now = System.currentTimeMillis();
		if (attacked != null && !invulnerable(attacked, now)) lastHit.put(attacked, now);
	}

	@Listen
	public void onPacketReceive(PacketReceiveEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || minecraft.world == null) return;

		Packet<?> received = event.packet();
		if (received instanceof EntityVelocityS2CPacket velocity && velocity.getId() == player.getNetworkId()) {
			takingKnockback = true;
			knockbackGrace = KNOCKBACK_GRACE_TICKS;
			return;
		}
		if (!(received instanceof EntityEventS2CPacket packet) || packet.getEvent() != CombatUtils.HURT) return;

		Entity entity = packet.getEntity(minecraft.world);
		if (entity == player) lastHurt = System.currentTimeMillis();
		else if (entity != null && useServerAttackTime) lastHit.put(entity, System.currentTimeMillis());
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null) return;

		if (knockbackGrace > 0) knockbackGrace--;
		else if (player.onGround) takingKnockback = false;
		lastHit.keySet().removeIf(entity -> entity.removed);
	}

	private boolean holdAttack(LocalClientPlayerEntity player, Entity target) {
		long now = System.currentTimeMillis();
		boolean fresh = target != engaged || now - lastAttempt > ENGAGE_TIMEOUT_MS;
		if (fresh) {
			engaged = target;
			engagedAt = now;
		}
		lastAttempt = now;

		if (invulnerable(target, now)) {
			critHoldStart = -1L;
			return roll(cancelRateInCombat);
		}
		if (mode.equals("Criticals")) return holdForCritical(player, now);

		long last = lastHit.getLong(target);
		boolean firstHit = now - last > ENGAGE_TIMEOUT_MS;
		long readyAt = firstHit ? engagedAt : last + (long) pauseDuration;
		float wait = firstHit ? waitForFirstHit : hitLaterInTrades;
		return lastHurt < readyAt && now - readyAt < wait;
	}

	private boolean holdForCritical(LocalClientPlayerEntity player, long now) {
		boolean rising = !player.onGround && player.velocityY > 0.0D;
		boolean skip = disableDuringKnockback && takingKnockback || onlyWhileDamaged && player.damagedTimer <= 0;
		if (!rising || skip) {
			critHoldStart = -1L;
			return false;
		}
		if (critHoldStart < 0L) critHoldStart = now;
		return now - critHoldStart < pauseDuration;
	}

	private boolean invulnerable(Entity target, long now) {
		return now - lastHit.getLong(target) < pauseDuration;
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
		lastHit.clear();
		engaged = null;
		lastAttempt = 0L;
		lastHurt = 0L;
		critHoldStart = -1L;
		takingKnockback = false;
		knockbackGrace = 0;
	}
}
