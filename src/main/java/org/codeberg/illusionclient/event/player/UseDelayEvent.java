package org.codeberg.illusionclient.event.player;

import org.codeberg.illusionclient.event.Event;

public final class UseDelayEvent implements Event {
	private static final UseDelayEvent INSTANCE = new UseDelayEvent();
	public int delay;

	private UseDelayEvent() {
	}

	public static UseDelayEvent of(int delay) {
		INSTANCE.delay = delay;
		return INSTANCE;
	}
}
