package org.codeberg.illusionclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.feature.module.visual.Nametags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin extends EntityRenderer<LivingEntity> {
	protected LivingEntityRendererMixin(EntityRenderDispatcher dispatcher) {
		super(dispatcher);
	}

	@ModifyReturnValue(method = "shouldRenderNameTag(Lnet/minecraft/entity/living/LivingEntity;)Z", at = @At("RETURN"))
	private boolean illusion$hideNameTag(boolean shown, LivingEntity entity) {
		return shown && !(entity instanceof PlayerEntity && IllusionClient.MODULES.get(Nametags.class).isEnabled());
	}

	@Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At("HEAD"))
	private void illusion$serverRotationModel(LivingEntity entity, double x, double y, double z, float yaw, float tickDelta,
		CallbackInfo ci, @Share("swapped") LocalBooleanRef swapped) {
		swapped.set(dispatcher.shouldRenderShadow() && IllusionClient.ROTATION.swapModelRotation(entity));
	}

	@Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At("RETURN"))
	private void illusion$restoreRotationModel(LivingEntity entity, double x, double y, double z, float yaw, float tickDelta,
		CallbackInfo ci, @Share("swapped") LocalBooleanRef swapped) {
		if (swapped.get()) IllusionClient.ROTATION.restoreModelRotation(entity);
	}
}
