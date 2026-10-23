package org.codeberg.illusionclient.event.world;

import org.codeberg.illusionclient.event.Event;

public final class WorldLoadEvent implements Event {
	public static final WorldLoadEvent INSTANCE = new WorldLoadEvent();

	private WorldLoadEvent() {
	}
}
