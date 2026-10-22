package org.codeberg.illusionclient.event;

public final class TimerEvent implements Event {
	private static final TimerEvent INSTANCE = new TimerEvent();
	public int ticks;
	public float partialTick;
	public float speed;

	private TimerEvent() {
	}

	public static TimerEvent of(int ticks, float partialTick) {
		INSTANCE.ticks = ticks;
		INSTANCE.partialTick = partialTick;
		INSTANCE.speed = 1.0F;
		return INSTANCE;
	}
}
