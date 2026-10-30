package org.codeberg.illusionclient.util.inventory;

import net.minecraft.inventory.slot.InventorySlot;

import java.util.concurrent.ThreadLocalRandom;

public final class ClickTimer {
	private static final float SLOT_SIZE = 18.0F;
	private static final long TICK = 50L;
	private boolean started;
	private long readyAt;
	private long due;
	private long closeAt;
	private InventorySlot last;

	public boolean started() {
		return started;
	}

	public void start(long from, float[] startDelay) {
		started = true;
		readyAt = from + roll(startDelay);
		closeAt = 0L;
		last = null;
	}

	public void reset() {
		started = false;
		closeAt = 0L;
		last = null;
	}

	public float distance(InventorySlot slot) {
		if (last == null || slot == null) return 0.0F;
		return (float) Math.hypot(slot.x - last.x, slot.y - last.y) / SLOT_SIZE;
	}

	public boolean ready(InventorySlot target, float travelDelay) {
		closeAt = 0L;
		due = readyAt + (long) (travelDelay * Math.log1p(distance(target)) / Math.log(2.0D));
		return System.currentTimeMillis() >= due;
	}

	public void clicked(InventorySlot slot, float[] delay) {
		clicked(slot, delay, 0.0F, null);
	}

	public void clicked(InventorySlot slot, float[] delay, float pauseChance, float[] pause) {
		long now = System.currentTimeMillis();
		if (slot != null) last = slot;
		readyAt = Math.max(due, now - TICK) + roll(delay);
		if (pauseChance > 0.0F && ThreadLocalRandom.current().nextFloat() * 100.0F < pauseChance) readyAt += roll(pause);
	}

	public boolean closing(float[] closeDelay) {
		long now = System.currentTimeMillis();
		if (closeAt == 0L) closeAt = Math.max(now, readyAt) + roll(closeDelay);
		return now >= closeAt;
	}

	public static long roll(float[] range) {
		return (long) (range[0] + ThreadLocalRandom.current().nextFloat() * (range[1] - range[0]));
	}
}
