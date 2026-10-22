package org.codeberg.illusionclient.util;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;

public final class ItemUtils {
	public static boolean isBlock(ItemStack stack) {
		return stack != null && stack.getItem() instanceof BlockItem;
	}

	public static Block block(ItemStack stack) {
		return isBlock(stack) ? ((BlockItem) stack.getItem()).getBlock() : null;
	}

	public static int blockPriority(ItemStack stack) {
		Block block = block(stack);
		if (block == null) return -1;
		if (block == Blocks.STONE || block == Blocks.GRASS || block == Blocks.DIRT || block == Blocks.WOOL || block == Blocks.BRICKS) return 2;
		if (block == Blocks.PLANKS || block == Blocks.LOG || block == Blocks.LOG2 || block == Blocks.GLASS || block == Blocks.SANDSTONE
			|| block == Blocks.NETHERRACK || block == Blocks.END_STONE || block == Blocks.STAINED_HARDENED_CLAY) return 1;
		return -1;
	}
}
