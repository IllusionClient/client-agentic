package org.codeberg.illusionclient.event.network;

import net.minecraft.network.Connection;
import net.minecraft.network.packet.Packet;
import org.codeberg.illusionclient.event.CancellableEvent;

public final class PacketSendEvent extends CancellableEvent {
	private final Packet<?> packet;
	private final Connection connection;

	public PacketSendEvent(Packet<?> packet, Connection connection) {
		this.packet = packet;
		this.connection = connection;
	}

	public Packet<?> packet() {
		return packet;
	}

	public Connection connection() {
		return connection;
	}
}
