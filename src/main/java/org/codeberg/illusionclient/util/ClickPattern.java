package org.codeberg.illusionclient.util;

import net.minecraft.util.math.MathHelper;

import java.util.ArrayDeque;
import java.util.Random;

public final class ClickPattern {
	private static final float MIN_UP = 6.0F;
	private static final float PAUSE_SHARE = 0.06F;
	private final Random random = new Random();
	private final Drift phaseCps = new Drift(75_000L);
	private final Drift streakCps = new Drift(18_000L);
	private final Drift jitterCps = new Drift(3_500L);
	private final Drift deviation = new Drift(25_000L);
	private final Drift correlation = new Drift(40_000L);
	private final Drift gapSpread = new Drift(12_000L);
	private final Drift gapWander = new Drift(4_000L);
	private final Drift singleChance = new Drift(30_000L);
	private final Drift exhaustChance = new Drift(240_000L);
	private final Drift exhaustLength = new Drift(60_000L);
	private final Drift hitchChance = new Drift(90_000L);
	private final Drift pauseGap = new Drift(150_000L);
	private final Drift tiring = new Drift(120_000L);
	private final Drift[] holdShare = {new Drift(40_000L), new Drift(55_000L)};
	private final ArrayDeque<float[]> queue = new ArrayDeque<>();
	public float variance = 1.0F;
	public float dropFrequency = 1.0F;
	public float dropLength = 1.0F;
	public float drift = 1.0F;
	public float holdVariance = 1.0F;
	public float gapVariance = 1.0F;
	public float singles = 1.0F;
	public float rampUp = 1.0F;
	private float cps;
	private boolean randomize;
	private boolean simulateExhaust;
	private boolean butterfly;
	private boolean clicking;
	private float wait;
	private boolean down;
	private float hold;
	private int presses;
	private float head;
	private float cursor;
	private float noise;
	private float effort;
	private long stopped;
	private int exhaustClicks;
	private int untilPause;
	private float exhaust;
	private float lastYaw;
	private float lastPitch;
	private float activity;
	private ClickProfile profile;
	private int streak;
	private int position;
	private long rampStart;
	private float rampLength;

	public void tick(float yaw, float pitch) {
		float turned = (float) Math.hypot(MathHelper.wrapDegrees(yaw - lastYaw), pitch - lastPitch);
		lastYaw = yaw;
		lastPitch = pitch;
		activity = activity * 0.6F + Math.min(turned, 30.0F) / 25.0F * 0.4F;
	}

	public void stop() {
		if (clicking) stopped = System.currentTimeMillis();
		clicking = false;
	}

	public int advance(float delta, ClickProfile profile, float cps, boolean randomize, boolean simulateExhaust, boolean butterfly) {
		if (this.profile != profile || this.butterfly != butterfly) clicking = false;
		this.profile = profile;
		this.cps = cps;
		this.randomize = randomize;
		this.simulateExhaust = simulateExhaust;
		this.butterfly = butterfly;

		if (!clicking) {
			long now = System.currentTimeMillis();
			clicking = true;
			queue.clear();
			head = 0.0F;
			cursor = 0.0F;
			noise = 0.0F;
			exhaustClicks = 0;
			untilPause = (int) ((15 + random.nextInt(30)) / dropFrequency);
			down = false;
			streak = -1;
			effort *= (float) Math.exp((stopped - now) / 6000.0);
			rampStart = now;
			rampLength = rampUp * (butterfly ? 500.0F + random.nextFloat() * 500.0F : randomize ? 250.0F + random.nextFloat() * 400.0F : 0.0F);
			wait = next();
		}
		effort += delta;

		int edges = 0;
		presses = 0;
		for (wait -= delta; wait <= 0.0F; wait += edge()) edges++;
		return edges;
	}

	public int presses() {
		return presses;
	}

	private float edge() {
		down = !down;
		if (down) return hold;
		presses++;
		return next();
	}

	private float next() {
		if (profile != null) return replay();
		if (queue.isEmpty()) {
			head -= cursor;
			cursor = 0.0F;
			stroke();
		}

		float[] press = queue.pollFirst();
		hold = press[1] - press[0];
		float gap = press[0] - cursor;
		cursor = press[1];
		return gap;
	}

	private float replay() {
		if (streak < 0) {
			streak = random.nextInt(profile.streaks.size());
			position = random.nextInt(profile.streaks.get(streak).length / 2) * 2;
		} else if (position >= profile.streaks.get(streak).length) {
			streak = (streak + 1) % profile.streaks.size();
			position = 0;
		}

		float[] clicks = profile.streaks.get(streak);
		hold = clicks[position + 1];
		position += 2;
		return clicks[position - 2];
	}

	private void stroke() {
		long now = System.currentTimeMillis();
		float ms = period(now);
		float extra = slowdown(now) + hitch(now);
		if (!butterfly) {
			head += ms + extra;
			push(head, hold(now, 0));
			return;
		}

		boolean single = extra > 0.0F || random.nextFloat() < singleChance(now);
		head += (single ? ms : 2.0F * ms) + extra;
		push(head, hold(now, 0));
		if (!single) push(head + gap(now), hold(now, 1));
	}

