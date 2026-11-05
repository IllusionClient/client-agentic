package org.codeberg.illusionclient.event.player;

public final class RotationSentEvent {
	private static final RotationSentEvent INSTANCE = new RotationSentEvent();
	public float yaw;
	public float pitch;

	private RotationSentEvent() {
	}

	public static RotationSentEvent of(float yaw, float pitch) {
		INSTANCE.yaw = yaw;
		INSTANCE.pitch = pitch;
		return INSTANCE;
	}
}
