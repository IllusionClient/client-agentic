package org.codeberg.illusionclient.feature.module.combat;

import org.codeberg.illusionclient.event.api.Listen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
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
import org.codeberg.illusionclient.util.input.InputState;
import org.codeberg.illusionclient.util.CombatUtils;

@ModuleInfo(name = "Velocity", category = Category.COMBAT)
@Description("Selects your weapon for you when aiming on a player.")
public class Velocity extends Module {
	private final Minecraft minecraft = Minecraft.getInstance();

  	@Description("Regular reduces motion, Ignore prevents knockback entirely, Jump automatically jumps after knockback.")
	@Combo(value = "Mode", options = {"Regular", "Ignore", "Jump"})
	public String mode = "Regular";

	@Description("The amount of time to aim on a player before activating.")
	@Slider(value = "Activation Time", min = 0.0F, max = 250.0F)
	public float activationTime = 150.0F;

  	@Description("Only activates while looking at a player.")
	@Check("Looking At Player")
	public boolean lookingAtPlayer = true;

  	@Description("Only works when pressing forward.")
	@Check("Moving Forward")
	public boolean notMovingForward = false;

	@Description("Only activates while the attack key is held.")
	@Check("Left Mouse Pressed")
	public boolean leftMousePressed = true;

  	@Description("Only activates while holding a weapon.")
	@Check("Weapons Only")
	public boolean weaponsOnly = true;

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

      	InputState input = IllusionClient.INPUT.player;

		if (!conditionsMet(player, input)) {
			countdown = ticks(activationTime);
			IllusionClient.MODULES.releaseSlot(this);
			return;
		}

		if (countdown > 0) {
			countdown--;
			IllusionClient.MODULES.releaseSlot(this);
			return;
		}

        int slotToUse = -1;

        for (int i = 0; i < PlayerInventory.getHotbarSize(); i++) {
			int slot = (inventory.selectedSlot + i) % PlayerInventory.getHotbarSize();
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

  	private boolean conditionsMet(LocalClientPlayerEntity player, InputState input) {
		if (minecraft.screen != null || !player.isAlive() || player.isSleeping()) return false;
      	if (weaponsOnly && Weapons.isHolding(player)) return false;
		if (leftMousePressed && !IllusionClient.INPUT.held(minecraft.options.attackKey)) return false;
		if (lookingAtPlayer && !hasTarget(player)) return false;
        if (movingForward && input.forwardAxis() <= 0.0F) return false;
		return true;
	}
}