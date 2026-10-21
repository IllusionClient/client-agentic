package org.codeberg.illusionclient.event;

import net.minecraft.network.Connection;
import net.minecraft.network.packet.Packet;

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
