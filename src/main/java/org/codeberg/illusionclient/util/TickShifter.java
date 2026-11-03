package org.codeberg.illusionclient.util;

import org.codeberg.illusionclient.event.TimerEvent;

public final class TickShifter {
	private static TickShifter owner;
	private int remaining;
	private int frozen;
	private float heldPartialTick;

	public boolean idle() {
		return remaining == 0 && frozen == 0;
	}

	public boolean available() {
		return idle() && (owner == null || owner == this);
	}

	public boolean bursting() {
		return remaining > 0;
	}

	public int skip(TimerEvent event, int ticks) {
		int debt = ticks - event.ticks;
		owner = this;
		remaining = ticks;
		event.ticks = ticks;
		return debt;
	}

	public boolean step() {
		return --remaining == 0;
	}

	public void freeze(int ticks) {
		frozen = ticks;
		if (ticks > 0) owner = this;
	}

	public boolean onTimer(TimerEvent event) {
		boolean thawed = false;
		if (frozen > 0) {
			int swallowed = Math.min(event.ticks, frozen);
			frozen -= swallowed;
			event.ticks -= swallowed;
			if (frozen > 0 || event.ticks == 0) event.partialTick = heldPartialTick;
			thawed = frozen == 0;
		}
		heldPartialTick = event.partialTick;
		if (owner == this && idle()) owner = null;
		return thawed;
	}

	public void reset() {
		remaining = frozen = 0;
		if (owner == this) owner = null;
	}
}
