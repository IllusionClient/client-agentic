package org.codeberg.illusionclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.Entity;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.util.PlayerUtils;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public class EntityMixin {
	@ModifyExpressionValue(method = "updateVelocity", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;yaw:F", opcode = Opcodes.GETFIELD))
	private float illusion$strafeYaw(float yaw) {
		return PlayerUtils.isLocalPlayer(this) ? IllusionClient.ROTATION.moveYaw(yaw) : yaw;
	}
}
