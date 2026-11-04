package org.codeberg.illusionclient.feature.module.player;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.gui.screen.game.inventory.ChestScreen;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.menu.ChestMenu;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.CloseInventoryMenuS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryMenuContentS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenInventoryMenuS2CPacket;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.network.PacketReceiveEvent;
import org.codeberg.illusionclient.event.player.PlayerTickEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.RangeSlider;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Section;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.util.inventory.ClickTimer;
import org.codeberg.illusionclient.util.inventory.InventoryManager;

import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(name = "Chest Stealer", category = Category.PLAYER)
@Description("Takes items out of chests for you.")
public class ChestStealer extends Module {
	private static final int MAX_CLICKS_PER_TICK = 4;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final InventoryManager inventory = IllusionClient.INVENTORY;
	private final ClickTimer timer = new ClickTimer();

	@Section("Items")
	@Description("Nearest moves to the closest item like a mouse would. Slot goes in chest order. Random picks any.")
	@Combo(value = "Order", options = {"Nearest", "Slot", "Random"})
	public String order = "Nearest";

	@Description("Takes armor, weapons, tools and bows before anything else.")
	@Check("Gear First")
	public boolean gearFirst = true;

	@Description("Leaves items Inventory Cleaner would throw away, using its settings, even while it's off.")
	@Check("Skip Junk")
	public boolean skipJunk = true;

	@Description("Moves items straight into their empty Inventory Cleaner hotbar slot.")
	@Check("Hotbar Slots")
	public boolean hotbarSlots = true;

	@Description("Ignores chests with a custom name, which are usually server menus.")
	@Check("Real Chests Only")
	public boolean realChestsOnly = true;

	@Section("Timing")
	@Description("Time between taking two items.")
	@RangeSlider(value = "Delay (ms)", min = 0.0F, max = 500.0F)
	public float[] delay = {0.0F, 10.0F};

	@Description("Time to wait after the chest opens before taking the first item.")
	@RangeSlider(value = "Start Delay (ms)", min = 0.0F, max = 1000.0F)
	public float[] startDelay = {120.0F, 220.0F};

	@Description("Extra time to move the cursor to a far slot. Grows slower the farther it goes, like a real mouse.")
	@Slider(value = "Travel Delay (ms)", min = 0.0F, max = 200.0F, step = 1.0F)
	public float travelDelay = 130.0F;

	@Description("Chance after each item to stop for a moment.")
	@Slider(value = "Short Stop Chance (%)", min = 0.0F, max = 100.0F, step = 1.0F)
	public float shortStopChance = 0.0F;

	@Description("How long a short stop lasts.")
	@RangeSlider(value = "Short Stop Length (ms)", min = 0.0F, max = 1000.0F)
	public float[] shortStopLength = {350.0F, 650.0F};

	@Section("Mistakes")
	@Description("Chance to click an empty slot next to the item first, then pause.")
	@Slider(value = "Miss Click Chance (%)", min = 0.0F, max = 100.0F, step = 1.0F)
	public float missClickChance = 1.0F;

	@Description("Raises the miss click chance for items that are farther away.")
	@Check("Scale With Distance")
	public boolean missClickScale = true;

	@Description("How long to pause after a miss click.")
	@RangeSlider(value = "Miss Click Pause (ms)", min = 0.0F, max = 1000.0F)
	public float[] missClickPause = {200.0F, 300.0F};

	@Section("Closing")
	@Description("Closes the chest once there is nothing left to take or your inventory is full.")
	@Check("Auto Close")
	public boolean autoClose = true;

	@Description("Time to wait before closing the chest.")
	@Requires("autoClose")
	@RangeSlider(value = "Close Delay (ms)", min = 0.0F, max = 1000.0F)
	public float[] closeDelay = {60.0F, 90.0F};

	private int loadedMenu = -1;
	private int menu = -1;
	private boolean ignored;

	@Override
	public String details() {
		return order;
	}

	@Override
	public void onDisable() {
		reset();
	}

	@Listen
	public void onWorldLoad(WorldLoadEvent event) {
		loadedMenu = -1;
		reset();
	}

