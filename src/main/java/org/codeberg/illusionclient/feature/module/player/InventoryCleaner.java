package org.codeberg.illusionclient.feature.module.player;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.FoodItem;
import net.minecraft.item.GoldenAppleItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.PotionItem;
import net.minecraft.item.ShearsItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolItem;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.player.PlayerTickEvent;
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
import org.codeberg.illusionclient.feature.module.combat.AutoArmor;
import org.codeberg.illusionclient.util.inventory.ClickTimer;
import org.codeberg.illusionclient.util.inventory.InventoryManager;

import java.util.List;
import java.util.Map;

@ModuleInfo(name = "Inventory Cleaner", category = Category.PLAYER)
@Description("Drops junk and worse gear, merges stacks and sorts your hotbar.")
public class InventoryCleaner extends Module {
	private static final int PLAYER_SLOTS = 36;
	private static final int HOTBAR = 9;
	private static final int MAX_CLICKS_PER_TICK = 4;
	private static final int PUFFERFISH = 3;
	private static final String NONE = "None";
	private static final String[] ARMOR = {"Helmet", "Chestplate", "Leggings", "Boots"};
	private static final String[] SLOT_KINDS = {
		NONE, "Sword", "Bow", "Pickaxe", "Axe", "Shovel", "Block", "Food", "Golden Apple", "Pearl", "Potion", "Soup",
		"Throwable", "Fishing Rod", "Water", "Lava", "Bucket", "Flint and Steel", "TNT", "Shears"
	};
	private final Minecraft minecraft = Minecraft.getInstance();
	private final InventoryManager inventory = IllusionClient.INVENTORY;
	private final ClickTimer timer = new ClickTimer();

	@Section("Inventory")
	@Description("Spoof opens your inventory without showing it. Open only cleans while you have it open.")
	@Combo(value = "Inventory", options = {"Spoof", "Open"})
	public String mode = "Spoof";

	@Description("Waits while you press movement keys with no screen open.")
	@Check("Stop While Moving")
	public boolean stopWhileMoving = false;

	@Section("Items")
	@Description("Drops junk, worse gear and stacks over the limits below.")
	@Check("Drop")
	public boolean drop = true;

	@Description("Joins half-empty stacks of the same item to free up slots.")
	@Check("Merge Stacks")
	public boolean merge = true;

	@Description("Keeps harmful splash potions for throwing at others.")
	@Check("Harmful Splash Potions")
	public boolean harmfulSplash = true;

	@Description("How many stacks of building blocks to keep.")
	@Slider(value = "Block Stacks", min = 0.0F, max = 36.0F, step = 1.0F)
	public float blockStacks = 5.0F;

	@Description("How many stacks of food to keep. Golden apples and soups are always kept.")
	@Slider(value = "Food Stacks", min = 0.0F, max = 36.0F, step = 1.0F)
	public float foodStacks = 2.0F;

	@Description("How many stacks of eggs and snowballs to keep.")
	@Slider(value = "Throwable Stacks", min = 0.0F, max = 36.0F, step = 1.0F)
	public float throwableStacks = 2.0F;

	@Description("How many fishing rods to keep.")
	@Slider(value = "Fishing Rods", min = 0.0F, max = 9.0F, step = 1.0F)
	public float fishingRods = 1.0F;

	@Section("Hotbar")
	@Description("Moves your best items into the hotbar slots below.")
	@Check("Sort")
	public boolean sort = true;

	@Requires("sort")
	@Combo(value = "Slot 1", source = "slotKinds")
	public String slot1 = "Sword";

	@Requires("sort")
	@Combo(value = "Slot 2", source = "slotKinds")
	public String slot2 = "Block";

	@Requires("sort")
	@Combo(value = "Slot 3", source = "slotKinds")
	public String slot3 = "Pickaxe";

	@Requires("sort")
	@Combo(value = "Slot 4", source = "slotKinds")
	public String slot4 = "Axe";

	@Requires("sort")
	@Combo(value = "Slot 5", source = "slotKinds")
	public String slot5 = "Bow";

	@Requires("sort")
	@Combo(value = "Slot 6", source = "slotKinds")
	public String slot6 = NONE;

	@Requires("sort")
	@Combo(value = "Slot 7", source = "slotKinds")
	public String slot7 = "Golden Apple";

	@Requires("sort")
	@Combo(value = "Slot 8", source = "slotKinds")
	public String slot8 = "Pearl";

