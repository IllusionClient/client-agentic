package org.codeberg.illusionclient.util.combat;

import io.github.nevalackin.radbus.Listen;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityEventS2CPacket;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.PacketReceiveEvent;
import org.codeberg.illusionclient.event.PacketSendEvent;
import org.codeberg.illusionclient.event.PlayerTickEvent;

public final class CombatManager {
	private static final byte HURT = 2;
	private static final byte DEATH = 3;
	private static final int HIT_WINDOW = 10;

	private final Minecraft minecraft = Minecraft.getInstance();
	private Entity target;
	private int combo;
	private int sinceAttack = Integer.MAX_VALUE;

	public CombatManager() {
		IllusionClient.BUS.subscribe(this);
	}

	public Entity target() {
		return target;
	}

	public int combo() {
		return combo;
	}

	@Listen
	public void onPacketSend(PacketSendEvent event) {
		if (!(event.packet() instanceof PlayerInteractEntityC2SPacket packet) || minecraft.world == null) return;
		if (packet.getAction() != PlayerInteractEntityC2SPacket.Action.ATTACK) return;

		Entity attacked = packet.getInteractTarget(minecraft.world);
		if (attacked == null) return;
		if (attacked != target) combo = 0;
		target = attacked;
		sinceAttack = 0;
	}

	@Listen
	public void onPacketReceive(PacketReceiveEvent event) {
		Packet<?> received = event.packet();
		if (!(received instanceof EntityEventS2CPacket packet) || minecraft.world == null) return;

		Entity entity = packet.getEntity(minecraft.world);
		if (entity == null) return;

		if (entity == minecraft.player) {
			if (packet.getEvent() == HURT || packet.getEvent() == DEATH) combo = 0;
		} else if (entity == target) {
			if (packet.getEvent() == HURT && sinceAttack <= HIT_WINDOW) combo++;
			else if (packet.getEvent() == DEATH) reset();
		}
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		if (minecraft.world == null || minecraft.player == null) {
			reset();
			return;
		}

		if (sinceAttack < Integer.MAX_VALUE) sinceAttack++;
		if (target != null && target.removed) reset();
	}

	private void reset() {
		target = null;
		combo = 0;
		sinceAttack = Integer.MAX_VALUE;
	}
}
