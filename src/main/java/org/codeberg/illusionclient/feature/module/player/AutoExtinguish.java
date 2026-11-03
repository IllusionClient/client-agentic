package org.codeberg.illusionclient.feature.module.player;

import net.minecraft.block.LiquidBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.input.InputEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.util.ItemUtils;
import org.codeberg.illusionclient.util.RotationUtils;
import org.codeberg.illusionclient.util.rotation.RotationManager;

@ModuleInfo(name = "Auto Extinguish", category = Category.PLAYER)
@Description("Places water under you when you're on fire, then picks it back up.")
public class AutoExtinguish extends Module {
	private static final double BUCKET_REACH = 5.0D;
	private static final int MAX_PICKUP_TRIES = 5;
	private static final int MAX_WAIT_TICKS = 40;
	private final Minecraft minecraft = Minecraft.getInstance();

	@Description("How long the water stays before it's picked back up. It also waits for the fire to go out.")
	@Slider(value = "Pickup Delay (ms)", min = 0.0F, max = 1000.0F)
	public float pickupDelay = 100.0F;

	@Description("Time between placements.")
	@Slider(value = "Cooldown (ms)", min = 0.0F, max = 5000.0F)
	public float cooldown = 500.0F;

	private enum Stage { IDLE, PLACE, PLACED, PICKUP }

	private Stage stage = Stage.IDLE;
	private BlockPos cell;
	private int previousSlot = -1;
	private int timer;
	private float yaw;
	private float pitch;

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
			case PLACE -> {
				if (!ItemUtils.holding(player, Items.WATER_BUCKET)) {
					finish();
					return;
				}
				use(player);
				timer = 0;
				stage = Stage.PLACED;
			}
			case PLACED -> {
				if (++timer < MAX_WAIT_TICKS && (player.isOnFire() || timer < ticks(pickupDelay))) return;
				timer = 0;
				stage = Stage.PICKUP;
				if (!waterSource(cell) || !aim(player, center(cell))) finish();
			}
			case PICKUP -> {
				if (!waterSource(cell) || !ItemUtils.holding(player, Items.BUCKET) || ++timer > MAX_PICKUP_TRIES) {
					finish();
					return;
				}
				use(player);
				aim(player, center(cell));
			}
		}
	}

	private void start(LocalClientPlayerEntity player) {
		if (minecraft.screen != null || !player.isAlive() || player.getItemInUse() != null) return;
		if (!player.isOnFire() || player.isInWater() || player.isInLava() || player.hasStatusEffect(StatusEffect.FIRE_RESISTANCE)) return;
		if (minecraft.world.dimension.yeetsWater()) return;

		int slot = ItemUtils.hotbarSlot(player.inventory, Items.WATER_BUCKET);
		if (slot == -1) return;

		BlockPos feet = new BlockPos(player);
		if (!replaceable(feet) || replaceable(feet.down())) return;

		Vec3d point = new Vec3d(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
		if (!visible(player, point, feet.down()) || !IllusionClient.MODULES.claimSlot(this)) return;

		cell = feet;
		previousSlot = player.inventory.selectedSlot;
		player.inventory.selectedSlot = slot;
		aim(player, point);
		stage = Stage.PLACE;
	}

	private boolean visible(LocalClientPlayerEntity player, Vec3d point, BlockPos support) {
		Vec3d eye = player.getEyePosition(1.0F);
		if (eye.distanceTo(point) > BUCKET_REACH) return false;

		HitResult hit = minecraft.world.rayTrace(eye, point.add(0.0D, -0.01D, 0.0D));
		return hit != null && hit.type == HitResult.Type.BLOCK && hit.face == Direction.UP && hit.getPos().equals(support);
	}

	private boolean aim(LocalClientPlayerEntity player, Vec3d point) {
		Vec3d eye = player.getEyePosition(1.0F);
		if (eye.distanceTo(point) > BUCKET_REACH) return false;

		float[] rotation = RotationUtils.rotationTo(eye, point);
		IllusionClient.ROTATION.rotate(rotation[0], rotation[1], RotationManager.Strafe.SILENT);
		yaw = IllusionClient.ROTATION.yaw(rotation[0]);
		pitch = IllusionClient.ROTATION.pitch(rotation[1]);
		return true;
	}

	private void use(LocalClientPlayerEntity player) {
		float realYaw = player.yaw;
		float realPitch = player.pitch;
		player.yaw = yaw;
		player.pitch = pitch;
		minecraft.interactionManager.useItem(player, minecraft.world, player.inventory.getSelectedItem());
		player.yaw = realYaw;
		player.pitch = realPitch;
	}

	private void finish() {
		if (previousSlot != -1 && minecraft.player != null) minecraft.player.inventory.selectedSlot = previousSlot;
		if (stage != Stage.IDLE) timer = ticks(cooldown);
		IllusionClient.MODULES.releaseSlot(this);
		previousSlot = -1;
		cell = null;
		stage = Stage.IDLE;
	}

	private static Vec3d center(BlockPos pos) {
		return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.4D, pos.getZ() + 0.5D);
	}

	private boolean replaceable(BlockPos pos) {
		return minecraft.world.getBlockState(pos).getBlock().getMaterial().isReplaceable();
	}

	private boolean waterSource(BlockPos pos) {
		BlockState state = minecraft.world.getBlockState(pos);
		return state.getBlock().getMaterial() == Material.WATER && state.get(LiquidBlock.LEVEL) == 0;
	}
}
