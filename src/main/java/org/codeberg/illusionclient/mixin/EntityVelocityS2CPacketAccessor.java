package org.codeberg.illusionclient.mixin;

import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityVelocityS2CPacket.class)
public interface EntityVelocityS2CPacketAccessor {
	@Accessor("velocityX")
	void illusion$setVelocityX(int velocityX);

	@Accessor("velocityY")
	void illusion$setVelocityY(int velocityY);

	@Accessor("velocityZ")
	void illusion$setVelocityZ(int velocityZ);
}
