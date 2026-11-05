package org.codeberg.illusionclient.event.player;

public final class ServerPitchEvent {
	private static final ServerPitchEvent INSTANCE = new ServerPitchEvent();
	public float pitch;

	private ServerPitchEvent() {
	}

	public static ServerPitchEvent of(float pitch) {
		INSTANCE.pitch = pitch;
		return INSTANCE;
	}
}
