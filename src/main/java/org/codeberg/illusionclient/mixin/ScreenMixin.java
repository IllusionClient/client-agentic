package org.codeberg.illusionclient.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.gui.screen.Screen;
import org.codeberg.illusionclient.feature.ui.ClickGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Screen.class)
public class ScreenMixin {
	@WrapWithCondition(method = "handleInputs", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;handleMouse()V"))
	private boolean illusion$handleMouse(Screen screen) {
		return !ClickGui.handleMouse(screen);
	}

	@WrapWithCondition(method = "handleInputs", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;handleKeyboard()V"))
	private boolean illusion$handleKeyboard(Screen screen) {
		return !ClickGui.handleKeyboard(screen);
	}
}
