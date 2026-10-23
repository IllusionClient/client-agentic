package org.codeberg.illusionclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.options.KeyBinding;
import org.lwjgl.input.Keyboard;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class KeyboardUtils {
	private static final Map<Integer, Boolean> pending = new LinkedHashMap<>();
	private static final Map<Integer, Boolean> overridden = new HashMap<>();
	private static int key;
	private static boolean state;
	private static boolean synthetic;

	public static void set(KeyBinding binding, boolean pressed) {
		int code = binding.getKeyCode();
		if (code <= 0 || code >= Keyboard.KEYBOARD_SIZE) return;
		if (binding.isPressed() == pressed) pending.remove(code);
		else pending.put(code, pressed);
	}

	public static boolean overridden(KeyBinding binding) {
		return overridden.containsKey(binding.getKeyCode());
	}

	public static boolean pressed(KeyBinding binding, boolean pressed) {
		Boolean state = overridden.get(binding.getKeyCode());
		return state == null ? pressed : state;
	}

	public static boolean next(boolean hasEvent) {
		synthetic = false;
		if (hasEvent) {
			int code = Keyboard.getEventKey();
			pending.remove(code);
			overridden.remove(code);
			return true;
		}
		if (Minecraft.getInstance().screen != null) {
			pending.clear();
			overridden.clear();
		}
		if (pending.isEmpty()) return false;
		Iterator<Map.Entry<Integer, Boolean>> iterator = pending.entrySet().iterator();
		Map.Entry<Integer, Boolean> event = iterator.next();
		iterator.remove();
		key = event.getKey();
		state = event.getValue();
		overridden.put(key, state);
		return synthetic = true;
	}

	public static boolean synthetic() {
		return synthetic;
	}

	public static int eventKey() {
		return key;
	}

	public static boolean eventState() {
		return state;
	}
}
