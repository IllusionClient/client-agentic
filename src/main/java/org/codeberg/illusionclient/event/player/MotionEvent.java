package org.codeberg.illusionclient.event.player;

import org.codeberg.illusionclient.event.Event;

public final class MotionEvent implements Event {
	private static final MotionEvent INSTANCE = new MotionEvent();
	public boolean pre;

	private MotionEvent() {
	}

	public static MotionEvent of(boolean pre) {
		INSTANCE.pre = pre;
		return INSTANCE;
	}
}
