package org.codeberg.illusionclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import org.codeberg.illusionclient.feature.module.visual.FakeBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {
	@ModifyExpressionValue(method = "setModelStatus", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;getItemUseTimer()I"))
	private int illusion$fakeBlock(int timer, ClientPlayerEntity player) {
		return timer > 0 || !FakeBlock.shown(player) ? timer : 1;
	}
}
