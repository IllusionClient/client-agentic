package org.codeberg.illusionclient.util.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseInventoryMenuC2SPacket;
import net.minecraft.network.packet.c2s.play.InventoryMenuClickSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.CloseInventoryMenuS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenInventoryMenuS2CPacket;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.network.PacketReceiveEvent;
import org.codeberg.illusionclient.event.network.PacketSendEvent;
import org.codeberg.illusionclient.event.player.PlayerTickEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.util.input.InputState;

public final class InventoryManager {
	public static final int PICKUP = 0;
	public static final int QUICK_MOVE = 1;
	public static final int SWAP = 2;
	public static final int THROW = 4;
	public static final int PICKUP_ALL = 6;
	public static final int OUTSIDE = -999;
	private static final int FIRST_STORAGE_SLOT = 9;

	private final Minecraft minecraft = Minecraft.getInstance();
	private Module owner;
	private boolean claimed;
	private boolean open;
	private boolean spoofed;
	private long openedAt;
	private long clickedAt;
	private float[] closeDelay = {0.0F, 0.0F};
	private long closeAt;

	public InventoryManager() {
		IllusionClient.BUS.subscribe(this);
	}

	public boolean claim(Module module) {
		if (owner != null && owner != module) return false;
		owner = module;
		claimed = true;
		return true;
	}

	public Module owner() {
		return owner;
	}

	public boolean open() {
		return open;
	}

	public boolean spoofed() {
		return spoofed;
	}

	public boolean usable(boolean spoof, boolean stillOnly) {
		if (minecraft.player == null || minecraft.interactionManager.hasCreativeInventory() || inContainer()) return false;
		Screen screen = minecraft.screen;
		if (screen instanceof SurvivalInventoryScreen) return true;
		return screen == null && spoof && minecraft.player.getItemInUse() == null && !IllusionClient.MODULES.slotClaimed() && !(stillOnly && moving());
	}

	public boolean inContainer() {
		LocalClientPlayerEntity player = minecraft.player;
		return player != null && player.menu != player.playerMenu;
	}

	public long sinceOpen() {
		return open ? System.currentTimeMillis() - openedAt : 0L;
	}

	public long sinceClick() {
		return System.currentTimeMillis() - clickedAt;
	}

	public void openInventory(float[] closeDelay) {
		this.closeDelay = closeDelay;
		if (open || inContainer() || minecraft.player == null || minecraft.screen != null) return;
		minecraft.player.networkHandler.sendPacket(new ClientStatusC2SPacket(ClientStatusC2SPacket.Status.OPEN_INVENTORY_ACHIEVEMENT));
		spoofed = true;
	}

	public void click(int slot, int button, int mode) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null) return;
		minecraft.interactionManager.clickSlot(player.menu.networkId, slot, button, mode, player);
	}

	public void pickup(int slot) {
		click(slot, 0, PICKUP);
	}

	public void quickMove(int slot) {
		click(slot, 0, QUICK_MOVE);
	}

	public void swap(int slot, int hotbar) {
		click(slot, hotbar, SWAP);
	}

	public void drop(int slot) {
		click(slot, 1, THROW);
	}

	@Listen
	public void onPacketSend(PacketSendEvent event) {
		Packet<?> packet = event.packet();
		if (packet instanceof InventoryMenuClickSlotC2SPacket) {
			clickedAt = System.currentTimeMillis();
		} else if (packet instanceof ClientStatusC2SPacket status && status.getStatus() == ClientStatusC2SPacket.Status.OPEN_INVENTORY_ACHIEVEMENT) {
			if (!open) opened();
		} else if (packet instanceof CloseInventoryMenuC2SPacket) {
			closed();
		}
	}

	@Listen
	public void onPacketReceive(PacketReceiveEvent event) {
		Packet<?> packet = event.packet();
		if (packet instanceof OpenInventoryMenuS2CPacket) {
			opened();
			spoofed = false;
		} else if (packet instanceof CloseInventoryMenuS2CPacket) {
			closed();
		}
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		if (!claimed || owner != null && !owner.isEnabled()) owner = null;
		if (claimed) closeAt = 0L;
		claimed = false;
		if (owner != null || !spoofed || minecraft.screen != null) return;

		long now = System.currentTimeMillis();
		if (closeAt == 0L) closeAt = now + ClickTimer.roll(closeDelay);
		if (now >= closeAt) closeSpoofed();
	}

	@Listen
	public void onWorldLoad(WorldLoadEvent event) {
		closed();
		owner = null;
	}

	private void closeSpoofed() {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || inContainer()) {
			closed();
			return;
		}

		if (player.inventory.getCursorItem() != null) {
			int empty = firstEmpty(player.playerMenu);
			if (empty == -1) return;
			pickup(empty);
		}
		player.networkHandler.sendPacket(new CloseInventoryMenuC2SPacket(player.playerMenu.networkId));
	}

	private static boolean moving() {
		InputState input = IllusionClient.INPUT.player;
		return input.forwardAxis() != 0.0F || input.sidewaysAxis() != 0.0F || input.jump;
	}

	private static int firstEmpty(InventoryMenu menu) {
		for (int i = FIRST_STORAGE_SLOT; i < menu.slots.size(); i++) {
			if (!menu.getSlot(i).hasItem()) return i;
		}
		return -1;
	}

	private void opened() {
		open = true;
		openedAt = System.currentTimeMillis();
	}

	private void closed() {
		open = false;
		spoofed = false;
		closeAt = 0L;
	}
}
