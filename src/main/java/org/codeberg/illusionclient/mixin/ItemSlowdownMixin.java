package org.codeberg.illusionclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.player.ItemSlowdownEvent;
import org.codeberg.illusionclient.event.player.MotionEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalClientPlayerEntity.class)
public abstract class ItemSlowdownMixin {

	@ModifyExpressionValue(method = "mobTick", at = @At(value = "CONSTANT", args = "floatValue=0.2F"), require = 2)
	private float illusion$itemSlowdown(float multiplier) {
		ItemSlowdownEvent event = ItemSlowdownEvent.of(multiplier);
		IllusionClient.BUS.publish(event);
		return event.multiplier;
	}

	@Inject(method = "sendMovementToServer", at = @At("HEAD"))
	private void illusion$preMotion(CallbackInfo ci) {
		IllusionClient.BUS.publish(MotionEvent.of(true));
	}

	@Inject(method = "sendMovementToServer", at = @At("RETURN"))
	private void illusion$postMotion(CallbackInfo ci) {
		IllusionClient.BUS.publish(MotionEvent.of(false));
	}
}
