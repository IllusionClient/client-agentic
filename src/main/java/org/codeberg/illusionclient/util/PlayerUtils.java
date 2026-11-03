package org.codeberg.illusionclient.util;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.text.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HitResult;
import net.minecraft.world.WorldSettings;

public final class PlayerUtils {
	public static boolean aimingAtBlock() {
		HitResult target = Minecraft.getInstance().crosshairTarget;
		return target != null && target.type == HitResult.Type.BLOCK;
	}

	public static boolean canBreakAimedBlock() {
		if (!aimingAtBlock()) return false;
		Minecraft minecraft = Minecraft.getInstance();
		LocalClientPlayerEntity player = minecraft.player;
		BlockPos pos = minecraft.crosshairTarget.getPos();
		Block block = minecraft.world.getBlockState(pos).getBlock();
		ItemStack stack = player.getItemInHand();
		WorldSettings.GameMode mode = minecraft.interactionManager.getGameMode();

		if (mode == WorldSettings.GameMode.SPECTATOR) return false;
		if (mode.restrictsWorldModification() && !player.canModifyWorld()
			&& (stack == null || !stack.hasMineBlockOverride(block))) return false;
		if (!minecraft.world.getWorldBorder().contains(pos)) return false;
		if (mode.isCreative()) return stack == null || !(stack.getItem() instanceof SwordItem);
		return block.getMiningSpeed(player, minecraft.world, pos) > 0;
	}

	public static boolean aimingAtPlayer() {
		HitResult target = Minecraft.getInstance().crosshairTarget;
		return target != null && target.entity instanceof PlayerEntity;
	}

	public static boolean isLocalPlayer(Object entity) {
		return entity == Minecraft.getInstance().player;
	}

	public static Formatting nametagColor(PlayerEntity player) {
		String display = player.getDisplayName().getFormattedString();
		int nameStart = display.indexOf(player.getName());

		Formatting color = null;
		for (int i = 0; i + 1 < nameStart; i++) {
			if (display.charAt(i) != '§') continue;

			char code = Character.toLowerCase(display.charAt(++i));
			int id = Character.digit(code, 16);
			if (id >= 0) color = Formatting.byId(id);
			else if (code == 'r') color = null;
		}
		return color;
	}
}
