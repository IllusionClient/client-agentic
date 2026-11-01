package org.codeberg.illusionclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import org.codeberg.illusionclient.feature.module.visual.Chams;
import org.codeberg.illusionclient.feature.module.visual.FakeBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {
	@ModifyExpressionValue(method = "setModelStatus", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;getItemUseTimer()I"))
	private int illusion$fakeBlock(int timer, ClientPlayerEntity player) {
		return timer > 0 || !FakeBlock.shown(player) ? timer : 1;
	}

	@Inject(method = "renderRightArm", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/entity/PlayerModel;renderRightArm()V"))
	private void illusion$chamsHand(ClientPlayerEntity player, CallbackInfo ci, @Share("chams") LocalBooleanRef applied) {
		applied.set(Chams.showsHand());
		if (applied.get()) Chams.beginHand();
	}

	@Inject(method = "renderRightArm", at = @At("RETURN"))
	private void illusion$restoreChamsHand(ClientPlayerEntity player, CallbackInfo ci, @Share("chams") LocalBooleanRef applied) {
		if (applied.get()) Chams.endHand(player);
	}
}
