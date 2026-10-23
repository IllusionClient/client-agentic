package org.codeberg.illusionclient.event.input;

import org.codeberg.illusionclient.event.Event;

public final class ScreenInputEvent implements Event {
	public static final ScreenInputEvent INSTANCE = new ScreenInputEvent();

	private ScreenInputEvent() {
	}
}
