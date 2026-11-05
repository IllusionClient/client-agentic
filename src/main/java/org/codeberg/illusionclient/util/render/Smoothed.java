package org.codeberg.illusionclient.util.render;

// FIXME: Integrate into Render2D.
public class Smoothed {
	private long last;
	private float value;
	private boolean set;

	public float update(float target, float speed) {
		long now = System.nanoTime();
		float dt = set ? Math.min((now - last) / 1.0E9F, 0.25F) : 0.0F;
		last = now;
		if (!set) {
			set = true;
			value = target;
		}
		value += (target - value) * (1.0F - (float) Math.exp(-speed * dt));
		return value;
	}

	public void snap() {
		set = false;
	}
}
