package org.codeberg.illusionclient.feature.module.player;

import io.github.nevalackin.radbus.Listen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.PacketSendEvent;
import org.codeberg.illusionclient.event.PlayerTickEvent;
import org.codeberg.illusionclient.event.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.util.lag.LagManager;

@ModuleInfo(name = "Blink", category = Category.PLAYER)
@Description("Holds back packets so you look like you teleport to other players.")
public class Blink extends Module {
	private static final int USE_ITEM_FACE = 255;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final LagManager.Lag outboundLag = IllusionClient.LAG.createOutbound();
	private final LagManager.Lag inboundLag = IllusionClient.LAG.create();

	@Description("Outbound makes you teleport for others, Inbound freezes everything on your screen.")
	@Combo(value = "Direction", options = {"Outbound", "Inbound", "Both"})
	public String direction = "Outbound";

	@Description("Turns Blink off after this long. You can still turn it off sooner.")
	@Slider(value = "Maximum Duration (s)", min = 0.1F, max = 20.0F)
	public float maximumDuration = 5.0F;

	@Description("Turns off when you attack.")
	@Check("Disable On Attack")
	public boolean disableOnAttack = true;

	@Description("Turns off when you right click a block.")
	@Check("Disable On Block Interact")
	public boolean disableOnBlockInteract = false;

	@Description("Turns off when you start breaking a block.")
	@Check("Disable On Block Dig")
	public boolean disableOnBlockDig = false;

	private long endsAt;

	@Override
	public String details() {
		return direction;
	}

	@Override
	public void onEnable() {
		endsAt = secondsFromNow(maximumDuration);
		if (!direction.equals("Inbound")) outboundLag.hold(Long.MAX_VALUE);
		if (!direction.equals("Outbound")) inboundLag.hold(Long.MAX_VALUE);
	}

	@Override
	public void onDisable() {
		outboundLag.release();
		inboundLag.release();
	}

	@Listen
	public void onWorldLoad(WorldLoadEvent event) {
		setEnabled(false);
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		if (minecraft.world == null || minecraft.player == null || System.currentTimeMillis() >= endsAt) setEnabled(false);
	}

	@Listen
	public void onPacketSend(PacketSendEvent event) {
		if (disables(event.packet())) setEnabled(false);
	}

	private boolean disables(Packet<?> packet) {
		if (packet instanceof PlayerInteractEntityC2SPacket interact) {
			return disableOnAttack && interact.getAction() == PlayerInteractEntityC2SPacket.Action.ATTACK;
		}
		if (packet instanceof PlayerUseC2SPacket use) return disableOnBlockInteract && use.getFace() != USE_ITEM_FACE;
		if (packet instanceof PlayerHandActionC2SPacket action) {
			return disableOnBlockDig && action.getAction() == PlayerHandActionC2SPacket.Action.START_DESTROY_BLOCK;
		}
		return false;
	}
}
