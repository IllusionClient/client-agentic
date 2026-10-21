package org.codeberg.illusionclient.mixin;

import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("jumpingCooldown")
	int illusion$jumpingCooldown();

	@Invoker("getArmSwingDuration")
	int illusion$getArmSwingDuration();
}
