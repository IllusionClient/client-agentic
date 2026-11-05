package org.codeberg.illusionclient.event.player;

public final class ServerYawEvent {
	private static final ServerYawEvent INSTANCE = new ServerYawEvent();
	public float yaw;

	private ServerYawEvent() {
	}

	public static ServerYawEvent of(float yaw) {
		INSTANCE.yaw = yaw;
		return INSTANCE;
	}
}
