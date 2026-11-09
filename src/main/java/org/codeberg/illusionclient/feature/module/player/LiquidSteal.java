package org.codeberg.illusionclient.feature.module.player;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.input.InputEvent;
import org.codeberg.illusionclient.event.network.PacketSendEvent;
import org.codeberg.illusionclient.event.world.BlockChangeEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.api.RotatingModule;
import org.codeberg.illusionclient.util.CombatUtils;
import org.codeberg.illusionclient.util.ItemUtils;
import org.codeberg.illusionclient.util.RotationUtils;

@ModuleInfo(name = "Liquid Steal", category = Category.PLAYER)
@Description("Picks up water and lava your opponents place with an empty bucket from your hotbar.")
public class LiquidSteal extends RotatingModule {
	private static final double BUCKET_REACH = 5.0D;
	private static final double OWNER_RANGE = 6.0D;
	private static final long FORGET_MS = 10_000L;
	private static final int MAX_PICKUP_TRIES = 5;
	private final Minecraft minecraft = Minecraft.getInstance();

	@Description("Steals water.")
	@Check("Water")
	public boolean water = true;

	@Description("Steals lava.")
	@Check("Lava")
	public boolean lava = true;

	@Description("How long to wait after a liquid is placed before picking it up.")
	@Slider(value = "Delay (ms)", min = 0.0F, max = 1000.0F)
	public float delay = 100.0F;

	private final Object2LongMap<BlockPos> placed = new Object2LongOpenHashMap<>();
	private BlockPos cell;
	private int previousSlot = -1;
	private int tries;
	private int ownUseTick = -1;

	@Override
	public void onDisable() {
		finish();
		placed.clear();
		stopRotating();
	}

	@Listen
	public void onWorldLoad(WorldLoadEvent event) {
		finish();
		placed.clear();
	}

	@Listen
	public void onPacketSend(PacketSendEvent event) {
		if (event.packet() instanceof PlayerUseC2SPacket && minecraft.player != null) ownUseTick = minecraft.player.ticks;
	}

	@Listen
	public void onBlockChange(BlockChangeEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || player.ticks == ownUseTick || !stealable(event.newState())) return;

		BlockPos pos = new BlockPos(event.pos());
		PlayerEntity owner = CombatUtils.best(other -> other.isAlive() && CombatUtils.isTarget(other)
			&& other.getSquaredDistanceToCenter(pos) <= OWNER_RANGE * OWNER_RANGE, other -> other.getSquaredDistanceToCenter(pos));
		if (owner != null) placed.put(pos, System.currentTimeMillis());
	}

	@Listen
	public void onInput(InputEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || minecraft.world == null) return;

		if (cell == null) {
			start(player);
			return;
		}

		if (!stealable(minecraft.world.getBlockState(cell)) || !ItemUtils.holding(player, Items.BUCKET) || tries >= MAX_PICKUP_TRIES) {
			finish();
			return;
		}
		if (!aimed()) {
			if (aimTimedOut()) finish();
			return;
		}
		tries++;
		useHeldItem(player);
		aim(player, center(cell));
	}

	private void start(LocalClientPlayerEntity player) {
		long now = System.currentTimeMillis();
		placed.object2LongEntrySet().removeIf(entry -> now - entry.getLongValue() > FORGET_MS
			|| !stealable(minecraft.world.getBlockState(entry.getKey())));
		if (placed.isEmpty() || minecraft.screen != null || !player.isAlive() || player.getItemInUse() != null) return;

		int slot = ItemUtils.hotbarSlot(player.inventory, Items.BUCKET);
		if (slot == -1) return;

		BlockPos target = null;
		for (Object2LongMap.Entry<BlockPos> entry : placed.object2LongEntrySet()) {
			if (now - entry.getLongValue() >= delay && visible(player, entry.getKey())) {
				target = entry.getKey();
				break;
			}
		}
		if (target == null || !IllusionClient.MODULES.claimSlot(this)) return;

		cell = target;
		tries = 0;
		previousSlot = player.inventory.selectedSlot;
		player.inventory.selectedSlot = slot;
		aim(player, center(cell));
	}

	private boolean visible(LocalClientPlayerEntity player, BlockPos pos) {
		Vec3d eye = player.getEyePosition(1.0F);
		Vec3d point = center(pos);
		if (eye.distanceTo(point) > BUCKET_REACH) return false;

		HitResult hit = minecraft.world.rayTrace(eye, point, true);
		return hit != null && hit.type == HitResult.Type.BLOCK && hit.getPos().equals(pos);
	}

	private void aim(LocalClientPlayerEntity player, Vec3d point) {
		aim(RotationUtils.rotationTo(player.getEyePosition(1.0F), point));
	}

	private void finish() {
		if (previousSlot != -1 && minecraft.player != null) minecraft.player.inventory.selectedSlot = previousSlot;
		IllusionClient.MODULES.releaseSlot(this);
		release();
		if (cell != null) placed.removeLong(cell);
		previousSlot = -1;
		cell = null;
	}

	private static Vec3d center(BlockPos pos) {
		return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.4D, pos.getZ() + 0.5D);
	}

	private boolean stealable(BlockState state) {
		Material material = state.getBlock().getMaterial();
		return (water && material == Material.WATER || lava && material == Material.LAVA) && state.get(LiquidBlock.LEVEL) == 0;
	}
}
