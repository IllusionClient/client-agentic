package org.codeberg.illusionclient.feature.module.combat;

import org.codeberg.illusionclient.event.api.Listen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.network.PacketReceiveEvent;
import org.codeberg.illusionclient.event.player.PlayerTickEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Section;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.util.CombatUtils;
import org.codeberg.illusionclient.util.lag.LagManager;

import java.util.Random;

@ModuleInfo(name = "Knockback Delay", category = Category.COMBAT)
@Description("Delays your knockback when you get hit. Pairs well with Velocity.")
public class KnockbackDelay extends Module {
	private final Minecraft minecraft = Minecraft.getInstance();
	private final Random random = new Random();
	private final LagManager.Lag lag = IllusionClient.LAG.create();

	@Section("General")
	@Description("Only delays when a target is within this distance, so rods and arrows from afar are ignored.")
	@Slider(value = "Distance To Target", min = 0.0F, max = 8.0F)
	public float distanceToTarget = 4.0F;

	@Description("Chance to delay your knockback.")
	@Slider(value = "Chance (%)", min = 0.0F, max = 100.0F)
	public float chance = 100.0F;

	@Description("How long your knockback is delayed. High values make the game feel laggy.")
	@Slider(value = "Maximum Delay (ms)", min = 0.0F, max = 1000.0F)
	public float maximumDelay = 200.0F;

	@Section("Conditions")
	@Description("Only delays while you are in the air.")
	@Check("In Air")
	public boolean inAir = true;

	@Description("Only delays while looking at a player.")
	@Check("Looking At Player")
	public boolean lookingAtPlayer = false;

	@Description("Only delays while the attack key is held.")
	@Check("Mouse Pressed")
	public boolean mousePressed = false;

	@Description("Only delays while holding a weapon.")
	@Check("Holding Weapon")
	public boolean holdingWeapon = true;

	private long releaseAt;

	@Override
	public String details() {
		return number(maximumDelay) + " ms";
	}

	@Override
	public void onDisable() {
		lag.release();
	}

	@Listen
	public void onWorldLoad(WorldLoadEvent event) {
		lag.release();
	}

	@Listen
	public void onPacketReceive(PacketReceiveEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || !((Packet<?>) event.packet() instanceof EntityVelocityS2CPacket packet) || packet.getId() != player.getNetworkId()) return;
		if (player.damagedTimer != player.damagedTime) return;
		if (!conditionsMet(player) || random.nextFloat() * 100.0F >= chance) return;

		releaseAt = System.currentTimeMillis() + (long) maximumDelay;
		lag.hold((long) maximumDelay);
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		if (System.currentTimeMillis() >= releaseAt) lag.release();
	}

	private boolean conditionsMet(LocalClientPlayerEntity player) {
		if (minecraft.screen != null || !player.isAlive()) return false;
		if (inAir && player.onGround) return false;
		if (holdingWeapon && !Weapons.isHolding(player)) return false;
		if (mousePressed && !IllusionClient.INPUT.held(minecraft.options.attackKey)) return false;
		if (lookingAtPlayer && !hasTarget()) return false;
		PlayerEntity target = CombatUtils.best(CombatUtils::isTarget, CombatUtils::distanceTo);
		return target != null && CombatUtils.distanceTo(target) <= distanceToTarget;
	}

	private boolean hasTarget() {
		HitResult crosshair = minecraft.crosshairTarget;
		return crosshair != null && crosshair.entity instanceof PlayerEntity hovered && CombatUtils.isTarget(hovered);
	}
}
