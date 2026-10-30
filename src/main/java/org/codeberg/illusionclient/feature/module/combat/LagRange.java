package org.codeberg.illusionclient.feature.module.combat;

import org.codeberg.illusionclient.event.api.Listen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMovementActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.util.math.Box;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.network.PacketSendEvent;
import org.codeberg.illusionclient.event.player.PlayerTickEvent;
import org.codeberg.illusionclient.event.render.Render3DEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.ColorPicker;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Section;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.api.ObsHidden;
import org.codeberg.illusionclient.util.CombatUtils;
import org.codeberg.illusionclient.util.lag.LagManager;
import org.codeberg.illusionclient.util.render.EspRenderer;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.Deque;

@ModuleInfo(name = "Lag Range", category = Category.COMBAT)
@ObsHidden(value = ObsHidden.Level.PARTIAL, unless = "Real Position Indicator")
@Description("Lags you to stay out of your opponent's range for easy first hits.")
public class LagRange extends Module {
	private static final double TARGET_RANGE = 8.0D;
	private static final int USE_ITEM_FACE = 255;
	private static final long POTION_PAUSE_MS = 250L;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final LagManager.Lag lag = IllusionClient.LAG.createOutbound();
	private final EspRenderer<Void> renderer = new EspRenderer<>();
	private final Deque<Sent> sent = new ArrayDeque<>();

	@Section("General")
	@Description("The longest your packets are held. Longer teleports you farther for others, but some anti-cheats cancel hits or set you back.")
	@Slider(value = "Maximum Delay (ms)", min = 50.0F, max = 1000.0F)
	public float maximumDelay = 200.0F;

	@Section("Flush Conditions")
	@Description("Stops lagging while you reset sprint so W-taps and blockhits land on time.")
	@Check("Sprint Reset")
	public boolean flushOnSprintReset = true;

	@Description("Stops lagging briefly after you throw a splash potion so it lands where you are.")
	@Check("Used Splash Potion")
	public boolean flushOnSplashPotion = true;

	@Section("Real Position Indicator")
	@Description("Draws the hitbox other players see while you lag. Only shows in third person.")
	@Check("Real Position Indicator")
	public boolean indicator = true;

	@Description("The color of the hitbox.")
	@Requires("indicator")
	@ColorPicker("Color")
	public Color color = new Color(0xFF5555);

	@Description("How thick the outline is.")
	@Requires("indicator")
	@Slider(value = "Line Width", min = 1.0F, max = 5.0F)
	public float lineWidth = 2.0F;

	@Description("Fills the hitbox instead of only drawing its outline.")
	@Requires("indicator")
	@Check("Filled")
	public boolean filled = false;

	@Section("Conditions")
	@Description("Only lags while holding a weapon.")
	@Check("Weapons Only")
	public boolean weaponsOnly = true;

	private boolean sprintReset;
	private long pausedUntil;

	@Override
	public String details() {
		return number(maximumDelay) + " ms";
	}

	@Override
	public void onDisable() {
		stop();
		sprintReset = false;
	}

	@Listen
	public void onWorldLoad(WorldLoadEvent event) {
		stop();
		sprintReset = false;
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		if (shouldLag()) {
			lag.hold((long) maximumDelay);
			trim();
		} else {
			stop();
		}
	}

	@Listen
	public void onPacketSend(PacketSendEvent event) {
		Packet<?> packet = event.packet();
		if (packet instanceof PlayerMoveC2SPacket move && move.hasPos()) {
			sent.addLast(new Sent(move.getX(), move.getMinY(), move.getZ(), System.currentTimeMillis()));
		} else if (packet instanceof PlayerInteractEntityC2SPacket interact && interact.getAction() == PlayerInteractEntityC2SPacket.Action.ATTACK) {
			stop();
		} else if (packet instanceof PlayerMovementActionC2SPacket action && flushOnSprintReset) {
			if (action.getAction() == PlayerMovementActionC2SPacket.Action.STOP_SPRINTING) {
				sprintReset = true;
				stop();
			} else if (action.getAction() == PlayerMovementActionC2SPacket.Action.START_SPRINTING) {
				sprintReset = false;
			}
		} else if (packet instanceof PlayerUseC2SPacket use && flushOnSplashPotion && isSplashPotion(use)) {
			pausedUntil = System.currentTimeMillis() + POTION_PAUSE_MS;
			stop();
		}
	}

	@Listen
	public void onRender3D(Render3DEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (!indicator || player == null || minecraft.options.perspective == 0) return;

		trim();
		Sent real = sent.peekFirst();
		if (real == null || sent.size() == 1) return;

		Box box = player.getShape().moved(real.x - player.x, real.y - player.y, real.z - player.z);
		renderer.begin();
		if (filled) renderer.box3D(box, color.getRGB());
		renderer.end3D();
		renderer.outline3D(box, color.getRGB(), lineWidth);
	}

	private boolean shouldLag() {
		LocalClientPlayerEntity player = minecraft.player;
		if (minecraft.world == null || player == null || !player.isAlive()) return false;
		if (sprintReset || System.currentTimeMillis() < pausedUntil) return false;
		if (weaponsOnly && !Weapons.isHolding(player)) return false;
		return CombatUtils.best(this::isTarget, CombatUtils::distanceTo) != null;
	}

	private boolean isTarget(PlayerEntity player) {
		return player.isAlive() && CombatUtils.isTarget(player) && CombatUtils.distanceTo(player) <= TARGET_RANGE;
	}

	private static boolean isSplashPotion(PlayerUseC2SPacket use) {
		ItemStack stack = use.getItemInHand();
		return use.getFace() == USE_ITEM_FACE && stack != null && stack.getItem() instanceof PotionItem && PotionItem.isSplashPotion(stack.getMetadata());
	}

	private void trim() {
		long now = System.currentTimeMillis();
		while (sent.size() > 1) {
			Sent oldest = sent.removeFirst();
			if (now - sent.peekFirst().time < maximumDelay) {
				sent.addFirst(oldest);
				return;
			}
		}
	}

	private void stop() {
		lag.release();
		while (sent.size() > 1) sent.removeFirst();
	}

	private record Sent(double x, double y, double z, long time) {
	}
}
