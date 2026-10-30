package org.codeberg.illusionclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.world.World;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.render.EffectVisualEvent;
import org.codeberg.illusionclient.event.player.UpdateSprintEvent;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalClientPlayerEntity.class)
public abstract class LocalClientPlayerEntityMixin extends Entity {

	@Shadow
	private float sentYaw;
	@Shadow
	private float sentPitch;

	public LocalClientPlayerEntityMixin(World world) {
		super(world);
	}

	@Inject(
		method = "mobTick",
		at = @At(value = "FIELD", target = "Lnet/minecraft/entity/living/player/PlayerAbilities;canFly:Z", ordinal = 1,
			opcode = Opcodes.GETFIELD)
	)
	private void illusion$updateSprint(CallbackInfo ci) {
		UpdateSprintEvent event = UpdateSprintEvent.of(isSprinting());
		IllusionClient.BUS.publish(event);
		if (event.sprinting != isSprinting()) setSprinting(event.sprinting);
	}

	@ModifyExpressionValue(
		method = "mobTick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/living/player/LocalClientPlayerEntity;hasStatusEffect(Lnet/minecraft/entity/living/effect/StatusEffect;)Z", ordinal = 0)
	)
	private boolean illusion$nauseaVisual(boolean nauseous) {
		EffectVisualEvent event = EffectVisualEvent.of(StatusEffect.NAUSEA, nauseous);
		IllusionClient.BUS.publish(event);
		return event.visible;
	}

	@ModifyExpressionValue(
		method = "sendMovementToServer",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/entity/living/player/LocalClientPlayerEntity;yaw:F", opcode = Opcodes.GETFIELD)
	)
	private float illusion$serverYaw(float yaw) {
		return IllusionClient.ROTATION.yaw(yaw);
	}

	@ModifyExpressionValue(
		method = "sendMovementToServer",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/entity/living/player/LocalClientPlayerEntity;pitch:F", opcode = Opcodes.GETFIELD)
	)
	private float illusion$serverPitch(float pitch) {
		return IllusionClient.ROTATION.pitch(pitch);
	}

	@Inject(method = "sendMovementToServer", at = @At("RETURN"))
	private void illusion$rotationSent(CallbackInfo ci) {
		IllusionClient.ROTATION.sent(sentYaw, sentPitch);
	}
}
