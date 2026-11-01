package org.codeberg.illusionclient.event.player;


public final class JumpYawEvent {
	private static final JumpYawEvent INSTANCE = new JumpYawEvent();
	public float yaw;

	private JumpYawEvent() {
	}

	public static JumpYawEvent of(float yaw) {
		INSTANCE.yaw = yaw;
		return INSTANCE;
	}
}
