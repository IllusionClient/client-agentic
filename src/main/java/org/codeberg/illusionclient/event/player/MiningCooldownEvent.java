package org.codeberg.illusionclient.event.player;


public final class MiningCooldownEvent {
	private static final MiningCooldownEvent INSTANCE = new MiningCooldownEvent();
	public int cooldown;

	private MiningCooldownEvent() {
	}

	public static MiningCooldownEvent of(int cooldown) {
		INSTANCE.cooldown = cooldown;
		return INSTANCE;
	}
}
