package org.codeberg.illusionclient.feature.module.combat;

import io.github.nevalackin.radbus.Listen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.AttackEvent;
import org.codeberg.illusionclient.event.PickEvent;
import org.codeberg.illusionclient.event.TimerEvent;
import org.codeberg.illusionclient.event.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.mixin.RemoteClientPlayerEntityAccessor;
import org.codeberg.illusionclient.util.CombatUtils;
import org.codeberg.illusionclient.util.RotationUtils;
import org.codeberg.illusionclient.util.SimulatedPlayer;
import org.codeberg.illusionclient.util.lag.LagManager;

@ModuleInfo(name = "Timer Range", category = Category.COMBAT)
@Description("Skips and pauses ticks to land the first hit in a trade.")
public class TimerRange extends Module {
	private static final double REACH = 3.0D;
	private static final double TARGET_RANGE = 15.0D;
	private static final double TARGET_FOV = 70.0D;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final LagManager.Lag lag = IllusionClient.LAG.createOutbound();

	@Description("Regular skips then pauses, Legit pauses then skips like a lag spike.")
	@Combo(value = "Mode", options = {"Regular", "Legit"})
	public String mode = "Regular";

	@Description("Only skips if you'd still be at least this far from the target.")
	@Slider(value = "Minimum Distance", min = 2.0F, max = 2.9F)
	public float minDistance = 2.7F;

	@Description("Most time to skip. Longer reaches farther but looks laggier.")
	@Requires(value = "mode", is = "Regular")
	@Slider(value = "Duration (ms)", min = 50.0F, max = 500.0F)
	public float duration = 150.0F;

	@Description("Most time to skip. Longer reaches farther but looks laggier.")
	@Requires(value = "mode", is = "Legit")
	@Slider(value = "Duration (ms)", min = 50.0F, max = 200.0F)
	public float legitDuration = 100.0F;

	@Description("Cooldown before skipping again.")
	@Slider(value = "Cooldown (s)", min = 0.1F, max = 5.0F)
	public float cooldown = 2.0F;

	@Description("Blocks with your sword while frozen to take less damage.")
	@Requires(value = "mode", is = "Regular")
	@Check("Block While Frozen")
	public boolean blockWhileFrozen = false;

	@Description("Only activates while the attack key is held.")
	@Check("Left Mouse Pressed")
	public boolean leftMousePressed = true;

	@Description("Only activates while holding a weapon.")
	@Check("Weapons Only")
	public boolean weaponsOnly = true;

	@Description("Pauses while you use an item.")
	@Check("Not Using Item")
	public boolean notUsingItem = true;

	private PlayerEntity target;
	private boolean eligible;
	private int remaining;
	private int pending;
	private int debt;
	private int frozenTicks;
	private boolean hit;
	private boolean attacking;
	private float heldPartialTick;
	private long readyAt;

