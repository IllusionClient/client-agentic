package org.codeberg.illusionclient.event.player;

import org.codeberg.illusionclient.event.Event;

public final class ItemSlowdownEvent implements Event {
	private static final ItemSlowdownEvent INSTANCE = new ItemSlowdownEvent();
	public float multiplier;

	private ItemSlowdownEvent() {
	}

	public static ItemSlowdownEvent of(float multiplier) {
		INSTANCE.multiplier = multiplier;
		return INSTANCE;
	}
}
