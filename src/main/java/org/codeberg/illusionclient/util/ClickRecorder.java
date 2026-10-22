package org.codeberg.illusionclient.util;

import it.unimi.dsi.fastutil.floats.FloatArrayList;

import java.util.ArrayList;

public final class ClickRecorder {
	private static final float MAX_HOLD = 150.0F;
	private static final float MAX_GAP = 250.0F;
	private static final int MIN_CLICKS = 8;
	private static final float MIN_CPS = 5.0F;

	public final ClickProfile profile = new ClickProfile(new ArrayList<>());
	private final FloatArrayList streak = new FloatArrayList();
	private long pressed = -1L;
	private long released = -1L;
	private float hold = -1.0F;

	public boolean input(boolean down, long nanos) {
		if (down) {
			boolean saved = false;
			if (hold > 0.0F) {
				float gap = (nanos - released) / 1.0E6F;
				if (gap <= MAX_GAP) {
					streak.add(hold);
					streak.add(gap);
				} else {
					saved = end();
				}
			}
			pressed = nanos;
			hold = -1.0F;
			return saved;
		}

		if (pressed < 0L) return false;
		float held = (nanos - pressed) / 1.0E6F;
		pressed = -1L;
		if (held > MAX_HOLD) return interrupt();

		hold = held;
		released = nanos;
		return false;
	}

	public int clicks() {
		return profile.clicks() + streak.size() / 2;
	}

	public boolean interrupt() {
		pressed = -1L;
		hold = -1.0F;
		return end();
	}

	private boolean end() {
		float ms = 0.0F;
		for (int i = 0; i < streak.size(); i++) ms += streak.getFloat(i);
		int clicks = streak.size() / 2;
		boolean keep = clicks >= MIN_CLICKS && clicks * 1000.0F / ms >= MIN_CPS;
		if (keep) profile.streaks.add(streak.toFloatArray());
		streak.clear();
		return keep;
	}
}
