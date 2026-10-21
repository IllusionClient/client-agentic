package org.codeberg.illusionclient.util;

public final class NoiseUtils {
	public static float noise(double x) {
		long cell = (long) Math.floor(x);
		float t = (float) (x - cell);
		float from = gradient(cell) * t;
		float to = gradient(cell + 1L) * (t - 1.0F);
		return 2.0F * (from + (to - from) * t * t * (3.0F - 2.0F * t));
	}

	public static float wander(float min, float max, float period, int channel) {
		float noise = noise(System.nanoTime() / 1.0E9D / period + channel * 1000.0D);
		return min + (max - min) * (noise + 1.0F) / 2.0F;
	}

	private static float gradient(long cell) {
		return Long.hashCode(cell * 0x9E3779B97F4A7C15L) / (float) Integer.MAX_VALUE;
	}
}
