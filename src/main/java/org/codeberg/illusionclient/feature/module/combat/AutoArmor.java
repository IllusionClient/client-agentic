package org.codeberg.illusionclient.feature.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.player.PlayerTickEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.RangeSlider;
import org.codeberg.illusionclient.feature.config.api.Section;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.util.inventory.ClickTimer;
import org.codeberg.illusionclient.util.inventory.InventoryManager;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Map;

@ModuleInfo(name = "Auto Armor", category = Category.COMBAT)
@Description("Puts on the armor set that protects you the most.")
public class AutoArmor extends Module {
	public static final int ARMOR_START = 5;
	private static final int PIECES = 4;
	private static final int HOTBAR = 9;
	private static final int MAX_CLICKS_PER_TICK = 4;
	private static final int ARMOR_SCALE = 25;
	private static final int MAX_ENCHANT_PROTECTION = 25;
	private static final int MAX_ENCHANT_REDUCTION = 20;
	private static final Piece NONE = new Piece(-1, 0, 0, 0);
	private final Minecraft minecraft = Minecraft.getInstance();
	private final InventoryManager inventory = IllusionClient.INVENTORY;
	private final ClickTimer timer = new ClickTimer();

	@Section("Inventory")
	@Description("Spoof opens your inventory without showing it. Open only swaps armor while you have it open.")
	@Combo(value = "Inventory", options = {"Spoof", "Open"})
	public String mode = "Spoof";

	@Description("Waits while you press movement keys with no screen open.")
	@Check("Stop While Moving")
	public boolean stopWhileMoving = false;

	@Description("Keep puts the armor you take off where the new piece was. Drop throws it away.")
	@Combo(value = "Old Armor", options = {"Keep", "Drop"})
	public String oldArmor = "Keep";

	@Description("Puts on armor from your hotbar by right-clicking it, without opening your inventory.")
	@Check("Hotbar")
	public boolean hotbar = true;

	@Section("Timing")
	@Description("Time between two clicks or hotbar equips.")
	@RangeSlider(value = "Delay (ms)", min = 0.0F, max = 500.0F)
	public float[] delay = {80.0F, 120.0F};

	@Description("Time to wait after the inventory opens before the first click.")
	@RangeSlider(value = "Start Delay (ms)", min = 0.0F, max = 1000.0F)
	public float[] startDelay = {100.0F, 200.0F};

	@Description("Extra time to move the cursor to a far slot. Grows slower the farther it goes, like a real mouse.")
	@Slider(value = "Travel Delay (ms)", min = 0.0F, max = 200.0F, step = 1.0F)
	public float travelDelay = 25.0F;

	@Description("Time to wait after the last click before closing a spoofed inventory.")
	@RangeSlider(value = "Close Delay (ms)", min = 0.0F, max = 1000.0F)
	public float[] closeDelay = {100.0F, 200.0F};

	private final ArrayDeque<int[]> queue = new ArrayDeque<>();
	private int restoreSlot = -1;
	private long hotbarAt;

	private record Piece(int slot, int armor, int enchants, int durability) {
	}

	@Override
	public String details() {
		return mode;
	}

	@Override
	public void onDisable() {
		restoreHotbar();
		queue.clear();
		timer.reset();
		hotbarAt = 0L;
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || restoreHotbar()) return;

		InventoryMenu menu = player.playerMenu;
		if (queue.isEmpty() && hotbar && minecraft.screen == null && equipFromHotbar(player, menu)) return;
		if (!inventory.usable(mode.equals("Spoof"), stopWhileMoving && queue.isEmpty())) {
			queue.clear();
			timer.reset();
			return;
		}
		if (queue.isEmpty()) plan(player, menu);
		if (queue.isEmpty()) {
			timer.reset();
			return;
		}
		if (!inventory.claim(this)) {
			queue.clear();
			return;
		}