	@Requires("sort")
	@Combo(value = "Slot 9", source = "slotKinds")
	public String slot9 = "Block";

	@Section("Timing")
	@Description("Time between two clicks.")
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

	private String[] kinds = new String[0];
	private final Map<String, IntList> kept = new Object2ObjectOpenHashMap<>();
	private final IntList junk = new IntArrayList();
	private int mergeSlot = -1;
	private boolean collected;

	@Override
	public String details() {
		return mode;
	}

	@Override
	public void onDisable() {
		finish();
	}

	public String[] slotKinds() {
		return SLOT_KINDS;
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null) return;
		if (!inventory.usable(mode.equals("Spoof"), stopWhileMoving && mergeSlot == -1)) {
			finish();
			return;
		}

		InventoryMenu menu = player.playerMenu;
		if (player.inventory.getCursorItem() == null) mergeSlot = -1;
		int[] click = next(player, menu);
		if (click == null) {
			timer.reset();
			return;
		}
		if (!inventory.claim(this)) return;

		inventory.openInventory(closeDelay);
		if (!timer.started()) timer.start(System.currentTimeMillis() - inventory.sinceOpen(), startDelay);
		for (int count = 0; count < MAX_CLICKS_PER_TICK && click != null; count++) {
			if (!timer.ready(menu.getSlot(click[0]), travelDelay)) return;

			boolean holding = player.inventory.getCursorItem() != null;
			inventory.click(click[0], click[1], click[2]);
			if (click[2] == InventoryManager.PICKUP) {
				mergeSlot = holding ? -1 : click[0];
				collected = false;
			} else if (click[2] == InventoryManager.PICKUP_ALL) {
				collected = true;
			}
			timer.clicked(menu.getSlot(click[0]), delay);
			click = next(player, menu);
		}
	}

	public boolean wants(PlayerEntity player, ItemStack stack) {
		String kind = kind(stack);
		if (kind == null) return false;
		if (stack.getItem() instanceof ArmorItem) return AutoArmor.improves(player, stack);

		int limit = limit(kind);
		if (limit >= PLAYER_SLOTS) return true;

		float score = score(stack);
		int better = 0;
		for (ItemStack own : player.inventory.items) {
			if (own != null && kind.equals(kind(own)) && score(own) >= score) better++;
		}
		return better < limit;
	}

	public boolean isGear(ItemStack stack) {
		Item item = stack.getItem();
		return item instanceof ArmorItem || item instanceof SwordItem || item instanceof ToolItem || item instanceof BowItem;
	}

	public int hotbarSlot(PlayerInventory playerInventory, ItemStack stack) {
		if (!sort) return -1;
		String kind = kind(stack);
		if (kind == null) return -1;

		String[] layout = layout();
		for (int i = 0; i < HOTBAR; i++) {
			if (layout[i].equals(kind) && playerInventory.items[i] == null) return i;
		}
		return -1;
	}

	private int[] next(PlayerEntity player, InventoryMenu menu) {
		if (player.inventory.getCursorItem() != null) {
			if (mergeSlot == -1) return null;
			return new int[]{mergeSlot, 0, collected ? InventoryManager.PICKUP : InventoryManager.PICKUP_ALL};
		}

		evaluate(player, menu);
		int[] click = merge ? merge(menu) : null;
		if (click == null && drop) click = drop(menu);
		if (click == null && sort) click = sort(menu);
		return click;
	}

	private void evaluate(PlayerEntity player, InventoryMenu menu) {
		kept.clear();
		junk.clear();
		if (kinds.length != menu.slots.size()) kinds = new String[menu.slots.size()];

		int playerStart = menu.slots.size() - PLAYER_SLOTS;
		Map<String, IntList> groups = new Object2ObjectOpenHashMap<>();
		for (int i = AutoArmor.ARMOR_START; i < menu.slots.size(); i++) {
			ItemStack stack = menu.getSlot(i).getItem();
			kinds[i] = stack == null ? null : kind(stack);
			if (stack == null) continue;
			if (kinds[i] != null) groups.computeIfAbsent(kinds[i], kind -> new IntArrayList()).add(i);
			else if (i >= playerStart) junk.add(i);
		}

		int[] armor = AutoArmor.bestSet(player);
		int hotbarStart = menu.slots.size() - HOTBAR;
		String[] layout = layout();
		IntComparator order = (a, b) -> {
			ItemStack first = menu.getSlot(a).getItem();
			ItemStack second = menu.getSlot(b).getItem();
			int result = Float.compare(score(second), score(first));
			if (result == 0) result = Integer.compare(durability(second), durability(first));
			if (result == 0) result = Integer.compare(placement(b, hotbarStart, layout), placement(a, hotbarStart, layout));
			return result == 0 ? Integer.compare(a, b) : result;
		};

		for (Map.Entry<String, IntList> group : groups.entrySet()) {
			IntList slots = group.getValue();
			int type = armorType(group.getKey());
			if (type != -1) {
				for (int slot : slots) {
					if (slot != armor[type] && slot >= playerStart) junk.add(slot);
				}
				continue;
			}

			slots.sort(order);
			int limit = limit(group.getKey());
			for (int i = limit; i < slots.size(); i++) junk.add(slots.getInt(i));
			kept.put(group.getKey(), slots.subList(0, Math.min(limit, slots.size())));
		}
	}

	private int[] merge(InventoryMenu menu) {
		int start = menu.slots.size() - PLAYER_SLOTS;
		int end = menu.slots.size();
		for (int i = start; i < end; i++) {
			ItemStack stack = menu.getSlot(i).getItem();
			if (stack == null || kinds[i] == null || !stack.isStackable() || stack.size >= stack.getMaxSize()) continue;

			int total = 0;
			int stacks = 0;
			int target = i;
			for (int j = start; j < end; j++) {
				ItemStack other = menu.getSlot(j).getItem();
				if (other == null || !other.matchesItem(stack) || !ItemStack.matchesNbt(other, stack)) continue;
				total += other.size;
				stacks++;
				if (other.size < other.getMaxSize() && other.size > menu.getSlot(target).getItem().size) target = j;
			}

			int max = stack.getMaxSize();
			if (stacks > (total + max - 1) / max) return new int[]{target, 0, InventoryManager.PICKUP};
		}
		return null;
	}

	private int[] drop(InventoryMenu menu) {
		int nearest = -1;
		for (int slot : junk) {
			if (nearest == -1 || timer.distance(menu.getSlot(slot)) < timer.distance(menu.getSlot(nearest))) nearest = slot;
		}
		return nearest == -1 ? null : new int[]{nearest, 1, InventoryManager.THROW};
	}

	private int[] sort(InventoryMenu menu) {
		int hotbarStart = menu.slots.size() - HOTBAR;
		String[] layout = layout();
		Object2IntMap<String> used = new Object2IntOpenHashMap<>();
		for (int i = 0; i < layout.length; i++) {
			IntList slots = kept.get(layout[i]);
			if (slots == null) continue;

			int rank = used.mergeInt(layout[i], 1, Integer::sum) - 1;
			if (rank >= slots.size()) continue;

			int slot = slots.getInt(rank);
			if (slot != hotbarStart + i) return new int[]{slot, i, InventoryManager.SWAP};
		}
		return null;
	}

	private String kind(ItemStack stack) {
		Item item = stack.getItem();
		if (item instanceof ArmorItem armor) return ARMOR[armor.slot];
		if (item instanceof SwordItem) return "Sword";
		if (item instanceof BowItem) return "Bow";
		if (item instanceof PickaxeItem) return "Pickaxe";
		if (item instanceof AxeItem) return "Axe";
		if (item instanceof ShovelItem) return "Shovel";
		if (item instanceof FishingRodItem) return "Fishing Rod";
		if (item instanceof ShearsItem) return "Shears";
		if (item instanceof FlintAndSteelItem) return "Flint and Steel";
		if (item instanceof GoldenAppleItem) return "Golden Apple";
		if (item instanceof EnderPearlItem) return "Pearl";
		if (item == Items.MUSHROOM_STEW) return "Soup";
		if (item instanceof FoodItem) return isBadFood(stack) ? null : "Food";
		if (item instanceof PotionItem potion) return keepPotion(potion, stack) ? "Potion" : null;
		if (item instanceof EggItem || item instanceof SnowballItem) return "Throwable";
		if (item == Items.WATER_BUCKET) return "Water";
		if (item == Items.LAVA_BUCKET) return "Lava";
		if (item == Items.BUCKET) return "Bucket";
		if (item instanceof BlockItem blockItem) return blockKind(blockItem.getBlock());
		if (isValuable(item)) return "Valuable";
		return stack.hasEnchantments() ? "Enchanted" : null;
	}

	private static int armorType(String kind) {
		for (int type = 0; type < ARMOR.length; type++) {
			if (ARMOR[type].equals(kind)) return type;
		}
		return -1;
	}

	private static String blockKind(Block block) {
		if (block == Blocks.TNT) return "TNT";
		if (!block.isCube() || block.hasBlockEntity() || block instanceof FallingBlock || block == Blocks.CRAFTING_TABLE) return null;
		return "Block";
	}

	private static boolean isBadFood(ItemStack stack) {
		Item item = stack.getItem();
		return item == Items.ROTTEN_FLESH || item == Items.SPIDER_EYE || item == Items.POISONOUS_POTATO
			|| item == Items.FISH && stack.getMetadata() == PUFFERFISH;
	}

	private static boolean isValuable(Item item) {
		return item == Items.ARROW || item == Items.DIAMOND || item == Items.IRON_INGOT || item == Items.GOLD_INGOT
			|| item == Items.EMERALD || item == Items.ENCHANTED_BOOK || item == Items.FIRE_CHARGE;
	}

	private boolean keepPotion(PotionItem item, ItemStack stack) {
		List<StatusEffectInstance> effects = item.getPotionEffects(stack);
		if (effects == null || effects.isEmpty()) return false;
		for (StatusEffectInstance effect : effects) {
			if (StatusEffect.BY_ID[effect.getId()].isHarmful()) return harmfulSplash && PotionItem.isSplashPotion(stack.getMetadata());
		}
		return true;
	}

	private int limit(String kind) {
		return switch (kind) {
			case "Block" -> (int) blockStacks;
			case "Food" -> (int) foodStacks;
			case "Throwable" -> (int) throwableStacks;
			case "Fishing Rod" -> (int) fishingRods;
			case "Golden Apple", "Pearl", "Potion", "Soup", "TNT", "Valuable", "Enchanted" -> PLAYER_SLOTS;
			default -> 1;
		};
	}

	private static float score(ItemStack stack) {
		Item item = stack.getItem();
		if (item instanceof SwordItem sword) {
			return sword.getAttackDamage() + level(Enchantment.SHARPNESS, stack) * 1.25F
				+ level(Enchantment.FIRE_ASPECT, stack) * 2.0F + level(Enchantment.KNOCKBACK, stack) * 0.5F;
		}
		if (item instanceof ToolItem) {
			Block block = item instanceof PickaxeItem ? Blocks.STONE : item instanceof AxeItem ? Blocks.PLANKS : Blocks.DIRT;
			float speed = stack.getMiningSpeed(block);
			int efficiency = level(Enchantment.EFFICIENCY, stack);
			return speed > 1.0F && efficiency > 0 ? speed + efficiency * efficiency + 1 : speed;
		}
		if (item instanceof BowItem) {
			return level(Enchantment.POWER, stack) + level(Enchantment.PUNCH, stack) * 0.5F
				+ level(Enchantment.FLAME, stack) * 0.5F + level(Enchantment.INFINITY, stack) * 0.25F;
		}
		if (item instanceof FoodItem food && !(item instanceof GoldenAppleItem)) {
			int hunger = food.getHungerPoints(stack);
			return (hunger + hunger * food.getSaturation(stack) * 2.0F) * stack.size;
		}
		return stack.isDamageable() ? 0.0F : stack.size;
	}

	private static int level(Enchantment enchantment, ItemStack stack) {
		return EnchantmentHelper.getLevel(enchantment.id, stack);
	}

	private static int durability(ItemStack stack) {
		return stack.isDamageable() ? stack.getMaxDamage() - stack.getDamage() : 0;
	}

	private int placement(int slot, int hotbarStart, String[] layout) {
		return sort && slot >= hotbarStart && layout[slot - hotbarStart].equals(kinds[slot]) ? 1 : 0;
	}

	private String[] layout() {
		return new String[]{slot1, slot2, slot3, slot4, slot5, slot6, slot7, slot8, slot9};
	}

	private void finish() {
		LocalClientPlayerEntity player = minecraft.player;
		if (player != null && mergeSlot != -1 && player.menu == player.playerMenu && player.inventory.getCursorItem() != null) {
			inventory.pickup(mergeSlot);
		}
		timer.reset();
		mergeSlot = -1;
	}
}
