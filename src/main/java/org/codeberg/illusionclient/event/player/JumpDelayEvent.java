package org.codeberg.illusionclient.event.player;


public final class JumpDelayEvent {
	private static final JumpDelayEvent INSTANCE = new JumpDelayEvent();
	public int ticks;

	private JumpDelayEvent() {
	}

	public static JumpDelayEvent of(int ticks) {
		INSTANCE.ticks = ticks;
		return INSTANCE;
	}
}
