package org.codeberg.illusionclient.feature.module.combat;

import org.codeberg.illusionclient.event.api.Listen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.TimerEvent;
import org.codeberg.illusionclient.event.network.PacketSendEvent;
import org.codeberg.illusionclient.event.player.AttackEvent;
import org.codeberg.illusionclient.event.player.PickEvent;
import org.codeberg.illusionclient.event.player.PlayerTickEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Section;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.client.AttackDelay;
import org.codeberg.illusionclient.feature.module.client.Weapons;
import org.codeberg.illusionclient.util.CombatUtils;
import org.codeberg.illusionclient.util.SimulatedPlayer;
import org.codeberg.illusionclient.util.TickShifter;
import org.codeberg.illusionclient.util.lag.LagManager;

import java.util.Random;

@ModuleInfo(name = "Criticals", category = Category.COMBAT)
@Description("Makes more of your hits land as critical hits.")
public class Criticals extends Module {
	private final Minecraft minecraft = Minecraft.getInstance();
	private final Random random = new Random();
	private final TickShifter shifter = new TickShifter();
	private final LagManager.Lag fallLag = IllusionClient.LAG.createOutbound();
	private final LagManager.Lag freezeLag = IllusionClient.LAG.createOutbound();

	@Section("General")
	@Description("Lag holds your packets while you fall so the server sees you falling for longer, Tick skips ahead to the moment you start falling.")
	@Combo(value = "Mode", options = {"Lag", "Tick"})
	public String mode = "Lag";

	@Description("The longest your packets are held back. Higher values land more critical hits but look laggier.")
	@Requires(value = "mode", is = "Lag")
	@Slider(value = "Maximum Delay (ms)", min = 50.0F, max = 500.0F)
	public float maximumDelay = 300.0F;

	@Description("Most time to skip. Longer reaches the fall from lower in your jump but looks laggier.")
	@Requires(value = "mode", is = "Tick")
	@Slider(value = "Maximum Duration (ms)", min = 50.0F, max = 500.0F)
	public float maximumDuration = 300.0F;

	@Description("Chance to activate. Lower is less obvious but lands fewer critical hits.")
	@Slider(value = "Chance (%)", min = 0.0F, max = 100.0F)
	public float chance = 100.0F;

	@Section("Conditions")
	@Description("Only activates while holding a weapon.")
	@Check("Weapons Only")
	public boolean weaponsOnly = true;

	@Description("Only activates while the attack key is held.")
	@Check("Mouse Pressed")
	public boolean mousePressed = true;

	private boolean lucky;
	private float delay;
	private long attackedAt;
	private PlayerEntity target;
	private boolean eligible;
	private boolean attacking;
	private boolean hit;
	private int pending;
	private long readyAt;

	@Override
	public String details() {
		return mode;
	}

	@Override
	public void onDisable() {
		reset();
	}

	@Listen
	public void onWorldLoad(WorldLoadEvent event) {
		reset();
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (minecraft.world == null || player == null) return;
		if (player.onGround) lucky = random.nextFloat() * 100.0F < chance;

		if (mode.equals("Lag") && falling(player) && System.currentTimeMillis() - attackedAt < AttackDelay.ms() && active(player)) {
			delay = Math.min(delay + maximumDelay / 5.0F, maximumDelay);
			fallLag.hold((long) delay);
		} else {
			delay = 0.0F;
			fallLag.release();
		}
	}

	@Listen
	public void onPacketSend(PacketSendEvent event) {
		if (event.packet() instanceof PlayerInteractEntityC2SPacket interact && interact.getAction() == PlayerInteractEntityC2SPacket.Action.ATTACK) {
			attackedAt = System.currentTimeMillis();
		}
	}

	@Listen
	public void onTimer(TimerEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (minecraft.world != null && player != null) skip(event, player);
	}

	@Listen
	public void onAttack(AttackEvent event) {
		if (!attacking) return;
		event.resetCooldown = true;
		attacking = false;
	}

	@Listen
	public void onPick(PickEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		attacking = false;
		if (minecraft.world == null || player == null) return;

		HitResult crosshair = minecraft.crosshairTarget;
		if (shifter.bursting()) {
			shifter.step();
			if (!hit && falling(player) && player.getItemInUse() == null && crosshair != null && crosshair.entity == target) {
				attacking = true;
				KeyBinding.click(minecraft.options.attackKey.getKeyCode());
				hit = true;
			}
			return;
		}

		target = crosshair != null && crosshair.entity instanceof PlayerEntity hovered && valid(hovered) ? hovered : null;
		eligible = mode.equals("Tick") && target != null && target.damagedTimer <= 1 && minecraft.screen == null
			&& !player.onGround && player.fallDistance == 0.0F && active(player);
	}

	private void skip(TimerEvent event, LocalClientPlayerEntity player) {
		if (eligible && shifter.available() && System.currentTimeMillis() >= readyAt) {
			int ticks = ticksUntilFalling(player);
			if (ticks > event.ticks) {
				hit = false;
				pending = ticks;
				shifter.freeze(ticks);
				freezeLag.hold(Long.MAX_VALUE);
			}
		}
		if (shifter.onTimer(event)) thaw(event);
	}

	private void thaw(TimerEvent event) {
		readyAt = System.currentTimeMillis() + (long) AttackDelay.ms();
		freezeLag.release();
		shifter.skip(event, event.ticks + pending);
		pending = 0;
	}

	private int ticksUntilFalling(LocalClientPlayerEntity player) {
		SimulatedPlayer self = SimulatedPlayer.of(player);
		int limit = ticks(maximumDuration);
		for (int tick = 1; tick < limit; tick++) {
			double y = self.y();
			self.tick();
			if (self.onGround) return 0;
			if (self.y() < y) return tick + 1;
		}
		return 0;
	}

	private boolean active(LocalClientPlayerEntity player) {
		if (!lucky) return false;
		if (mousePressed && !IllusionClient.INPUT.held(minecraft.options.attackKey)) return false;
		return !weaponsOnly || Weapons.isHolding(player);
	}

	private static boolean falling(LocalClientPlayerEntity player) {
		return !player.onGround && player.fallDistance > 0.0F;
	}

	private static boolean valid(PlayerEntity player) {
		return player.isAlive() && !player.isSleeping() && CombatUtils.isTarget(player);
	}

	private void reset() {
		delay = 0.0F;
		pending = 0;
		fallLag.release();
		freezeLag.release();
		target = null;
		eligible = false;
		shifter.reset();
	}
}
