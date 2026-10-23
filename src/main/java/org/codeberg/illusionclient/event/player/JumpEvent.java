package org.codeberg.illusionclient.event.player;

import org.codeberg.illusionclient.event.CancellableEvent;

public final class JumpEvent extends CancellableEvent {
	private static final JumpEvent INSTANCE = new JumpEvent();

	private JumpEvent() {
	}

	public static JumpEvent of() {
		INSTANCE.cancelled = false;
		return INSTANCE;
	}
}
