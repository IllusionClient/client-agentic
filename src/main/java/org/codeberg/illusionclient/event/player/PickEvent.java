package org.codeberg.illusionclient.event.player;

import org.codeberg.illusionclient.event.Event;

public final class PickEvent implements Event {
	public static final PickEvent INSTANCE = new PickEvent();

	private PickEvent() {
	}
}
