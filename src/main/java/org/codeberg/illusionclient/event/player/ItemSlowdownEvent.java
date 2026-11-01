package org.codeberg.illusionclient.event.player;


public final class ItemSlowdownEvent {
	private static final ItemSlowdownEvent INSTANCE = new ItemSlowdownEvent();
	public float multiplier;

	private ItemSlowdownEvent() {
	}

	public static ItemSlowdownEvent of(float multiplier) {
		INSTANCE.multiplier = multiplier;
		return INSTANCE;
	}
}
