package org.codeberg.illusionclient.util;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Mouse;

public final class MouseUtils {
	private static final int BUTTONS = 16;
	private static final int[] pendingEvents = new int[BUTTONS];
	private static final boolean[] lifted = new boolean[BUTTONS];
	private static int button;
	private static boolean synthetic;

	public static void toggle(int button) {
		if (button >= 0 && button < BUTTONS) pendingEvents[button]++;
	}

	public static void resync(int button) {
		if (button < 0 || button >= BUTTONS) return;
		pendingEvents[button] = lifted[button] && Mouse.isButtonDown(button) ? 1 : 0;
		if (pendingEvents[button] == 0) lifted[button] = false;
	}

	public static boolean next(boolean hasEvent) {
		synthetic = false;
		if (hasEvent) return true;
		boolean screen = Minecraft.getInstance().screen != null;
		for (int i = 0; i < BUTTONS; i++) {
			if (lifted[i] && (screen || !Mouse.isButtonDown(i))) {
				pendingEvents[i] = 0;
				lifted[i] = false;
			}
			if (pendingEvents[i] == 0) continue;
			pendingEvents[i]--;
			lifted[i] = !lifted[i];
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
		return !lifted[button];
	}
}
