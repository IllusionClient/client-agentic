package org.codeberg.illusionclient.feature.module.combat;

import org.codeberg.illusionclient.event.api.Listen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import net.minecraft.util.math.Box;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.network.PacketReceiveEvent;
import org.codeberg.illusionclient.event.player.PlayerTickEvent;
import org.codeberg.illusionclient.event.render.Render3DEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.ColorPicker;
import org.codeberg.illusionclient.feature.config.api.RangeSlider;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Section;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.api.ObsHidden;
import org.codeberg.illusionclient.util.CombatUtils;
import org.codeberg.illusionclient.util.GhostPlayer;
import org.codeberg.illusionclient.util.lag.LagManager;
import org.codeberg.illusionclient.util.render.EspRenderer;

import java.awt.Color;

@ModuleInfo(name = "Backtrack", category = Category.COMBAT)
@ObsHidden(value = ObsHidden.Level.PARTIAL, unless = "Show Ghost")
@Description("Lags players leaving your range for extra hits.")
public class Backtrack extends Module {
	private static final int GHOST_LINGER_TICKS = 4;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final LagManager.Lag lag = IllusionClient.LAG.create();
	private final EspRenderer<Void> renderer = new EspRenderer<>();

	@Description("Only lags players within this distance.")
	@RangeSlider(value = "Target Distance", min = 0.0F, max = 8.0F)
	public float[] targetDistance = {2.0F, 3.2F};

	@Description("The longest the target is held back.")
	@Slider(value = "Maximum Delay (ms)", min = 0.0F, max = 500.0F)
	public float maximumDelay = 50.0F;

	@Description("Only lags players hit within this time. 500 is unlimited.")
	@Slider(value = "Maximum Hurt Time (ms)", min = 0.0F, max = 500.0F)
	public float maximumHurtTime = 500.0F;

	@Description("Cooldown before lagging again.")
	@Slider(value = "Cooldown (s)", min = 0.0F, max = 5.0F)
	public float cooldown = 0.4F;

	@Description("Stops lagging as soon as you take knockback.")
	@Check("Disable On Hit")
	public boolean disableOnHit = false;

	@Section("Real Position Indicator")
	@Description("Shows the target's real server position.")
	@Check("Show Ghost")
	public boolean showGhost = true;

	@Description("How visible the ghost is.")
	@Requires("showGhost")
	@Slider(value = "Ghost Opacity", min = 0.0F, max = 100.0F)
	public float ghostOpacity = 50.0F;

	@Description("Draws a hitbox at the target's real server position. Stays hidden from OBS while OBS Proof is on.")
	@Check("Show ESP")
	public boolean showEsp = false;

	@Description("The color of the hitbox.")
	@Requires("showEsp")
	@ColorPicker("Color")
	public Color color = new Color(0xFF5555);

	@Description("How thick the outline is.")
	@Requires("showEsp")
	@Slider(value = "Line Width", min = 1.0F, max = 5.0F)
	public float lineWidth = 2.0F;

	@Description("Fills the hitbox instead of only drawing its outline.")
	@Requires("showEsp")
	@Check("Filled")
	public boolean filled = false;

	private PlayerEntity target;
	private GhostPlayer ghost;
	private boolean lagging;
	private long readyAt;
	private int lingerTicks;

	@Override
	public String details() {
		return number(maximumDelay) + " ms";
	}

	@Override
	public void onDisable() {
		target = null;
		stop();
		ghost = null;
	}

	@Listen
	public void onWorldLoad(WorldLoadEvent event) {
		target = null;
		ghost = null;
	}

	@Listen
	public void onPacketReceive(PacketReceiveEvent event) {
		if (lagging && disableOnHit && isKnockback(event.packet())) stop();
		if (ghost != null) ghost.handle(event.packet());
	}

	@Listen
	public void onRender3D(Render3DEvent event) {
		if (ghost == null) return;

		float tickDelta = event.partialTicks();
		if (showGhost) ghost.render(tickDelta, ghostOpacity / 100.0F);
		if (showEsp) renderEsp(tickDelta);
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		if (minecraft.world == null || minecraft.player == null) {
			target = null;
			ghost = null;
			lagging = false;
			lag.release();
			return;
		}

		if (!lagging && System.currentTimeMillis() >= readyAt) {
			target = CombatUtils.best(this::shouldLag, CombatUtils::distanceTo);
			lagging = target != null;
			if (lagging) ghost = new GhostPlayer(target);
		}

		if (lagging && ghost != null && shouldLag(target)) {
			ghost.tick();
			lag.hold((long) maximumDelay);
			return;
		}

		if (lagging) stop();
		if (ghost == null) return;

		ghost.tick();
		if (--lingerTicks <= 0) ghost = null;
	}

	private void renderEsp(float tickDelta) {
		Box box = ghost.getShape().moved(
			(ghost.prevX - ghost.x) * (1.0F - tickDelta),
			(ghost.prevY - ghost.y) * (1.0F - tickDelta),
			(ghost.prevZ - ghost.z) * (1.0F - tickDelta));
		renderer.begin();
		if (filled) renderer.box3D(box, color.getRGB());
		renderer.end3D();
		renderer.outline3D(box, color.getRGB(), lineWidth);
	}

	private void stop() {
		if (lagging) readyAt = secondsFromNow(cooldown);
		lagging = false;
		lingerTicks = GHOST_LINGER_TICKS;
		lag.release();
	}

	private boolean isKnockback(Packet<?> packet) {
		LocalClientPlayerEntity player = minecraft.player;
		return player != null && packet instanceof EntityVelocityS2CPacket velocity && velocity.getId() == player.getNetworkId();
	}

	private boolean shouldLag(PlayerEntity player) {
		if (player == null || !player.isAlive() || !CombatUtils.isTarget(player)) return false;
		if (player.damagedTimer * MS_PER_TICK > maximumHurtTime) return false;

		double distance = CombatUtils.distanceTo(player);
		if (distance < targetDistance[0] || distance > targetDistance[1]) return false;
		return serverDistanceTo(player) > distance;
	}

	private double serverDistanceTo(PlayerEntity player) {
		Entity server = player == target && ghost != null ? ghost : player;
		return CombatUtils.distanceTo(player.getShape().moved(
			server.lastKnownX / GhostPlayer.POSITION_SCALE - player.x,
			server.lastKnownY / GhostPlayer.POSITION_SCALE - player.y,
			server.lastKnownZ / GhostPlayer.POSITION_SCALE - player.z));
	}
}
