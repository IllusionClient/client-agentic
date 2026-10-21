package org.codeberg.illusionclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.codeberg.illusionclient.util.KeyboardUtils;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Keyboard.class, remap = false)
public class KeyboardMixin {
	@ModifyReturnValue(method = "next", at = @At("RETURN"))
	private static boolean illusion$next(boolean hasEvent) {
		return KeyboardUtils.next(hasEvent);
	}

	@ModifyReturnValue(method = "getEventKey", at = @At("RETURN"))
	private static int illusion$eventKey(int key) {
		return KeyboardUtils.synthetic() ? KeyboardUtils.eventKey() : key;
	}

	@ModifyReturnValue(method = "getEventKeyState", at = @At("RETURN"))
	private static boolean illusion$eventKeyState(boolean state) {
		return KeyboardUtils.synthetic() ? KeyboardUtils.eventState() : state;
	}

	@ModifyReturnValue(method = "getEventCharacter", at = @At("RETURN"))
	private static char illusion$eventCharacter(char character) {
		return KeyboardUtils.synthetic() ? Keyboard.CHAR_NONE : character;
	}

	@ModifyReturnValue(method = "isRepeatEvent", at = @At("RETURN"))
	private static boolean illusion$repeatEvent(boolean repeat) {
		return !KeyboardUtils.synthetic() && repeat;
	}
}
