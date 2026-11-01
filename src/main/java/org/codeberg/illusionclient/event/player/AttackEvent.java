package org.codeberg.illusionclient.event.player;


public final class AttackEvent {
	private static final AttackEvent INSTANCE = new AttackEvent();
	public boolean resetCooldown;

	private AttackEvent() {
	}

	public static AttackEvent of() {
		INSTANCE.resetCooldown = false;
		return INSTANCE;
	}
}
