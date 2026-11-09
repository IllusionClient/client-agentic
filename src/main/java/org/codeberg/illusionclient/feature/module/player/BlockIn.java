package org.codeberg.illusionclient.feature.module.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.ItemStack;
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
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.RangeSlider;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.api.RotatingModule;
import org.codeberg.illusionclient.util.CombatUtils;
import org.codeberg.illusionclient.util.ItemUtils;
import org.codeberg.illusionclient.util.RotationUtils;
import org.codeberg.illusionclient.util.SimulatedPlayer;
import org.codeberg.illusionclient.util.input.InputState;

import java.util.Random;

@ModuleInfo(name = "Block In", category = Category.PLAYER)
@Description("Surrounds you with blocks and a roof, starting on the side facing your opponent.")
public class BlockIn extends RotatingModule {
	private static final double FACE_SPREAD = 0.35D;
	private static final int MAX_JUMPS = 3;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final Random random = new Random();

	@Description("Toggle turns itself off once you're blocked in. Auto blocks you in whenever an opponent gets close while you stand still.")
	@Combo(value = "Mode", options = {"Toggle", "Auto"})
	public String mode = "Toggle";

	@Description("How close an opponent has to be before blocking in.")
	@Requires(value = "mode", is = "Auto")
	@Slider(value = "Trigger Range", min = 1.0F, max = 12.0F)
	public float triggerRange = 6.0F;

	@Description("Time between placements.")
	@RangeSlider(value = "Place Delay (ms)", min = 0.0F, max = 500.0F)
	public float[] placeDelay = {50.0F, 100.0F};

	@Description("Varies where on each face blocks are placed.")
	@Check("Randomize")
	public boolean randomize = true;

	@Description("Swaps back to your previous slot when done.")
	@Check("Swap Back")
	public boolean swapBack = true;

	private boolean pending;
	private boolean active;
	private int cooldown;
	private int previousSlot = -1;
	private Direction[] sides;
	private BlockPos target;
	private BlockPos against;
	private Direction face;
	private Vec3d aim;
	private BlockPos origin;
	private int jumps;

	public BlockIn() {
		turnMode = "Regular";
		horizontalSpeed = 80.0F;
		verticalSpeed = 80.0F;
	}

	@Override
	public String details() {
		return mode;
	}

	@Override
	public void onEnable() {
		pending = mode.equals("Toggle");
	}

	@Override
	public void onDisable() {
		if (active) stop();
		pending = false;
		stopRotating();
	}

	@Listen
	public void onWorldLoad(WorldLoadEvent event) {
		onDisable();
	}

	@Listen
	public void onInput(InputEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (!active && (pending || triggered(player))) {
			if (findPlacement(player, true)) start(player);
			else pending = false;
		}
		if (active) {
			if (cooldown > 0) cooldown--;
			boolean found = (pending || triggered(player)) && findPlacement(player, false) && holdBlocks(player);
			HitResult hit = found && rotating() && cooldown == 0 && minecraft.screen == null ? aimedHit() : null;
			if (hits(hit, against, face) && place(player, hit.facePos)) found = findPlacement(player, false) && holdBlocks(player);
			if (!found) stop();
		}
		if (active) aim(RotationUtils.rotationTo(nextEye(player), aim));
		else release();
		if (mode.equals("Toggle") && !pending && !active && !rotating()) setEnabled(false);
	}

	private boolean triggered(LocalClientPlayerEntity player) {
		if (!mode.equals("Auto") || minecraft.screen != null || !(player.onGround || active)) return false;
		InputState input = IllusionClient.INPUT.player;
		if (input.forwardAxis() != 0.0F || input.sidewaysAxis() != 0.0F || input.jump) return false;
		return CombatUtils.best(other -> CombatUtils.isTarget(other) && CombatUtils.distanceTo(other) <= triggerRange, CombatUtils::distanceTo) != null;
	}

	private void start(LocalClientPlayerEntity player) {
		if (!IllusionClient.MODULES.claimSlot(this)) return;
		previousSlot = player.inventory.selectedSlot;
		PlayerEntity opponent = CombatUtils.best(CombatUtils::isTarget, CombatUtils::distanceTo);
		Direction front = opponent == null ? player.getHorizontalFacing()
			: Direction.byRotation(RotationUtils.rotationTo(new Vec3d(opponent.x, opponent.y, opponent.z))[0]);
		sides = new Direction[]{front, front.clockwiseY(), front.counterClockwiseY(), front.getOpposite()};
		target = null;
		origin = new BlockPos(player);
		jumps = cooldown = 0;
		active = true;
		findPlacement(player, false);
	}

	private void stop() {
		if (swapBack && previousSlot != -1) minecraft.player.inventory.selectedSlot = previousSlot;
		IllusionClient.MODULES.releaseSlot(this);
		previousSlot = -1;
		active = pending = false;
	}

