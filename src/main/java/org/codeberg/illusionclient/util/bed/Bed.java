package org.codeberg.illusionclient.util.bed;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.FireBlock;
import net.minecraft.block.FlowerPotBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.TripwireBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import org.codeberg.illusionclient.util.BlockUtils;

public final class Bed {
	private static final int[] FACING_X = {0, -1, 0, 1};
	private static final int[] FACING_Z = {1, 0, -1, 0};
	private static final int[] STEP_X = {1, -1, 0, 0, 0, 0};
	private static final int[] STEP_Y = {0, 0, 0, 0, 1, -1};
	private static final int[] STEP_Z = {0, 0, 1, -1, 0, 0};
	private static final int UP = 4;
	private static final int DOWN = 5;
	private static final byte NEVER = 0, ALWAYS = 1, NOT_BELOW = 2, SIDES = 3;
	private static final int[] NONE = new int[0];

	public final int x, y, z;
	public final int footX, footZ;
	public final int facing;
	private int[] cover = NONE;
	private boolean exposed;
	private int revision;
	boolean dirty = true;

	Bed(int x, int y, int z, int facing) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.facing = facing;
		this.footX = x - FACING_X[facing];
		this.footZ = z - FACING_Z[facing];
	}

	public double centerX() {
		return (x + footX) / 2.0D + 0.5D;
	}

	public double centerZ() {
		return (z + footZ) / 2.0D + 0.5D;
	}

	public int distance(int x, int y, int z) {
		int dx = Math.max(0, Math.max(Math.min(this.x, footX) - x, x - Math.max(this.x, footX)));
		int dz = Math.max(0, Math.max(Math.min(this.z, footZ) - z, z - Math.max(this.z, footZ)));
		return dx + Math.abs(y - this.y) + dz;
	}

	public boolean exposed() {
		refresh();
		return exposed;
	}

	public int[] cover() {
		refresh();
		return cover;
	}

	public int revision() {
		refresh();
		return revision;
	}

	private void refresh() {
		World world = Minecraft.getInstance().world;
		if (!dirty || world == null) return;
		dirty = false;
		revision++;

		int columnX = footX, columnZ = footZ;
		if (BlockUtils.state(world, columnX, y + 1, columnZ) == 0) {
			columnX = x;
			columnZ = z;
			exposed = true;
		} else {
			exposed = exposed(world, footX, y, footZ) || exposed(world, x, y, z);
		}

		IntArrayList states = new IntArrayList();
		for (int above = y + 1; above <= 255; above++) {
			int state = BlockUtils.state(world, columnX, above, columnZ);
			if (state == 0) break;
			states.add(state);
		}
		cover = states.isEmpty() ? NONE : states.toIntArray();
	}

	private static boolean exposed(World world, int x, int y, int z) {
		for (int face = 0; face < 6; face++) {
			int id = BlockUtils.id(BlockUtils.state(world, x + STEP_X[face], y + STEP_Y[face], z + STEP_Z[face]));
			switch (Lookup.EXPOSURE[id]) {
				case ALWAYS -> {
					return true;
				}
				case NOT_BELOW -> {
					if (face != DOWN) return true;
				}
				case SIDES -> {
					if (face < UP) return true;
				}
				default -> {
				}
			}
		}
		return false;
	}

	private static byte exposure(Block block) {
		if (block == Blocks.AIR || block == Blocks.IRON_BARS) return ALWAYS;
		if (block == Blocks.END_PORTAL_FRAME) return SIDES;
		if (block instanceof SlabBlock slab) return slab.isDouble() ? NEVER : NOT_BELOW;
		return block instanceof LiquidBlock || block instanceof FireBlock || block instanceof LadderBlock
			|| block instanceof EnderChestBlock || block instanceof EnchantingTableBlock || block instanceof SkullBlock
			|| block instanceof AnvilBlock || block instanceof TripwireBlock || block instanceof VineBlock
			|| block instanceof FlowerPotBlock || block instanceof ChestBlock || block instanceof DoorBlock
			|| block instanceof TrapdoorBlock || block instanceof FenceBlock || block instanceof FenceGateBlock
			|| block instanceof WallBlock || block instanceof StairsBlock ? ALWAYS : NEVER;
	}

	private static final class Lookup {
		static final byte[] EXPOSURE = new byte[4096];

		static {
			for (int id = 0; id < EXPOSURE.length; id++) EXPOSURE[id] = exposure(Block.byId(id));
		}
	}
}
