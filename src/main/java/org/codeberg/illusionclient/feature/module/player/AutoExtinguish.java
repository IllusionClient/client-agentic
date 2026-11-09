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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.input.InputEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.api.RotatingModule;
import org.codeberg.illusionclient.util.ItemUtils;
import org.codeberg.illusionclient.util.RotationUtils;

@ModuleInfo(name = "Auto Extinguish", category = Category.PLAYER)
@Description("Places water under you when you're on fire, then picks it back up.")
public class AutoExtinguish extends RotatingModule {
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

	private enum Stage { IDLE, PLACE, PLACED, PICKUP, CONFIRM }

	private Stage stage = Stage.IDLE;
	private BlockPos cell;
	private int previousSlot = -1;
	private int timer;
	private int confirmTicks;

	@Override
	public void onDisable() {
		finish();
		stopRotating();
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
				if (!aimed()) {
					if (aimTimedOut()) finish();
					return;
				}
				useHeldItem(player);
				timer = 0;
				stage = Stage.PLACED;
			}
			case PLACED -> {
				if (++timer < MAX_WAIT_TICKS && (timer < Math.max(ticks(pickupDelay), serverTicks()) || player.isOnFire() && player.isInWater())) return;
				timer = 0;
				stage = Stage.PICKUP;
				if (!waterSource(cell) || !aim(player, center(cell))) finish();
			}
			case PICKUP -> {
				if (!waterSource(cell) || !ItemUtils.holding(player, Items.BUCKET) || timer >= MAX_PICKUP_TRIES) {
					finish();
					return;
				}
				if (!aimed()) {
					if (aimTimedOut()) finish();
					return;
				}
				timer++;
				useHeldItem(player);
				release();
				confirmTicks = serverTicks();
				stage = Stage.CONFIRM;
			}
			case CONFIRM -> {
				if (--confirmTicks > 0) return;
				if (!waterSource(cell) || !aim(player, center(cell))) {
					finish();
					return;
				}
				stage = Stage.PICKUP;
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

		boolean above = MathHelper.floor(player.x) == cell.getX() && MathHelper.floor(player.z) == cell.getZ();
		aim(above ? new float[]{aimYaw(), 90.0F} : RotationUtils.rotationTo(eye, point));
		return true;
	}

	private void finish() {
		if (previousSlot != -1 && minecraft.player != null) minecraft.player.inventory.selectedSlot = previousSlot;
		if (stage != Stage.IDLE) timer = ticks(cooldown);
		IllusionClient.MODULES.releaseSlot(this);
		release();
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
