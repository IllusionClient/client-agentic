package org.codeberg.illusionclient.event.player;


public final class UpdateSprintEvent {
	private static final UpdateSprintEvent INSTANCE = new UpdateSprintEvent();
	public boolean sprinting;

	private UpdateSprintEvent() {
	}

	public static UpdateSprintEvent of(boolean sprinting) {
		INSTANCE.sprinting = sprinting;
		return INSTANCE;
	}
}
