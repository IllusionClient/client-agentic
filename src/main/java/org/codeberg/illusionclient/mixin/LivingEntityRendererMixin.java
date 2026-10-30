package org.codeberg.illusionclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.feature.module.visual.Nametags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
	@ModifyReturnValue(method = "shouldRenderNameTag(Lnet/minecraft/entity/living/LivingEntity;)Z", at = @At("RETURN"))
	private boolean illusion$hideNameTag(boolean shown, LivingEntity entity) {
		return shown && !(entity instanceof PlayerEntity && IllusionClient.MODULES.get(Nametags.class).isEnabled());
	}
}
