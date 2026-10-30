package org.codeberg.illusionclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.ItemInHandRenderer;
import org.codeberg.illusionclient.feature.module.visual.FakeBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
	@ModifyExpressionValue(method = "renderInFirstPerson", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;getItemUseTimer()I"))
	private int illusion$fakeBlock(int timer) {
		return timer > 0 || !FakeBlock.shown(Minecraft.getInstance().player) ? timer : 1;
	}
}
