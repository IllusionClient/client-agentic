package org.codeberg.illusionclient.feature.module.combat;

import net.minecraft.block.LiquidBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.input.InputEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.client.Weapons;
import org.codeberg.illusionclient.util.CombatUtils;
import org.codeberg.illusionclient.util.RotationUtils;
import org.codeberg.illusionclient.util.rotation.RotationManager;

@ModuleInfo(name = "Auto Lava", category = Category.COMBAT)
@Description("Places lava from a bucket under opponents to burn and slow them, then picks it back up.")
public class AutoLava extends Module {
	private static final double BUCKET_REACH = 5.0D;
	private static final int MAX_PICKUP_TRIES = 5;
	private final Minecraft minecraft = Minecraft.getInstance();

	@Description("Only places under opponents this close.")
	@Slider(value = "Range", min = 1.0F, max = 4.5F)
	public float range = 4.0F;

	@Description("Only places under opponents within this many degrees of your crosshair.")
	@Slider(value = "FOV", min = 0.0F, max = 180.0F)
	public float fov = 60.0F;

	@Description("How long the lava stays before it's picked back up.")
	@Slider(value = "Pickup Delay (ms)", min = 50.0F, max = 2000.0F)
	public float pickupDelay = 300.0F;

	@Description("Time between placements.")
	@Slider(value = "Cooldown (ms)", min = 0.0F, max = 5000.0F)
	public float cooldown = 1000.0F;

	@Description("Only places while you're falling, so your critical hit overrides the lava damage. Pair with Combat Jump.")
	@Check("Wait For Critical")
	public boolean waitForCritical = false;

	@Description("Only places while holding a weapon.")
	@Check("Weapons Only")
	public boolean weaponsOnly = true;

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
				if (!holding(player, Items.LAVA_BUCKET)) {
					finish();
					return;
				}
				use(player);
				timer = ticks(pickupDelay);
				stage = Stage.PLACED;
			}
			case PLACED -> {
				if (--timer > 0) return;
				timer = 0;
				stage = Stage.PICKUP;
				if (!lavaSource(cell) || !aim(player, new Vec3d(cell.getX() + 0.5D, cell.getY() + 0.4D, cell.getZ() + 0.5D))) finish();
			}
			case PICKUP -> {
				if (!lavaSource(cell) || !holding(player, Items.BUCKET) || ++timer > MAX_PICKUP_TRIES) {
					finish();
					return;
				}
				use(player);
				aim(player, new Vec3d(cell.getX() + 0.5D, cell.getY() + 0.4D, cell.getZ() + 0.5D));
			}
		}
	}

	private void start(LocalClientPlayerEntity player) {
		if (minecraft.screen != null || !player.isAlive() || player.getItemInUse() != null) return;
		if (weaponsOnly && !Weapons.isHolding(player)) return;
		if (waitForCritical && (player.onGround || player.velocityY >= 0.0D)) return;

		int slot = slot(player.inventory, Items.LAVA_BUCKET);
		if (slot == -1) return;

		PlayerEntity target = CombatUtils.best(other -> other.isAlive() && other.onGround && CombatUtils.isTarget(other)
			&& CombatUtils.distanceTo(other) <= range
			&& RotationUtils.angleTo(other, player.yaw, player.pitch) <= fov, CombatUtils::distanceTo);
		if (target == null) return;

		BlockPos feet = new BlockPos(target);
		if (!replaceable(feet) || replaceable(feet.down()) || new Box(feet, feet.add(1, 1, 1)).intersects(player.getShape())) return;

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

	private boolean replaceable(BlockPos pos) {
		return minecraft.world.getBlockState(pos).getBlock().getMaterial().isReplaceable();
	}

	private boolean lavaSource(BlockPos pos) {
		BlockState state = minecraft.world.getBlockState(pos);
		return state.getBlock().getMaterial() == Material.LAVA && state.get(LiquidBlock.LEVEL) == 0;
	}

	private static boolean holding(LocalClientPlayerEntity player, Item item) {
		ItemStack held = player.inventory.getSelectedItem();
		return held != null && held.getItem() == item;
	}

	private static int slot(PlayerInventory inventory, Item item) {
		for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
			ItemStack stack = inventory.items[slot];
			if (stack != null && stack.getItem() == item) return slot;
		}
		return -1;
	}
}
