package org.codeberg.illusionclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.codeberg.illusionclient.feature.module.visual.Chams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	@Shadow
	public abstract boolean shouldRenderShadow();

	@WrapOperation(method = "render(Lnet/minecraft/entity/Entity;DDDFFZ)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;render(Lnet/minecraft/entity/Entity;DDDFF)V"))
	private void illusion$chams(EntityRenderer<Entity> renderer, Entity entity, double x, double y, double z, float yaw, float tickDelta, Operation<Void> original) {
		if (!shouldRenderShadow() || !Chams.shows(entity)) {
			original.call(renderer, entity, x, y, z, yaw, tickDelta);
			return;
		}
		Chams.begin();
		try {
			original.call(renderer, entity, x, y, z, yaw, tickDelta);
		} finally {
			Chams.end(entity, tickDelta);
		}
	}
}
