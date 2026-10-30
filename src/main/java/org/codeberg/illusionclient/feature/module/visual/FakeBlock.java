package org.codeberg.illusionclient.feature.module.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.combat.AutoBlock;
import org.codeberg.illusionclient.util.CombatUtils;

@ModuleInfo(name = "Fake Block", category = Category.VISUAL)
@Description("Shows your sword as blocking without actually blocking. Only you can see it.")
public class FakeBlock extends Module {
	@Description("Only shows the block while you hold left click.")
	@Check("Left Mouse Pressed")
	public boolean leftMousePressed = true;

	@Description("Only shows the block while a player is in range.")
	@Check("Only With Target")
	public boolean onlyWithTarget = false;

	@Description("How close a player has to be.")
	@Requires("onlyWithTarget")
	@Slider(value = "Range", min = 0.0F, max = 8.0F)
	public float range = 4.0F;

	public static boolean shown(PlayerEntity player) {
		if (player != Minecraft.getInstance().player) return false;
		ItemStack stack = player.getItemInHand();
		if (stack == null || !(stack.getItem() instanceof SwordItem)) return false;
		FakeBlock fake = IllusionClient.MODULES.get(FakeBlock.class);
		return fake.isEnabled() && fake.active() || IllusionClient.MODULES.get(AutoBlock.class).forcesBlockAnimation();
	}

	public static boolean targetInRange(float range) {
		return CombatUtils.best(other -> other.isAlive() && CombatUtils.isTarget(other) && CombatUtils.distanceTo(other) <= range,
			CombatUtils::distanceTo) != null;
	}

	private boolean active() {
		if (leftMousePressed && !IllusionClient.INPUT.held(Minecraft.getInstance().options.attackKey)) return false;
		return !onlyWithTarget || targetInRange(range);
	}
}