	private void push(float start, float hold) {
		float[] last = queue.peekLast();
		start = Math.max(start, (last != null ? last[1] : cursor) + MIN_UP);
		queue.addLast(new float[]{start, start + hold});
	}

	private float ramp(long now) {
		if (rampLength <= 0.0F) return 1.0F;
		float t = Math.clamp((now - rampStart) / rampLength, 0.0F, 1.0F);
		float from = butterfly ? 0.45F : 0.72F;
		return from + (1.0F - from) * t * t * (3.0F - 2.0F * t);
	}

	private float fatigue(long now) {
		if (!randomize) return 0.0F;
		return tiring.range(now, 0.03F, 0.09F) * (1.0F - (float) Math.exp(-effort / 12_000.0F));
	}

	private float period(long now) {
		float base = 1000.0F / (cps * ramp(now));
		if (!randomize) return base;
		if (simulateExhaust) base /= 1.0F + PAUSE_SHARE * dropFrequency * dropLength;

		float fatigue = fatigue(now);
		float drift = 1.0F + this.drift * (phaseCps.signed(now) * 0.1F + streakCps.signed(now) * 0.07F + jitterCps.signed(now) * 0.04F);
		float sigma = variance * deviation.range(now, 0.13F, 0.24F) * (1.0F + fatigue * 2.0F + activity * 0.3F);
		float rho = correlation.range(now, 0.25F, 0.5F);
		noise = noise * rho + (float) random.nextGaussian() * (float) Math.sqrt(1.0F - rho * rho);
		return base / (1.0F - fatigue) / drift * (float) Math.exp(sigma * noise - sigma * sigma / 2.0F);
	}

	private float hold(long now, int finger) {
		if (!randomize) return 500.0F / cps;
		float ms = butterfly
			? holdShare[finger].range(now, 0.2F, 0.34F) * Math.min(1000.0F / cps, 70.0F)
			: holdShare[finger].range(now, 0.28F, 0.45F) * 1000.0F / cps;
		ms = Math.clamp(ms, 12.0F, 90.0F);
		return ms * (float) Math.exp(holdVariance * 0.16F * random.nextGaussian());
	}

	private float gap(long now) {
		if (!randomize) return 30.0F;
		float ms = gapSpread.range(now, 20.0F, 42.0F) + gapVariance * gapWander.signed(now) * 8.0F;
		return Math.max(8.0F, ms * (float) Math.exp(gapVariance * 0.2F * random.nextGaussian()));
	}

	private float singleChance(long now) {
		float chance = randomize ? singles * singleChance.range(now, 0.03F, 0.07F) : 0.0F;
		return chance + (1.0F - ramp(now)) * 0.6F;
	}

	private float hitch(long now) {
		if (!randomize || !simulateExhaust) return 0.0F;
		if (--untilPause <= 0) {
			untilPause = (int) ((30 + random.nextInt((int) pauseGap.range(now, 30.0F, 60.0F))) / dropFrequency);
			float r = random.nextFloat();
			return dropLength * Math.max(100.0F, 1000.0F / cps * (1.5F + 1.5F * r * r));
		}
		float stumble = dropFrequency * (hitchChance.range(now, 0.01F, 0.025F) + activity * 0.005F);
		if (random.nextFloat() < stumble) return dropLength * 1000.0F / cps * (0.3F + 0.5F * random.nextFloat());
		return 0.0F;
	}

	private float slowdown(long now) {
		if (exhaustClicks > 0) {
			exhaustClicks--;
			return exhaust * (1.0F + 0.35F * gaussian());
		}
		if (!simulateExhaust || random.nextFloat() >= dropFrequency * (exhaustChance.range(now, 0.012F, 0.035F) + activity * 0.03F)) {
			return 0.0F;
		}

		float keep = exhaustLength.range(now, 0.1F, 0.25F);
		while (exhaustClicks < 3 && random.nextFloat() < keep) exhaustClicks++;
		exhaust = dropLength * (18.0F + random.nextFloat() * 22.0F);
		return exhaust * (1.0F + 0.35F * gaussian());
	}

	private float gaussian() {
		return (random.nextFloat() + random.nextFloat() + random.nextFloat()) / 1.5F - 1.0F;
	}

	private final class Drift {
		private final long length;
		private float from;
		private float to;
		private long start;
		private long segment;

		private Drift(long length) {
			this.length = length;
		}

		private float range(long now, float min, float max) {
			return min + (max - min) * sample(now);
		}

		private float signed(long now) {
			return sample(now) * 2.0F - 1.0F;
		}

		private float sample(long now) {
			if (segment == 0L || now - start > segment + length * 4L) {
				to = random.nextFloat();
				start = now;
				next();
			}
			while (now - start >= segment) {
				start += segment;
				next();
			}
			float t = (float) (now - start) / segment;
			return from + (to - from) * t * t * (3.0F - 2.0F * t);
		}

		private void next() {
			from = to;
			float jump = (random.nextBoolean() ? 1.0F : -1.0F) * (0.25F + random.nextFloat() * 0.5F);
			to = from + jump;
			if (to < 0.0F || to > 1.0F) to = Math.clamp(from - jump, 0.0F, 1.0F);
			segment = (long) (length * (0.6F + random.nextFloat() * 0.8F));
		}
	}
}