	private boolean findPlacement(LocalClientPlayerEntity player, boolean any) {
		BlockPos feet = any ? new BlockPos(player) : origin;
		if (any && !hasBlocks(player.inventory)) return false;
		Direction[] order = any ? new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST} : sides;
		for (Direction side : order) {
			if (canTarget(player, feet.offset(side)) || canTarget(player, feet.up().offset(side))) return true;
		}
		return roof(player, feet.up(2), order);
	}

	private boolean roof(LocalClientPlayerEntity player, BlockPos roof, Direction[] order) {
		if (!replaceable(roof)) return false;
		if (canTarget(player, roof)) return true;
		for (Direction side : order) {
			if (replaceable(roof.offset(side))) continue;
			if (player.onGround) return false;

			against = null;
			aim = new Vec3d(roof.getX() + 0.5D, roof.getY(), roof.getZ() + 0.5D);
			return true;
		}

		for (Direction side : order) {
			if (canTarget(player, roof.offset(side))) return true;
		}
		BlockPos wall = roof.down().offset(order[0]);
		against = null;
		aim = new Vec3d(wall.getX() + 0.5D, wall.getY() + 1.0D, wall.getZ() + 0.5D);
		if (!player.onGround) return true;
		if (jumps >= MAX_JUMPS) return false;

		jumps++;
		IllusionClient.INPUT.client.jump = true;
		return true;
	}

	private boolean canTarget(LocalClientPlayerEntity player, BlockPos cell) {
		if (!replaceable(cell) || new Box(cell, cell.add(1, 1, 1)).intersects(player.getShape())) return false;
		if (cell.equals(target) && visible(player, against, face, aim)) return true;

		for (Direction direction : Direction.values()) {
			BlockPos support = cell.offset(direction);
			if (replaceable(support)) continue;

			Direction side = direction.getOpposite();
			Vec3d point = facePoint(support, side);
			if (!visible(player, support, side, point)) continue;

			target = cell;
			against = support;
			face = side;
			aim = point;
			return true;
		}
		return false;
	}

	private boolean replaceable(BlockPos pos) {
		return minecraft.world.getBlockState(pos).getBlock().getMaterial().isReplaceable();
	}

	private Vec3d facePoint(BlockPos support, Direction side) {
		return new Vec3d(
			support.getX() + 0.5D + spread(side.getOffsetX()),
			support.getY() + 0.5D + spread(side.getOffsetY()),
			support.getZ() + 0.5D + spread(side.getOffsetZ()));
	}

	private double spread(int offset) {
		if (offset != 0) return offset * 0.5D;
		return randomize ? (random.nextDouble() * 2.0D - 1.0D) * FACE_SPREAD : 0.0D;
	}

	private boolean visible(LocalClientPlayerEntity player, BlockPos support, Direction side, Vec3d point) {
		Vec3d eye = player.getEyePosition(1.0F);
		double reach = minecraft.interactionManager.getReach();
		if (eye.squaredDistanceTo(point) > reach * reach) return false;

		Vec3d end = point.add(side.getOffsetX() * -0.01D, side.getOffsetY() * -0.01D, side.getOffsetZ() * -0.01D);
		return hits(minecraft.world.rayTrace(eye, end), support, side);
	}

	private static boolean hits(HitResult hit, BlockPos pos, Direction side) {
		return hit != null && hit.type == HitResult.Type.BLOCK && hit.face == side && hit.getPos().equals(pos);
	}

	private boolean place(LocalClientPlayerEntity player, Vec3d hitPos) {
		PlayerInventory inventory = player.inventory;
		ItemStack stack = inventory.getSelectedItem();
		if (!minecraft.interactionManager.useBlock(player, minecraft.world, stack, against, face, hitPos)) return false;

		player.swingArm();
		if (stack.size == 0) inventory.items[inventory.selectedSlot] = null;
		float delay = placeDelay[0] + random.nextFloat() * (placeDelay[1] - placeDelay[0]);
		cooldown = ticks(delay);
		target = null;
		return true;
	}

	private Vec3d nextEye(LocalClientPlayerEntity player) {
		InputState input = IllusionClient.INPUT.client;
		SimulatedPlayer simulated = SimulatedPlayer.of(player);
		simulated.forward = input.forwardAxis();
		simulated.sideways = input.sidewaysAxis();
		simulated.jumping = input.jump;
		simulated.sneaking = input.sneak;
		simulated.tick();
		return simulated.eyePosition();
	}

	private static boolean usable(ItemStack stack) {
		return ItemUtils.isBlock(stack) && stack.size > 0
			&& (ItemUtils.blockPriority(stack) >= 0 || ItemUtils.block(stack).isOpaque());
	}

	private static boolean hasBlocks(PlayerInventory inventory) {
		for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
			if (usable(inventory.items[slot])) return true;
		}
		return false;
	}

	private static boolean holdBlocks(LocalClientPlayerEntity player) {
		PlayerInventory inventory = player.inventory;
		if (usable(inventory.getSelectedItem())) return true;

		int best = -1;
		for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
			ItemStack stack = inventory.items[slot];
			if (usable(stack) && (best == -1 || stack.size > inventory.items[best].size)) best = slot;
		}
		if (best == -1) return false;
		inventory.selectedSlot = best;
		return true;
	}
}
