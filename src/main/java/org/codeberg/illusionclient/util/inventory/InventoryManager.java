package org.codeberg.illusionclient.util.inventory;

import io.github.nevalackin.radbus.Listen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseInventoryMenuC2SPacket;
import net.minecraft.network.packet.c2s.play.InventoryMenuClickSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.CloseInventoryMenuS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenInventoryMenuS2CPacket;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.PacketReceiveEvent;
import org.codeberg.illusionclient.event.PacketSendEvent;
import org.codeberg.illusionclient.event.PlayerTickEvent;
import org.codeberg.illusionclient.event.WorldLoadEvent;
import org.codeberg.illusionclient.feature.module.api.Module;

public final class InventoryManager {
	public static final int PICKUP = 0;
	public static final int QUICK_MOVE = 1;
	public static final int SWAP = 2;
	public static final int THROW = 4;

	private final Minecraft minecraft = Minecraft.getInstance();
	private Module owner;
	private boolean open;
	private int sinceOpen;
	private int sinceClick = Integer.MAX_VALUE;

	public InventoryManager() {
		IllusionClient.BUS.subscribe(this);
	}

	public boolean claim(Module module) {
		if (owner != null && owner != module) return false;
		owner = module;
		return true;
	}

	public void release(Module module) {
		if (owner == module) owner = null;
	}

	public boolean open() {
		return open;
	}

	public int sinceOpen() {
		return open ? sinceOpen : 0;
	}

	public int sinceClick() {
		return sinceClick;
	}

	public boolean inContainer() {
		LocalClientPlayerEntity player = minecraft.player;
		return player != null && player.menu != player.playerMenu;
	}

	public void openInventory() {
		if (open || inContainer() || minecraft.player == null) return;
		minecraft.player.networkHandler.sendPacket(new ClientStatusC2SPacket(ClientStatusC2SPacket.Status.OPEN_INVENTORY_ACHIEVEMENT));
	}

	public void closeInventory() {
		if (!open || minecraft.screen != null || minecraft.player == null) return;
		minecraft.player.networkHandler.sendPacket(new CloseInventoryMenuC2SPacket(minecraft.player.menu.networkId));
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
			sinceClick = 0;
		} else if (packet instanceof ClientStatusC2SPacket status && status.getStatus() == ClientStatusC2SPacket.Status.OPEN_INVENTORY_ACHIEVEMENT) {
			opened();
		} else if (packet instanceof CloseInventoryMenuC2SPacket) {
			open = false;
		}
	}

	@Listen
	public void onPacketReceive(PacketReceiveEvent event) {
		Packet<?> packet = event.packet();
		if (packet instanceof OpenInventoryMenuS2CPacket) opened();
		else if (packet instanceof CloseInventoryMenuS2CPacket) open = false;
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		if (sinceOpen < Integer.MAX_VALUE) sinceOpen++;
		if (sinceClick < Integer.MAX_VALUE) sinceClick++;
		if (owner != null && !owner.isEnabled()) owner = null;
	}

	@Listen
	public void onWorldLoad(WorldLoadEvent event) {
		open = false;
		owner = null;
	}

	private void opened() {
		open = true;
		sinceOpen = 0;
	}
}
