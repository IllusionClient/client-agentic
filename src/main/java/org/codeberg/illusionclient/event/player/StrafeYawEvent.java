package org.codeberg.illusionclient.event.player;

public final class StrafeYawEvent {
	private static final StrafeYawEvent INSTANCE = new StrafeYawEvent();
	public float yaw;

	private StrafeYawEvent() {
	}

	public static StrafeYawEvent of(float yaw) {
		INSTANCE.yaw = yaw;
		return INSTANCE;
	}
}
