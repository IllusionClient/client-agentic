package org.codeberg.illusionclient.feature.module.combat;

import org.codeberg.illusionclient.event.api.Listen;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.player.PlayerTickEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.combat.Weapons;
import org.codeberg.illusionclient.util.CombatUtils;

@ModuleInfo(name = "Auto Weapon", category = Category.COMBAT)
@Description("Selects your weapon for you when aiming on a player.")
public class AutoWeapon extends Module {
	private final Minecraft minecraft = Minecraft.getInstance();

	@Description("The amount of time to aim on a player before Auto Weapon activates.")
	@Slider(value = "Activation Time", min = 0.0F, max = 250.0F)
	public float activationTime = 150.0F;

	@Description("Only activates while the attack key is held.")
	@Check("Left Mouse Pressed")
	public boolean leftMousePressed = true;

	private int countdown;

	@Override
	public void onEnable() {
		countdown = ticks(activationTime);
	}

	@Override
	public void onDisable() {
		IllusionClient.MODULES.releaseSlot(this);
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null) return;
      
		PlayerInventory inventory = player.inventory;

		if (!conditionsMet(player)) {
			countdown = ticks(activationTime);
			IllusionClient.MODULES.releaseSlot(this);
			return;
		}

		if (countdown > 0) {
			countdown--;
			IllusionClient.MODULES.releaseSlot(this);
			return;
		}

		int current = inventory.selectedSlot;
        int slotToUse = -1;

        for (int i = 0; i < PlayerInventory.getHotbarSize(); i++) {
			int slot = (current + i) % PlayerInventory.getHotbarSize();
			ItemStack stack = inventory.items[slot];
			if (Weapons.isWeapons(stack)) {
				slotToUse = slot;
				break;
			}
		}

		if (slotToUse == -1 || !IllusionClient.MODULES.claimSlot(this)) return;

		inventory.selectedSlot = slotToUse;
	}

  	private boolean hasTarget(LocalClientPlayerEntity player) {
		HitResult crosshair = minecraft.crosshairTarget;

		return crosshair != null && crosshair.entity instanceof PlayerEntity hovered && CombatUtils.isTarget(hovered);
	}

  	private boolean conditionsMet(LocalClientPlayerEntity player) {
		if (minecraft.screen != null || !player.isAlive() || player.isSleeping()) return false;
      	if (Weapons.isHolding(player)) return false;
		if (!hasTarget(player)) return false;
		if (leftMousePressed && !IllusionClient.INPUT.held(minecraft.options.attackKey)) return false;
		return true;
	}
}