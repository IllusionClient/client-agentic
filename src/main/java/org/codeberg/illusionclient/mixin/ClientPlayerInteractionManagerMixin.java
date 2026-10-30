package org.codeberg.illusionclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.ClientPlayerInteractionManager;
import org.codeberg.illusionclient.feature.module.player.FastMine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
	@Shadow
	private int miningCooldown;

	@Unique
	private boolean illusion$mined;

	@ModifyExpressionValue(
		method = "tickBlockMining",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getMiningSpeed(Lnet/minecraft/entity/living/player/PlayerEntity;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)F")
	)
	private float illusion$miningSpeed(float speed) {
		return FastMine.miningSpeed(speed);
	}

	@ModifyExpressionValue(method = "tickBlockMining", at = @At(value = "CONSTANT", args = "intValue=5"))
	private int illusion$breakDelay(int delay) {
		return FastMine.breakDelay(delay);
	}

	@Inject(method = "tickBlockMining", at = @At("HEAD"))
	private void illusion$mining(CallbackInfoReturnable<Boolean> cir) {
		illusion$mined = true;
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void illusion$tick(CallbackInfo ci) {
		if (!illusion$mined && miningCooldown > 0 && FastMine.alwaysDecrease()) miningCooldown--;
		illusion$mined = false;
	}
}
