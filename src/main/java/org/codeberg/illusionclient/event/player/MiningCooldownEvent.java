package org.codeberg.illusionclient.event.player;

import org.codeberg.illusionclient.event.Event;

public final class MiningCooldownEvent implements Event {
	private static final MiningCooldownEvent INSTANCE = new MiningCooldownEvent();
	public int cooldown;

	private MiningCooldownEvent() {
	}

	public static MiningCooldownEvent of(int cooldown) {
		INSTANCE.cooldown = cooldown;
		return INSTANCE;
	}
}