		inventory.openInventory(closeDelay);
		if (!timer.started()) timer.start(System.currentTimeMillis() - inventory.sinceOpen(), startDelay);
		for (int count = 0; count < MAX_CLICKS_PER_TICK && !queue.isEmpty(); count++) {
			int[] click = queue.peek();
			InventorySlot slot = slot(menu, click[0]);
			if (!timer.ready(slot, travelDelay)) return;

			queue.poll();
			inventory.click(click[0], click[1], click[2]);
			timer.clicked(slot, delay);
			if (queue.isEmpty()) plan(player, menu);
		}
	}

	private void plan(LocalClientPlayerEntity player, InventoryMenu menu) {
		if (player.inventory.getCursorItem() != null) return;

		int[] best = bestSet(player);
		int type = -1;
		for (int i = 0; i < PIECES; i++) {
			if (best[i] == -1 || best[i] == ARMOR_START + i) continue;
			if (type == -1 || timer.distance(menu.getSlot(best[i])) < timer.distance(menu.getSlot(best[type]))) type = i;
		}
		if (type == -1) return;

		int source = best[type];
		int worn = ARMOR_START + type;
		if (!menu.getSlot(worn).hasItem()) {
			queue.add(new int[]{source, 0, InventoryManager.QUICK_MOVE});
		} else if (oldArmor.equals("Drop")) {
			queue.add(new int[]{worn, 1, InventoryManager.THROW});
			queue.add(new int[]{source, 0, InventoryManager.QUICK_MOVE});
		} else {
			queue.add(new int[]{source, 0, InventoryManager.PICKUP});
			queue.add(new int[]{worn, 0, InventoryManager.PICKUP});
			queue.add(new int[]{source, 0, InventoryManager.PICKUP});
		}
	}

	private boolean equipFromHotbar(LocalClientPlayerEntity player, InventoryMenu menu) {
		if (player.getItemInUse() != null || inventory.open() || minecraft.interactionManager.hasCreativeInventory()) {
			hotbarAt = 0L;
			return false;
		}

		int[] best = bestSet(player);
		int hotbarStart = menu.slots.size() - HOTBAR;
		for (int type = 0; type < PIECES; type++) {
			int slot = best[type];
			if (slot < hotbarStart || menu.getSlot(ARMOR_START + type).hasItem()) continue;

			long now = System.currentTimeMillis();
			if (hotbarAt == 0L) hotbarAt = now + ClickTimer.roll(delay);
			if (now < hotbarAt || !IllusionClient.MODULES.claimSlot(this)) return true;

			restoreSlot = player.inventory.selectedSlot;
			player.inventory.selectedSlot = slot - hotbarStart;
			minecraft.interactionManager.useItem(player, minecraft.world, menu.getSlot(slot).getItem());
			hotbarAt = 0L;
			return true;
		}
		hotbarAt = 0L;
		return false;
	}

	private boolean restoreHotbar() {
		if (restoreSlot == -1) return false;
		if (minecraft.player != null) minecraft.player.inventory.selectedSlot = restoreSlot;
		restoreSlot = -1;
		IllusionClient.MODULES.releaseSlot(this);
		return true;
	}

	private static InventorySlot slot(InventoryMenu menu, int slot) {
		return slot == InventoryManager.OUTSIDE ? null : menu.getSlot(slot);
	}

	public static int[] bestSet(PlayerEntity player) {
		Piece[] set = bestPieces(player.playerMenu, DamageSource.player(player));
		int[] slots = new int[PIECES];
		for (int type = 0; type < PIECES; type++) slots[type] = set[type].slot;
		return slots;
	}

	public static boolean improves(PlayerEntity player, ItemStack stack) {
		if (!(stack.getItem() instanceof ArmorItem armor)) return false;
		DamageSource damage = DamageSource.player(player);
		Piece[] set = bestPieces(player.playerMenu, damage);
		Piece[] with = set.clone();
		with[armor.slot] = piece(-1, armor, stack, damage);
		return compare(with, set) < 0;
	}

	private static Piece[] bestPieces(InventoryMenu menu, DamageSource damage) {
		Piece[][] candidates = new Piece[PIECES][];
		for (int type = 0; type < PIECES; type++) {
			int count = 0;
			Piece[] pieces = new Piece[menu.slots.size()];
			for (int i = ARMOR_START; i < menu.slots.size(); i++) {
				ItemStack stack = menu.getSlot(i).getItem();
				if (stack != null && stack.getItem() instanceof ArmorItem armor && armor.slot == type) pieces[count++] = piece(i, armor, stack, damage);
			}
			candidates[type] = count == 0 ? new Piece[]{NONE} : Arrays.copyOf(pieces, count);
		}

		Piece[] best = null;
		for (Piece helmet : candidates[0]) {
			for (Piece chestplate : candidates[1]) {
				for (Piece leggings : candidates[2]) {
					for (Piece boots : candidates[3]) {
						Piece[] set = {helmet, chestplate, leggings, boots};
						if (best == null || compare(set, best) < 0) best = set;
					}
				}
			}
		}
		return best;
	}

	private static int compare(Piece[] first, Piece[] second) {
		int order = Float.compare(taken(first), taken(second));
		if (order == 0) order = Integer.compare(durability(second), durability(first));
		if (order == 0) order = Integer.compare(changes(first), changes(second));
		return order;
	}

	private static float taken(Piece[] set) {
		int armor = 0;
		int enchants = 0;
		for (Piece piece : set) {
			armor += piece.armor;
			enchants += piece.enchants;
		}
		return (ARMOR_SCALE - Math.min(armor, ARMOR_SCALE)) / (float) ARMOR_SCALE * enchantMultiplier(enchants);
	}

	private static int durability(Piece[] set) {
		int durability = 0;
		for (Piece piece : set) durability += piece.durability;
		return durability;
	}

	private static int changes(Piece[] set) {
		int changes = 0;
		for (int type = 0; type < PIECES; type++) {
			if (set[type].slot != ARMOR_START + type) changes++;
		}
		return changes;
	}

	private static Piece piece(int slot, ArmorItem armor, ItemStack stack, DamageSource damage) {
		int enchants = 0;
		for (Map.Entry<Integer, Integer> entry : EnchantmentHelper.getEnchantments(stack).entrySet()) {
			Enchantment enchantment = Enchantment.byId(entry.getKey());
			if (enchantment != null) enchants += enchantment.getExtraProtection(entry.getValue(), damage);
		}
		return new Piece(slot, armor.protection, enchants, stack.getMaxDamage() - stack.getDamage());
	}

	private static float enchantMultiplier(int enchants) {
		int capped = Math.min(enchants, MAX_ENCHANT_PROTECTION);
		int base = capped + 1 >> 1;
		int rolls = (capped >> 1) + 1;
		float total = 0.0F;
		for (int roll = 0; roll < rolls; roll++) total += ARMOR_SCALE - Math.min(base + roll, MAX_ENCHANT_REDUCTION);
		return total / rolls / ARMOR_SCALE;
	}
}
