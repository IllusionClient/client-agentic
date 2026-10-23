package org.codeberg.illusionclient.event.player;

import org.codeberg.illusionclient.event.CancellableEvent;

public final class SwingEvent extends CancellableEvent {
	private static final SwingEvent INSTANCE = new SwingEvent();

	private SwingEvent() {
	}

	public static SwingEvent of() {
		INSTANCE.cancelled = false;
		return INSTANCE;
	}
}
