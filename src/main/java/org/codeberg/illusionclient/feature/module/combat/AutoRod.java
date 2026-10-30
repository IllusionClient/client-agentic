package org.codeberg.illusionclient.feature.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.input.InputEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.RangeSlider;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.util.CombatUtils;
import org.codeberg.illusionclient.util.RotationUtils;
import org.codeberg.illusionclient.util.rotation.RotationManager;

@ModuleInfo(name = "Auto Rod", category = Category.COMBAT)
@Description("Throws your fishing rod at opponents so it lands right as they can take damage again, then swaps back.")
public class AutoRod extends Module {
	private static final double SPEED = 1.5D;
	private static final double DRAG = 0.92D;
	private static final double GRAVITY = 0.04D;
	private static final int MAX_FLIGHT = 20;
	private static final int HURT_TICKS = 10;
	private final Minecraft minecraft = Minecraft.getInstance();

	@Description("Only throws at opponents this far away. Keep the minimum above your reach so the rod doesn't replace a sword hit.")
	@RangeSlider(value = "Range", min = 0.0F, max = 12.0F)
	public float[] range = {3.5F, 8.0F};

	@Description("Only throws at opponents within this many degrees of your crosshair.")
	@Slider(value = "FOV", min = 0.0F, max = 180.0F)
	public float fov = 60.0F;

	@Description("Throws this many ticks before the opponent can take damage again, so the rod still lands if they recover early.")
	@Slider(value = "Hurt Time Lead (ticks)", min = 0.0F, max = 5.0F, step = 1.0F)
	public float hurtTimeLead = 1.0F;

	@Description("Swaps back once the rod hits, or after this long.")
	@Slider(value = "Swap Back Delay (ms)", min = 50.0F, max = 1000.0F)
	public float swapBackDelay = 500.0F;

	@Description("Time between throws.")
	@Slider(value = "Cooldown (ms)", min = 0.0F, max = 2000.0F)
	public float cooldown = 600.0F;

	@Description("Only throws while holding a weapon.")
	@Check("Weapons Only")
	public boolean weaponsOnly = true;

	private enum Stage { IDLE, AIMED, THROWN }

	private Stage stage = Stage.IDLE;
	private PlayerEntity target;
	private int previousSlot = -1;
	private int timer;

	@Override
	public void onDisable() {
		finish();
	}

	@Listen
	public void onWorldLoad(WorldLoadEvent event) {
		finish();
	}

	@Listen
	public void onInput(InputEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || minecraft.world == null) return;

		switch (stage) {
			case IDLE -> {
				if (timer > 0) timer--;
				else start(player);
			}
			case AIMED -> {
				ItemStack held = player.inventory.getSelectedItem();
				if (held == null || held.getItem() != Items.FISHING_ROD || !target.isAlive()) {
					finish();
					return;
				}
				minecraft.interactionManager.useItem(player, minecraft.world, held);
				timer = ticks(swapBackDelay);
				stage = Stage.THROWN;
			}
			case THROWN -> {
				if (--timer <= 0 || target.damagedTimer == HURT_TICKS) finish();
			}
		}
	}

	private void start(LocalClientPlayerEntity player) {
		if (minecraft.screen != null || !player.isAlive() || player.getItemInUse() != null) return;
		if (weaponsOnly && !Weapons.isHolding(player)) return;

		int slot = rodSlot(player.inventory);
		if (slot == -1) return;

		PlayerEntity found = CombatUtils.best(other -> other.isAlive() && CombatUtils.isTarget(other)
			&& CombatUtils.distanceTo(other) >= range[0] && CombatUtils.distanceTo(other) <= range[1]
			&& RotationUtils.angleTo(other, player.yaw, player.pitch) <= fov, CombatUtils::distanceTo);
		float[] rotation = found == null ? null : aim(player, found);
		if (rotation == null || !IllusionClient.MODULES.claimSlot(this)) return;

		IllusionClient.ROTATION.rotate(rotation[0], rotation[1], RotationManager.Strafe.SILENT);
		target = found;
		previousSlot = player.inventory.selectedSlot;
		player.inventory.selectedSlot = slot;
		stage = Stage.AIMED;
	}

	private float[] aim(LocalClientPlayerEntity player, PlayerEntity found) {
		Vec3d eye = player.getEyePosition(1.0F);
		double distance = Math.hypot(found.x - eye.x, found.z - eye.z);
		double travelled = 0.0D;
		double speed = SPEED;
		double drop = 0.0D;
		double fall = 0.0D;
		int ticks = 0;
		while (travelled < distance) {
			if (++ticks > MAX_FLIGHT) return null;
			travelled += speed;
			drop += fall;
			speed *= DRAG;
			fall = fall * DRAG - GRAVITY;
		}
		if (IllusionClient.COMBAT.ticksUntilDamageable(found) > ticks + hurtTimeLead) return null;

		Box box = found.getShape();
		Vec3d point = new Vec3d(
			(box.minX + box.maxX) / 2.0D + (found.x - found.prevX) * ticks,
			(box.minY + box.maxY) / 2.0D - drop,
			(box.minZ + box.maxZ) / 2.0D + (found.z - found.prevZ) * ticks);
		return RotationUtils.rotationTo(eye, point);
	}

	private void finish() {
		if (previousSlot != -1 && minecraft.player != null) minecraft.player.inventory.selectedSlot = previousSlot;
		if (stage != Stage.IDLE) timer = ticks(cooldown);
		IllusionClient.MODULES.releaseSlot(this);
		previousSlot = -1;
		target = null;
		stage = Stage.IDLE;
	}

	private static int rodSlot(PlayerInventory inventory) {
		for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
			ItemStack stack = inventory.items[slot];
			if (stack != null && stack.getItem() == Items.FISHING_ROD) return slot;
		}
		return -1;
	}
}
