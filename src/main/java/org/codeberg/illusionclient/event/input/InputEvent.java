package org.codeberg.illusionclient.event.input;

import org.codeberg.illusionclient.event.Event;

public final class InputEvent implements Event {
	public static final InputEvent INSTANCE = new InputEvent();

	private InputEvent() {
	}
}
