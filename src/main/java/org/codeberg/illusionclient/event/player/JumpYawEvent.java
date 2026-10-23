package org.codeberg.illusionclient.event.player;

import org.codeberg.illusionclient.event.Event;

public final class JumpYawEvent implements Event {
	private static final JumpYawEvent INSTANCE = new JumpYawEvent();
	public float yaw;

	private JumpYawEvent() {
	}

	public static JumpYawEvent of(float yaw) {
		INSTANCE.yaw = yaw;
		return INSTANCE;
	}
}
