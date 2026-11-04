package org.codeberg.illusionclient.feature.module.combat;

import org.codeberg.illusionclient.event.api.Listen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.input.InputEvent;
import org.codeberg.illusionclient.event.network.PacketReceiveEvent;
import org.codeberg.illusionclient.event.player.JumpEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.client.Weapons;
import org.codeberg.illusionclient.mixin.EntityVelocityS2CPacketAccessor;
import org.codeberg.illusionclient.util.CombatUtils;

import java.util.Random;

@ModuleInfo(name = "Velocity", category = Category.COMBAT)
@Description("Reduces the knockback you take.")
public class Velocity extends Module {
	private static final int MAX_LATE_TICKS = 3;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final Random random = new Random();

	@Description("Regular reduces motion, Ignore prevents knockback entirely, Jump automatically jumps after knockback.")
	@Combo(value = "Mode", options = {"Regular", "Ignore", "Jump"})
	public String mode = "Jump";

	@Description("How much horizontal knockback to keep.")
	@Requires(value = "mode", is = "Regular")
	@Slider(value = "Horizontal (%)", min = 0.0F, max = 100.0F)
	public float horizontal = 90.0F;

	@Description("How much vertical knockback to keep.")
	@Requires(value = "mode", is = "Regular")
	@Slider(value = "Vertical (%)", min = 0.0F, max = 100.0F)
	public float vertical = 100.0F;

	@Description("How often the jump lands on the right tick. Missed jumps come 1 to 3 ticks late.")
	@Requires(value = "mode", is = "Jump")
	@Slider(value = "Accuracy (%)", min = 0.0F, max = 100.0F)
	public float accuracy = 80.0F;

	@Description("Chance to activate.")
	@Slider(value = "Chance (%)", min = 0.0F, max = 100.0F)
	public float chance = 80.0F;

	@Description("Only activates while looking at a player.")
	@Check("Looking At Player")
	public boolean lookingAtPlayer = true;

	@Description("Only works when pressing forward.")
	@Check("Moving Forward")
	public boolean movingForward = false;

	@Description("Only activates while the attack key is held.")
	@Check("Left Mouse Pressed")
	public boolean leftMousePressed = true;

	@Description("Only activates while holding a weapon.")
	@Check("Weapons Only")
	public boolean weaponsOnly = true;

	@Description("Only activates while sprinting.")
	@Check("Sprinting")
	public boolean sprinting = true;

	private int jumpDelay = -1;
	private boolean resetting;
	private EntityVelocityS2CPacket knockback;

	@Override
	public String details() {
		return mode;
	}

	@Override
	public void onDisable() {
		jumpDelay = -1;
		resetting = false;
		knockback = null;
	}

	@Listen
	public void onPacketReceive(PacketReceiveEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		Packet<?> received = event.packet();
		if (player == null || !(received instanceof EntityVelocityS2CPacket packet) || packet.getId() != player.getNetworkId()) return;
		if (player.damagedTimer != player.damagedTime) return;
		if (!conditionsMet(player) || random.nextFloat() * 100.0F >= chance) return;

		switch (mode) {
			case "Ignore" -> event.cancelled = true;
			case "Jump" -> {
				if (!player.onGround) return;
				jumpDelay = random.nextFloat() * 100.0F < accuracy ? 0 : 1 + random.nextInt(MAX_LATE_TICKS);
				knockback = packet;
			}
			default -> {
				EntityVelocityS2CPacketAccessor scaled = (EntityVelocityS2CPacketAccessor) packet;
				scaled.illusion$setVelocityX(Math.round(packet.getVelocityX() * horizontal / 100.0F));
				scaled.illusion$setVelocityY(Math.round(packet.getVelocityY() * vertical / 100.0F));
				scaled.illusion$setVelocityZ(Math.round(packet.getVelocityZ() * horizontal / 100.0F));
			}
		}
	}

	@Listen
	public void onInput(InputEvent event) {
		if (knockback != null && IllusionClient.LAG.isHeld(knockback)) return;
		knockback = null;
		LocalClientPlayerEntity player = minecraft.player;
		resetting = jumpDelay == 0 && player != null && player.onGround;
		if (jumpDelay < 0 || jumpDelay-- > 0) return;

		IllusionClient.INPUT.client.jump = true;
	}

	@Listen
	public void onJump(JumpEvent event) {
		if (!resetting) return;

		resetting = false;
		if (!minecraft.player.isSprinting()) event.cancelled = true;
	}

	private boolean hasTarget() {
		HitResult crosshair = minecraft.crosshairTarget;
		return crosshair != null && crosshair.entity instanceof PlayerEntity hovered && CombatUtils.isTarget(hovered);
	}

	private boolean conditionsMet(LocalClientPlayerEntity player) {
		if (minecraft.screen != null || !player.isAlive() || player.isSleeping()) return false;
		if (weaponsOnly && !Weapons.isHolding(player)) return false;
		if (leftMousePressed && !IllusionClient.INPUT.held(minecraft.options.attackKey)) return false;
		if (lookingAtPlayer && !hasTarget()) return false;
		if (movingForward && IllusionClient.INPUT.player.forwardAxis() <= 0.0F) return false;
		return !sprinting || player.isSprinting();
	}
}
