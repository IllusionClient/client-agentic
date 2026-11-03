package org.codeberg.illusionclient.util;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
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

	public static int bestBlockSlot(PlayerInventory inventory, Block preferred) {
		int best = -1;
		int bestPriority = -1;
		for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
			if (preferred != null && block(inventory.items[slot]) == preferred) return slot;
			int priority = blockPriority(inventory.items[slot]);
			if (priority > bestPriority || priority == bestPriority && priority >= 0 && slot == inventory.selectedSlot) {
				best = slot;
				bestPriority = priority;
			}
		}
		return best;
	}

	public static boolean holding(PlayerEntity player, Item item) {
		ItemStack held = player.inventory.getSelectedItem();
		return held != null && held.getItem() == item;
	}

	public static int hotbarSlot(PlayerInventory inventory, Item item) {
		for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
			ItemStack stack = inventory.items[slot];
			if (stack != null && stack.getItem() == item) return slot;
		}
		return -1;
	}
}
