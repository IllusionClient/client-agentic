package org.codeberg.illusionclient.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketUtils;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.KeepAliveS2CPacket;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.network.PacketSendEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
	@Inject(method = "handleKeepAlive", at = @At("HEAD"))
	private void illusion$keepAliveOnClientThread(KeepAliveS2CPacket packet, CallbackInfo ci) {
		if (IllusionClient.LAG.holdsKeepAlives()) {
			PacketUtils.ensureOnSameThread(packet, (ClientPlayPacketHandler) this, Minecraft.getInstance());
		}
	}

	@WrapWithCondition(
		method = "sendPacket",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/packet/Packet;)V")
	)
	private boolean illusion$packetSend(Connection connection, Packet<?> packet) {
		PacketSendEvent event = new PacketSendEvent(packet, connection);
		IllusionClient.BUS.publish(event);
		return !event.cancelled;
	}
}
