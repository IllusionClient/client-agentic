package org.codeberg.illusionclient.event.player;


public final class MotionEvent {
	private static final MotionEvent INSTANCE = new MotionEvent();
	public boolean pre;

	private MotionEvent() {
	}

	public static MotionEvent of(boolean pre) {
		INSTANCE.pre = pre;
		return INSTANCE;
	}
}
