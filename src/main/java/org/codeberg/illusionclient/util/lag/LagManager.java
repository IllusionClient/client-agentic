package org.codeberg.illusionclient.util.lag;

import io.github.nevalackin.radbus.Listen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.handler.PacketHandler;
import net.minecraft.network.packet.Packet;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.PacketReceiveEvent;
import org.codeberg.illusionclient.event.PacketSendEvent;
import org.codeberg.illusionclient.event.PlayerTickEvent;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

public final class LagManager {
	private final Minecraft minecraft = Minecraft.getInstance();
	private final Queue<Held> inbound = new ArrayDeque<>();
	private final Queue<Held> outbound = new ConcurrentLinkedQueue<>();
	private final List<Lag> lags = new CopyOnWriteArrayList<>();

	public LagManager() {
		IllusionClient.BUS.subscribe(this);
	}

	public Lag create() {
		return create(packet -> true);
	}

	public Lag create(Predicate<Packet<?>> filter) {
		return add(new Lag(filter, false));
	}

	public Lag createOutbound() {
		return createOutbound(packet -> true);
	}

	public Lag createOutbound(Predicate<Packet<?>> filter) {
		return add(new Lag(filter, true));
	}

	private Lag add(Lag lag) {
		lags.add(lag);
		return lag;
	}

	public boolean holdsKeepAlives() {
		for (Lag lag : lags) {
			if (lag.active && !lag.outbound && lag.holdKeepAlives) return true;
		}
		return false;
	}

	@Listen
	public void onPacketReceive(PacketReceiveEvent event) {
		if (inbound.isEmpty() && !wanted(event.packet(), false)) return;

		Packet<PacketHandler> packet = event.packet();
		PacketHandler listener = event.listener();
		event.cancelled = true;
		inbound.add(new Held(packet, false, () -> packet.handle(listener), System.currentTimeMillis()));
	}

	@Listen
	public void onPacketSend(PacketSendEvent event) {
		if (outbound.isEmpty() && !wanted(event.packet(), true)) return;

		event.cancelled = true;
		outbound.add(new Held(event.packet(), true, () -> event.connection().send(event.packet()), System.currentTimeMillis()));
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		flush();
	}

	private boolean wanted(Packet<?> packet, boolean outbound) {
		for (Lag lag : lags) {
			if (lag.active && lag.outbound == outbound && lag.filter.test(packet)) return true;
		}
		return false;
	}

	private boolean holds(Held held, long now) {
		for (Lag lag : lags) {
			if (lag.active && lag.outbound == held.outbound && now - held.time < lag.delay && lag.filter.test(held.packet)) return true;
		}
		return false;
	}

	private void flush() {
		if (minecraft.world == null || minecraft.player == null) {
			inbound.clear();
			outbound.clear();
			return;
		}

		long now = System.currentTimeMillis();
		flush(inbound, now);
		flush(outbound, now);
	}

	private void flush(Queue<Held> packets, long now) {
		while (!packets.isEmpty() && !holds(packets.peek(), now)) {
			Held held = packets.poll();
			try {
				held.release.run();
			} catch (RuntimeException e) {
				IllusionClient.LOGGER.error("Failed to release held {}", held.packet.getClass().getSimpleName(), e);
			}
		}
	}

	private record Held(Packet<?> packet, boolean outbound, Runnable release, long time) {
	}

	public final class Lag {
		private final Predicate<Packet<?>> filter;
		private final boolean outbound;
		private volatile boolean active;
		private volatile boolean holdKeepAlives = true;
		private long delay;

		private Lag(Predicate<Packet<?>> filter, boolean outbound) {
			this.filter = filter;
			this.outbound = outbound;
		}

		public boolean isActive() {
			return active;
		}

		public void holdKeepAlives(boolean hold) {
			holdKeepAlives = hold;
		}

		public void hold(long delay) {
			this.delay = delay;
			active = true;
		}

		public void release() {
			if (!active) return;
			active = false;
			flush();
		}
	}
}
