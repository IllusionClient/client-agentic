package org.codeberg.illusionclient.event.player;

import org.codeberg.illusionclient.event.Event;

public final class AttackEvent implements Event {
	private static final AttackEvent INSTANCE = new AttackEvent();
	public boolean resetCooldown;

	private AttackEvent() {
	}

	public static AttackEvent of() {
		INSTANCE.resetCooldown = false;
		return INSTANCE;
	}
}
