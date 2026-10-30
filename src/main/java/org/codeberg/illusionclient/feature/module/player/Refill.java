package org.codeberg.illusionclient.feature.module.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.player.PlayerTickEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.RangeSlider;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.util.inventory.ClickTimer;
import org.codeberg.illusionclient.util.inventory.InventoryManager;

import java.util.List;

@ModuleInfo(name = "Refill", category = Category.PLAYER)
@Description("Moves soups and potions into your empty hotbar slots when you open your inventory.")
public class Refill extends Module {
	private static final int PLAYER_SLOTS = 36;
	private static final int HOTBAR = 9;
	private static final int MAX_CLICKS_PER_TICK = 4;
	private static final float JITTER = 0.15F;
	private static final float SMART_TRAVEL = 40.0F;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final InventoryManager inventory = IllusionClient.INVENTORY;
	private final ClickTimer timer = new ClickTimer();

	@Description("Refills mushroom stew.")
	@Check("Soup")
	public boolean soup = true;

	@Description("Refills splash potions of healing.")
	@Check("Potion")
	public boolean potion = true;

	@Description("Refills any splash potion, not just healing.")
	@Requires("potion")
	@Check("Use Non-Health Potions")
	public boolean nonHealth = false;

	@Description("The mouse button the clicks are sent with. Right is unusual for real players but gets past some anticheats. Left + Right alternates, which allows faster refills.")
	@Combo(value = "Simulate Button", options = {"Left", "Right", "Left + Right"})
	public String button = "Left";

	@Description("Time to wait after the inventory opens before the first refill.")
	@RangeSlider(value = "Delay After Open (ms)", min = 0.0F, max = 1000.0F)
	public float[] openDelay = {100.0F, 200.0F};

	@Description("Time to wait after the last refill before closing the inventory.")
	@RangeSlider(value = "Delay Before Close (ms)", min = 0.0F, max = 1000.0F)
	public float[] closeDelay = {100.0F, 200.0F};

	@Description("Items moved per second.")
	@Slider(value = "Speed", min = 1.0F, max = 20.0F, step = 1.0F)
	public float speed = 8.0F;

	@Description("Slows down for items that are far apart, like a real player would.")
	@Check("Smart Speed")
	public boolean smartSpeed = true;

	private boolean refilled;
	private boolean right;

	@Override
	public void onDisable() {
		reset();
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || !(minecraft.screen instanceof SurvivalInventoryScreen) || !inventory.usable(false, false)) {
			reset();
			return;
		}
		InventoryMenu menu = player.playerMenu;
		if (!refilled && (player.inventory.getCursorItem() != null || next(player, menu) == -1)) {
			timer.reset();
			return;
		}
		if (!inventory.claim(this)) return;
		if (!timer.started()) timer.start(System.currentTimeMillis() - inventory.sinceOpen(), openDelay);

		float interval = 1000.0F / speed;
		float[] delay = {interval * (1.0F - JITTER), interval * (1.0F + JITTER)};
		for (int count = 0; count < MAX_CLICKS_PER_TICK; count++) {
			int slot = player.inventory.getCursorItem() == null ? next(player, menu) : -1;
			if (slot == -1) {
				if (refilled && timer.closing(closeDelay)) player.closeMenu();
				return;
			}

			InventorySlot target = menu.getSlot(slot);
			if (!timer.ready(target, smartSpeed ? SMART_TRAVEL : 0.0F)) return;

			inventory.click(slot, nextButton(), InventoryManager.QUICK_MOVE);
			timer.clicked(target, delay);
			refilled = true;
		}
	}

	private int next(LocalClientPlayerEntity player, InventoryMenu menu) {
		boolean room = false;
		for (int i = 0; i < HOTBAR; i++) room |= player.inventory.items[i] == null;
		if (!room) return -1;

		int nearest = -1;
		int start = menu.slots.size() - PLAYER_SLOTS;
		int end = menu.slots.size() - HOTBAR;
		for (int i = start; i < end; i++) {
			ItemStack stack = menu.getSlot(i).getItem();
			if (stack == null || !refills(stack)) continue;
			if (nearest == -1 || timer.distance(menu.getSlot(i)) < timer.distance(menu.getSlot(nearest))) nearest = i;
		}
		return nearest;
	}

	private boolean refills(ItemStack stack) {
		if (stack.getItem() == Items.MUSHROOM_STEW) return soup;
		if (!potion || !(stack.getItem() instanceof PotionItem item) || !PotionItem.isSplashPotion(stack.getMetadata())) return false;
		if (nonHealth) return true;

		List<StatusEffectInstance> effects = item.getPotionEffects(stack);
		if (effects == null) return false;
		for (StatusEffectInstance effect : effects) {
			if (effect.getId() == StatusEffect.INSTANT_HEALTH.id) return true;
		}
		return false;
	}

	private int nextButton() {
		return switch (button) {
			case "Right" -> 1;
			case "Left + Right" -> (right = !right) ? 1 : 0;
			default -> 0;
		};
	}

	private void reset() {
		timer.reset();
		refilled = false;
	}
}
