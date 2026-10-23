package org.codeberg.illusionclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.options.KeyBinding;
import org.codeberg.illusionclient.util.KeyboardUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyBinding.class)
public class KeyBindingMixin {
	@ModifyReturnValue(method = "isPressed", at = @At("RETURN"))
	private boolean illusion$isPressed(boolean pressed) {
		return KeyboardUtils.pressed((KeyBinding) (Object) this, pressed);
	}
}
