package org.codeberg.illusionclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.options.KeyBinding;
import org.lwjgl.input.Keyboard;

import it.unimi.dsi.fastutil.ints.Int2BooleanLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;

public final class KeyboardUtils {
	private static final Int2BooleanLinkedOpenHashMap pending = new Int2BooleanLinkedOpenHashMap();
	private static final Int2BooleanMap overridden = new Int2BooleanOpenHashMap();
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
		int code = binding.getKeyCode();
		return overridden.containsKey(code) ? overridden.get(code) : pressed;
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
		key = pending.firstIntKey();
		state = pending.removeFirstBoolean();
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
