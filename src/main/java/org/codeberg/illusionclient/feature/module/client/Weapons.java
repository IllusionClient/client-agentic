package org.codeberg.illusionclient.feature.module.client;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.*;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.feature.config.api.MultiSelect;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;

import java.util.Set;

@Description("Sets which items count as weapons for Weapons Only checks.")
@ModuleInfo(name = "Weapons", category = Category.CLIENT)
public class Weapons extends Module {
	@Description("Item types that count as weapons. Fists means an empty hand.")
	@MultiSelect(value = "Items", options = {"Swords", "Axes", "Sticks", "Fists"})
	public Set<String> items = Set.of("Swords", "Axes");

	@Description("Any item with one of these enchantments counts as a weapon.")
	@MultiSelect(value = "Enchantments", options = {"Sharpness", "Knockback", "Fire Aspect"})
	public Set<String> enchantments = Set.of("Sharpness", "Knockback", "Fire Aspect");

	public static boolean isHolding(PlayerEntity player) {
		return isWeapons(player.getItemInHand());
	}

	public static boolean isWeapons(ItemStack stack) {
		Weapons weapons = IllusionClient.MODULES.get(Weapons.class);
		if (!weapons.isEnabled()) return true;
		if (stack == null) return weapons.items.contains("Fists");

		Item item = stack.getItem();
		if (weapons.items.contains("Swords") && item instanceof SwordItem) return true;
		if (weapons.items.contains("Axes") && item instanceof AxeItem) return true;
		if (weapons.items.contains("Sticks") && item == Items.STICK) return true;

		return weapons.enchantments.contains("Sharpness") && EnchantmentHelper.getLevel(Enchantment.SHARPNESS.id, stack) > 0
			|| weapons.enchantments.contains("Knockback") && EnchantmentHelper.getLevel(Enchantment.KNOCKBACK.id, stack) > 0
			|| weapons.enchantments.contains("Fire Aspect") && EnchantmentHelper.getLevel(Enchantment.FIRE_ASPECT.id, stack) > 0;
	}
}
