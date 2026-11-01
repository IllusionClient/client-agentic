package org.codeberg.illusionclient.event.render;


public final class Render3DEvent {
	private static final Render3DEvent INSTANCE = new Render3DEvent();
	private float partialTicks;

	private Render3DEvent() {
	}

	public static Render3DEvent of(float partialTicks) {
		INSTANCE.partialTicks = partialTicks;
		return INSTANCE;
	}

	public float partialTicks() {
		return partialTicks;
	}
}
