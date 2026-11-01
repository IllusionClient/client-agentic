package org.codeberg.illusionclient.event.player;


public final class UseDelayEvent {
	private static final UseDelayEvent INSTANCE = new UseDelayEvent();
	public int delay;

	private UseDelayEvent() {
	}

	public static UseDelayEvent of(int delay) {
		INSTANCE.delay = delay;
		return INSTANCE;
	}
}
