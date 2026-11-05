package org.codeberg.illusionclient.feature.module.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.player.PlayerTickEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.MultiSelect;
import org.codeberg.illusionclient.feature.config.api.Section;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(name = "Auto Heal", category = Category.PLAYER, aliases = {"AutoSoup", "AutoGapple", "AutoHead"})
@Description("Eats soup or golden heads from your hotbar when your health gets low.")
public class AutoHeal extends Module {
	private final Minecraft minecraft = Minecraft.getInstance();

	@Section("Maximum Health")
	@Description("Heals once when your health is at or below this many hearts.")
	@Slider(value = "Single Use", min = 0.5F, max = 10.0F, step = 0.5F)
	public float singleUse = 6.0F;

	@Description("Heals twice in a row when a big hit drops you to or below this many hearts.")
	@Slider(value = "Double Use", min = 0.0F, max = 10.0F, step = 0.5F)
	public float doubleUse = 3.0F;

	@Section("Delays")
	@Description("How much every delay varies. 0% always waits exactly the set time.")
	@Slider(value = "Randomization (%)", min = 0.0F, max = 100.0F)
	public float randomization = 20.0F;

	@Description("Time to wait after your health gets low before switching to a healing item.")
	@Slider(value = "Before Switching (ms)", min = 0.0F, max = 500.0F)
	public float beforeSwitching = 50.0F;

	@Description("Time between switching, eating and dropping the bowl.")
	@Slider(value = "Between Actions (ms)", min = 0.0F, max = 500.0F)
	public float betweenActions = 50.0F;

	@Description("Time to wait after healing before healing again. Raise it if a second item gets used before the first one registers.")
	@Slider(value = "Cooldown (ms)", min = 0.0F, max = 1000.0F)
	public float cooldown = 250.0F;

	@Section("Items")
	@Description("Drops the empty bowl after each soup. Turn it off on servers that clear bowls for you.")
	@Check("Drop Bowls")
	public boolean dropBowls = true;

	@Description("The items to eat. Skull eats golden heads in UHC modes.")
	@MultiSelect(value = "Items", options = {"Soup", "Skull"})
	public Set<String> items = Set.of("Soup");

	private long nextAt;
	private long triggerAt;
	private boolean active;
	private boolean used;
	private int remaining;
	private int usedSlot = -1;
	private int previousSlot = -1;

	@Override
	public String details() {
		return String.join(", ", items);
	}

	@Override
	public void onDisable() {
		if (active) finish();
		nextAt = triggerAt = 0L;
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null) return;
		if (active && minecraft.screen != null) {
			finish();
			return;
		}

		long now = System.currentTimeMillis();
		float hearts = player.getHealth() / 2.0F;
		if (!active) {
			if (minecraft.screen != null || hearts > singleUse || next(player.inventory) == -1) {
				triggerAt = 0L;
				return;
			}
			if (now < nextAt) return;
			if (triggerAt == 0L) triggerAt = now + delay(beforeSwitching);
			if (now < triggerAt || !IllusionClient.MODULES.claimSlot(this)) return;

			triggerAt = 0L;
			active = true;
			remaining = hearts <= doubleUse ? 2 : 1;
			usedSlot = -1;
			previousSlot = player.inventory.selectedSlot;
		}
		if (now < nextAt) return;

		act(player);
		nextAt = now + delay(active ? betweenActions : cooldown);
	}

	private void act(LocalClientPlayerEntity player) {
		PlayerInventory inventory = player.inventory;
		if (used) {
			used = false;
			if (dropBowls && inventory.getSelectedItem() != null && inventory.getSelectedItem().getItem() == Items.BOWL) {
				player.dropItem(false);
				return;
			}
		}

		if (remaining == 0) {
			finish();
			return;
		}

		if (inventory.selectedSlot == usedSlot || !matches(inventory.getSelectedItem())) {
			int slot = next(inventory);
			if (slot == -1) finish();
			else inventory.selectedSlot = slot;
			return;
		}

		minecraft.interactionManager.useItem(player, minecraft.world, inventory.getSelectedItem());
		usedSlot = inventory.selectedSlot;
		used = true;
		remaining--;
	}

	private int next(PlayerInventory inventory) {
		for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
			if (slot != usedSlot && matches(inventory.items[slot])) return slot;
		}
		return -1;
	}

	private boolean matches(ItemStack stack) {
		if (stack == null) return false;
		Item item = stack.getItem();
		return item == Items.MUSHROOM_STEW && items.contains("Soup") || item == Items.SKULL && items.contains("Skull");
	}

	private long delay(float ms) {
		float spread = randomization / 100.0F * (ThreadLocalRandom.current().nextFloat() * 2.0F - 1.0F);
		return (long) (ms * (1.0F + spread));
	}

	private void finish() {
		if (previousSlot != -1 && minecraft.player != null) minecraft.player.inventory.selectedSlot = previousSlot;
		IllusionClient.MODULES.releaseSlot(this);
		previousSlot = usedSlot = -1;
		remaining = 0;
		used = active = false;
	}
}
