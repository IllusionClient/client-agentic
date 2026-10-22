package org.codeberg.illusionclient.util;

import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.item.ItemStack;

import java.util.function.Predicate;

public final class InventoryUtils {
	public static final int PLAYER_SLOTS = 36;
	public static final int MAIN_SLOTS = 27;
	private static final int ARMOR_START = 5;

	public static int playerStart(InventoryMenu menu) {
		return menu.slots.size() - PLAYER_SLOTS;
	}

	public static int hotbarStart(InventoryMenu menu) {
		return playerStart(menu) + MAIN_SLOTS;
	}

	public static int menuSlot(InventoryMenu menu, int inventoryIndex) {
		int hotbar = PlayerInventory.getHotbarSize();
		return inventoryIndex < hotbar ? hotbarStart(menu) + inventoryIndex : playerStart(menu) + inventoryIndex - hotbar;
	}

	public static int armorSlot(int armorItemSlot) {
		return ARMOR_START + armorItemSlot;
	}

	public static ItemStack item(InventoryMenu menu, int slot) {
		return menu.getSlot(slot).getItem();
	}

	public static int find(InventoryMenu menu, int from, int to, Predicate<ItemStack> filter) {
		for (int i = from; i < to; i++) {
			ItemStack stack = item(menu, i);
			if (stack != null && filter.test(stack)) return i;
		}
		return -1;
	}

	public static int firstEmpty(InventoryMenu menu, int from, int to) {
		for (int i = from; i < to; i++) {
			if (!menu.getSlot(i).hasItem()) return i;
		}
		return -1;
	}

	public static boolean hasEmpty(PlayerInventory inventory) {
		for (ItemStack stack : inventory.items) {
			if (stack == null) return true;
		}
		return false;
	}

	public static int count(InventoryMenu menu, int from, int to, Predicate<ItemStack> filter) {
		int count = 0;
		for (int i = from; i < to; i++) {
			ItemStack stack = item(menu, i);
			if (stack != null && filter.test(stack)) count += stack.size;
		}
		return count;
	}

	public static boolean isEmpty(InventoryMenu menu, int from, int to) {
		for (int i = from; i < to; i++) {
			if (menu.getSlot(i).hasItem()) return false;
		}
		return true;
	}
}
