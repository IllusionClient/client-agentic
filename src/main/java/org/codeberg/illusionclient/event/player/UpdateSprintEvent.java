package org.codeberg.illusionclient.event.player;

import org.codeberg.illusionclient.event.Event;

public final class UpdateSprintEvent implements Event {
	private static final UpdateSprintEvent INSTANCE = new UpdateSprintEvent();
	public boolean sprinting;

	private UpdateSprintEvent() {
	}

	public static UpdateSprintEvent of(boolean sprinting) {
		INSTANCE.sprinting = sprinting;
		return INSTANCE;
	}
}
