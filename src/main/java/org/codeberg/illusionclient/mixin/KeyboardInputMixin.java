package org.codeberg.illusionclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.entity.living.player.Input;
import net.minecraft.client.entity.living.player.KeyboardInput;
import net.minecraft.client.options.KeyBinding;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.util.input.InputState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin extends Input {
	@Inject(method = "tick", at = @At("HEAD"))
	private void illusion$updateInput(CallbackInfo ci) {
		IllusionClient.INPUT.update();
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/options/KeyBinding;isPressed()Z"))
	private boolean illusion$clientInput(KeyBinding key, Operation<Boolean> original) {
		return IllusionClient.INPUT.resolve(key, original.call(key));
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void illusion$enforceSneak(CallbackInfo ci) {
		InputState player = IllusionClient.INPUT.player;
		InputState client = IllusionClient.INPUT.client;
		if (player.sneak == client.sneak || sneaking == client.sneak) return;

		sneaking = client.sneak;
		float scale = sneaking ? 0.3F : 1.0F / 0.3F;
		movementForward *= scale;
		movementSideways *= scale;
	}
}
