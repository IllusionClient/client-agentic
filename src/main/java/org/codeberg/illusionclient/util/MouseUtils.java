package org.codeberg.illusionclient.util;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Mouse;

public final class MouseUtils {
	private static final int BUTTONS = 16;
	private static final int[] pendingEvents = new int[BUTTONS];
	private static final boolean[] flipped = new boolean[BUTTONS];
	private static final boolean[] physical = new boolean[BUTTONS];
	private static int button;
	private static boolean synthetic;

	public static void toggle(int button) {
		if (button >= 0 && button < BUTTONS) pendingEvents[button]++;
	}

	public static void resync(int button) {
		if (button < 0 || button >= BUTTONS) return;
		pendingEvents[button] = flipped[button] ? 1 : 0;
	}

	public static boolean next(boolean hasEvent) {
		synthetic = false;
		if (hasEvent) return true;
		boolean screen = Minecraft.getInstance().screen != null;
		for (int i = 0; i < BUTTONS; i++) {
			boolean down = Mouse.isButtonDown(i);
			if (flipped[i] && (screen || down != physical[i])) {
				pendingEvents[i] = 0;
				flipped[i] = false;
			}
			if (pendingEvents[i] == 0) continue;
			pendingEvents[i]--;
			flipped[i] = !flipped[i];
			physical[i] = down;
			button = i;
			return synthetic = true;
		}
		return false;
	}

	public static boolean synthetic() {
		return synthetic;
	}

	public static int eventButton() {
		return button;
	}

	public static boolean eventState() {
		return physical[button] != flipped[button];
	}
}
