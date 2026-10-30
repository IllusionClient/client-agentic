package org.codeberg.illusionclient.feature.module.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.item.UseAction;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.network.packet.c2s.play.SelectSlotC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.player.ItemSlowdownEvent;
import org.codeberg.illusionclient.event.player.MotionEvent;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;

@ModuleInfo(name = "No Slow", category = Category.MOVEMENT)
@Description("Reduces the slowdown from blocking, eating and drawing bows.")
public class NoSlow extends Module {
	private final Minecraft minecraft = Minecraft.getInstance();

	@Description("Vanilla only changes the slowdown, NCP re-blocks around each movement packet and Switch swaps slots each tick. Packet modes only affect swords.")
	@Combo(value = "Mode", options = {"Vanilla", "NCP", "Switch"})
	public String mode = "Vanilla";

	@Description("Speed kept while blocking with a sword. 0.2 is vanilla.")
	@Slider(value = "Sword", min = 0.2F, max = 1.0F)
	public float sword = 1.0F;

	@Description("Speed kept while eating or drinking. 0.2 is vanilla.")
	@Slider(value = "Consumables", min = 0.2F, max = 1.0F)
	public float consumables = 1.0F;

	@Description("Speed kept while drawing a bow. 0.2 is vanilla.")
	@Slider(value = "Bow", min = 0.2F, max = 1.0F)
	public float bow = 1.0F;

	@Override
	public String details() {
		return mode;
	}

	@Listen
	public void onItemSlowdown(ItemSlowdownEvent event) {
		UseAction action = useAction();
		if (action == UseAction.BLOCK) event.multiplier = sword;
		else if (action == UseAction.EAT || action == UseAction.DRINK) event.multiplier = consumables;
		else if (action == UseAction.BOW) event.multiplier = bow;
	}

	@Listen
	public void onMotion(MotionEvent event) {
		if (mode.equals("Vanilla") || useAction() != UseAction.BLOCK) return;

		LocalClientPlayerEntity player = minecraft.player;
		if (mode.equals("Switch")) {
			if (!event.pre) return;
			int slot = player.inventory.selectedSlot;
			player.networkHandler.sendPacket(new SelectSlotC2SPacket((slot + 1) % 9));
			player.networkHandler.sendPacket(new SelectSlotC2SPacket(slot));
		} else if (event.pre) {
			player.networkHandler.sendPacket(new PlayerHandActionC2SPacket(PlayerHandActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
		} else {
			player.networkHandler.sendPacket(new PlayerUseC2SPacket(player.inventory.getSelectedItem()));
		}
	}

	private UseAction useAction() {
		LocalClientPlayerEntity player = minecraft.player;
		return player != null && player.hasItemInUse() ? player.getItemInUse().getUseAction() : null;
	}
}