	@Listen
	public void onPacketReceive(PacketReceiveEvent event) {
		Object packet = event.packet();
		if (packet instanceof OpenInventoryMenuS2CPacket || packet instanceof CloseInventoryMenuS2CPacket) loadedMenu = -1;
		else if (packet instanceof InventoryMenuContentS2CPacket content && content.getMenuId() != 0) loadedMenu = content.getMenuId();
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || !(minecraft.screen instanceof ChestScreen) || !(player.menu instanceof ChestMenu chest)) {
			reset();
			return;
		}
		if (chest.networkId != loadedMenu) return;

		if (chest.networkId != menu) {
			menu = chest.networkId;
			ignored = realChestsOnly && chest.getChest().hasCustomName();
			timer.start(System.currentTimeMillis(), startDelay);
		}
		if (ignored || !inventory.claim(this) || player.inventory.getCursorItem() != null) return;

		InventoryCleaner cleaner = IllusionClient.MODULES.get(InventoryCleaner.class);
		for (int count = 0; count < MAX_CLICKS_PER_TICK; count++) {
			int slot = next(chest, player, cleaner);
			if (slot == -1) {
				if (autoClose && timer.closing(closeDelay)) player.closeMenu();
				return;
			}

			InventorySlot target = chest.getSlot(slot);
			if (!timer.ready(target, travelDelay)) return;

			int miss = missClick(chest, target);
			if (miss != -1) {
				inventory.pickup(miss);
				timer.clicked(chest.getSlot(miss), missClickPause);
				continue;
			}

			int hotbar = hotbarSlots ? cleaner.hotbarSlot(player.inventory, target.getItem()) : -1;
			if (hotbar != -1) inventory.swap(slot, hotbar);
			else inventory.quickMove(slot);
			timer.clicked(target, delay, shortStopChance, shortStopLength);
		}
	}

	private int next(ChestMenu chest, LocalClientPlayerEntity player, InventoryCleaner cleaner) {
		IntArrayList items = new IntArrayList();
		IntArrayList gear = new IntArrayList();
		int size = chest.getChest().getSize();
		for (int i = 0; i < size; i++) {
			ItemStack stack = chest.getSlot(i).getItem();
			if (stack == null || !fits(player.inventory, stack) || skipJunk && !cleaner.wants(player, stack)) continue;
			items.add(i);
			if (cleaner.isGear(stack)) gear.add(i);
		}

		IntArrayList pool = gearFirst && !gear.isEmpty() ? gear : items;
		if (pool.isEmpty()) return -1;

		return switch (order) {
			case "Random" -> pool.getInt(ThreadLocalRandom.current().nextInt(pool.size()));
			case "Nearest" -> nearest(chest, pool);
			default -> pool.getInt(0);
		};
	}

	private int nearest(ChestMenu chest, IntArrayList slots) {
		int nearest = slots.getInt(0);
		for (int slot : slots) {
			if (timer.distance(chest.getSlot(slot)) < timer.distance(chest.getSlot(nearest))) nearest = slot;
		}
		return nearest;
	}

	private int missClick(ChestMenu chest, InventorySlot target) {
		float chance = missClickChance * (missClickScale ? 1.0F + timer.distance(target) / 4.0F : 1.0F);
		if (chance <= 0.0F || ThreadLocalRandom.current().nextFloat() * 100.0F >= chance) return -1;

		int miss = -1;
		float closest = Float.MAX_VALUE;
		int size = chest.getChest().getSize();
		for (int i = 0; i < size; i++) {
			InventorySlot slot = chest.getSlot(i);
			if (slot.hasItem()) continue;

			float distance = (float) Math.hypot(slot.x - target.x, slot.y - target.y);
			if (distance < closest) {
				closest = distance;
				miss = i;
			}
		}
		return miss;
	}

	private static boolean fits(PlayerInventory playerInventory, ItemStack stack) {
		int room = 0;
		for (ItemStack own : playerInventory.items) {
			if (own == null) return true;
			if (own.isStackable() && own.matchesItem(stack) && ItemStack.matchesNbt(own, stack)) room += own.getMaxSize() - own.size;
		}
		return room >= stack.size;
	}

	private void reset() {
		menu = -1;
		timer.reset();
	}
}
