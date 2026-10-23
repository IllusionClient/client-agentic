package org.codeberg.illusionclient.event.player;

import org.codeberg.illusionclient.event.Event;

public final class PlayerTickEvent implements Event {
	public static final PlayerTickEvent INSTANCE = new PlayerTickEvent();

	private PlayerTickEvent() {
	}
}
