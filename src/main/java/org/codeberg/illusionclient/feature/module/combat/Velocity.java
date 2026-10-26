package org.codeberg.illusionclient.feature.module.combat;

import org.codeberg.illusionclient.event.api.Listen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.player.InputEvent;
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

	@Description("Chance to activate.")
	@Slider(value = "Chance (%)", min = 0.0F, max = 100.0F)
	public float chance = 100.0F;

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
	
  	@Description("Only activates while holding a weapon.")
	@Check("Sprinting")
	public boolean sprinting = true;

	@Listen
	public void onInput(InputEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null) return;
      
		PlayerInventory inventory = player.inventory;
      	InputState input = IllusionClient.INPUT.player;

		if (!conditionsMet(player, input)) return;
		if (random.nextFloat() * 100.0F < chance) return;

		IllusionClient.INPUT.client.jump = true;
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
		if (sprinting && !player.sprinting) return false;
		return true;
	}
}