	@Override
	public String details() {
		return mode + " " + number(legit() ? legitDuration : duration) + " ms";
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
	public void onAttack(AttackEvent event) {
		if (!attacking) return;
		event.resetCooldown = true;
		attacking = false;
	}

	@Listen
	public void onTimer(TimerEvent event) {
		if (eligible && frozenTicks == 0 && remaining == 0 && System.currentTimeMillis() >= readyAt) trigger(event);
		if (frozenTicks > 0) freeze(event);
		heldPartialTick = event.partialTick;
	}

	@Listen
	public void onPick(PickEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		attacking = false;
		if (minecraft.world == null || player == null) {
			reset();
			return;
		}

		if (remaining > 0) {
			burst(player);
			return;
		}

		target = findTarget(player);
		eligible = target != null && conditionsMet(player) && player.input.movementForward > 0.0F;
	}

	private void trigger(TimerEvent event) {
		if (target == null || minecraft.player == null) return;
		int ticks = reachTicks(minecraft.player, durationTicks());
		if (ticks <= event.ticks) return;

		hit = false;
		if (legit()) {
			pending = ticks;
			frozenTicks = ticks;
			lag.hold(Long.MAX_VALUE);
		} else {
			debt = ticks - event.ticks;
			remaining = ticks;
			event.ticks = ticks;
		}
	}

	private void freeze(TimerEvent event) {
		int swallowed = Math.min(event.ticks, frozenTicks);
		frozenTicks -= swallowed;
		event.ticks -= swallowed;
		if (frozenTicks > 0 || event.ticks == 0) event.partialTick = heldPartialTick;
		if (frozenTicks > 0) return;

		readyAt = secondsFromNow(cooldown);
		if (pending == 0) return;

		lag.release();
		remaining = pending;
		event.ticks += pending;
		pending = 0;
	}

	private void burst(LocalClientPlayerEntity player) {
		remaining--;
		HitResult crosshair = minecraft.crosshairTarget;
		if (!hit && player.getItemInUse() == null && crosshair != null && crosshair.entity == target) {
			attacking = true;
			KeyBinding.click(minecraft.options.attackKey.getKeyCode());
			hit = true;
		}
		if (remaining > 0 || legit()) return;

		frozenTicks = debt;
		ItemStack stack = player.getItemInHand();
		if (blockWhileFrozen && stack != null && stack.getItem() instanceof SwordItem && player.getItemInUse() == null) {
			KeyBinding.click(minecraft.options.useKey.getKeyCode());
		}
	}

	private int reachTicks(LocalClientPlayerEntity player, int limit) {
		Vec3d look = player.getRotationVector(1.0F);
		SimulatedPlayer self = SimulatedPlayer.of(player);

		double targetX = target.x;
		double targetY = target.y;
		double targetZ = target.z;
		double destX = targetX;
		double destY = targetY;
		double destZ = targetZ;
		int steps = 0;
		if (target instanceof RemoteClientPlayerEntity remote) {
			RemoteClientPlayerEntityAccessor lerp = (RemoteClientPlayerEntityAccessor) remote;
			destX = lerp.illusion$lerpX();
			destY = lerp.illusion$lerpY();
			destZ = lerp.illusion$lerpZ();
			steps = lerp.illusion$lerpSteps();
		}

		for (int tick = 1; tick <= limit; tick++) {
			Vec3d eye = self.eyePosition();
			Box box = new Box(targetX - 0.4D, targetY - 0.1D, targetZ - 0.4D, targetX + 0.4D, targetY + 1.9D, targetZ + 0.4D);
			if (box.clip(eye, eye.add(look.x * REACH, look.y * REACH, look.z * REACH)) != null) {
				double distance = CombatUtils.distanceTo(eye, box);
				if (distance > 0.0D) return distance < minDistance ? 0 : tick;
			}

			self.tick();

			if (legit()) {
				double gapX = self.x() - destX;
				double gapZ = self.z() - destZ;
				double gap = Math.hypot(gapX, gapZ);
				if (gap > 0.0D) {
					double pursuit = Math.hypot(self.velocityX, self.velocityZ) / gap;
					destX += gapX * pursuit;
					destZ += gapZ * pursuit;
				}
			}

			if (steps > 0) {
				targetX += (destX - targetX) / steps;
				targetY += (destY - targetY) / steps;
				targetZ += (destZ - targetZ) / steps;
				steps--;
			}
		}
		return 0;
	}

	private PlayerEntity findTarget(LocalClientPlayerEntity player) {
		HitResult crosshair = minecraft.crosshairTarget;
		if (crosshair != null && crosshair.entity instanceof PlayerEntity hovered && valid(hovered)) return hovered;

		return CombatUtils.best(other -> valid(other)
			&& CombatUtils.distanceTo(other) <= TARGET_RANGE
			&& RotationUtils.angleTo(other, player.yaw, player.pitch) <= TARGET_FOV, CombatUtils::distanceTo);
	}

	private static boolean valid(PlayerEntity player) {
		return player.isAlive() && !player.isSleeping() && CombatUtils.isTarget(player);
	}

	private boolean conditionsMet(LocalClientPlayerEntity player) {
		if (minecraft.screen != null || !player.isAlive() || !player.onGround || player.velocityY > 0.0D) return false;
		if (target.damagedTimer > 1 || minecraft.options.jumpKey.isPressed() || !player.canSee(target)) return false;
		if (leftMousePressed && !IllusionClient.INPUT.held(minecraft.options.attackKey)) return false;
		if (weaponsOnly && !Weapons.isHolding(player)) return false;
		return !notUsingItem || player.getItemInUse() == null;
	}

	private boolean legit() {
		return mode.equals("Legit");
	}

	private int durationTicks() {
		return Math.max(1, ticks(legit() ? legitDuration : duration));
	}

	private void reset() {
		target = null;
		eligible = false;
		remaining = pending = frozenTicks = 0;
		lag.release();
	}
}
