package org.codeberg.illusionclient.feature.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.player.AttackEvent;
import org.codeberg.illusionclient.event.player.PlayerTickEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.util.CombatUtils;
import org.codeberg.illusionclient.util.KeyboardUtils;
import org.codeberg.illusionclient.util.MouseUtils;
import org.codeberg.illusionclient.util.lag.LagManager;

import java.util.Random;

@ModuleInfo(name = "Auto Block", category = Category.COMBAT)
@Description("Blocks with your sword right before you can take damage again.")
public class AutoBlock extends Module {
	private final Minecraft minecraft = Minecraft.getInstance();
	private final LagManager.Lag lag = IllusionClient.LAG.createOutbound();
	private final Random random = new Random();

	@Description("Starts blocking when a player is this close. Above 3.5 you may get comboed.")
	@Slider(value = "Range", min = 0.0F, max = 8.0F)
	public float range = 3.0F;

	@Description("How early blocking starts before you can be hit again. Try your ping + 100.")
	@Slider(value = "Maximum Hurt Time (ms)", min = 50.0F, max = 500.0F)
	public float maximumHurtTime = 200.0F;

	@Description("How long the sword stays blocked. Keep it at least 50 below Maximum Hurt Time.")
	@Slider(value = "Maximum Hold Duration (ms)", min = 50.0F, max = 450.0F)
	public float maximumHoldDuration = 100.0F;

	@Description("Keeps your sword looking blocked, with block hits if an old animations mod adds them. Only you see it, other players still see you unblock.")
	@Check("Force Block Animation")
	public boolean forceBlockAnimation = false;

	@Description("Only forces the animation while a player is within Range.")
	@Requires("forceBlockAnimation")
	@Check("Only With Target")
	public boolean animationOnlyWithTarget = true;

	@Description("Chance to hold back your unblock so the server thinks you blocked for longer.")
	@Slider(value = "Lag Chance (%)", min = 0.0F, max = 100.0F)
	public float lagChance = 0.0F;

	@Description("How long the unblock is held back.")
	@Requires("lagChance")
	@Slider(value = "Lag Maximum Duration (ms)", min = 50.0F, max = 500.0F)
	public float lagMaximumDuration = 150.0F;

	@Description("Ends the lag as soon as you attack so hits are never delayed.")
	@Requires("lagChance")
	@Check("Prevent Delaying Attacks")
	public boolean preventDelayingAttacks = true;

	@Description("Blocks again as soon as the lag ends.")
	@Requires("lagChance")
	@Check("Block Again Immediately")
	public boolean blockAgainImmediately = false;

	@Description("Only blocks while you hold left click.")
	@Check("Left Mouse Pressed")
	public boolean leftMousePressed = false;

	@Description("Only blocks while you hold right click.")
	@Check("Right Mouse Pressed")
	public boolean rightMousePressed = false;

	@Description("Only blocks after you took damage.")
	@Check("Damaged")
	public boolean damaged = false;

	private boolean blocking;
	private boolean useHeld;
	private long blockedAt;
	private long lagEndsAt;

	@Override
	public String details() {
		return number(range);
	}

	@Override
	public void onDisable() {
		reset();
	}

	public boolean forcesBlockAnimation() {
		return isEnabled() && forceBlockAnimation && holdingSword(minecraft.player) && (!animationOnlyWithTarget || targetInRange());
	}

	@Listen
	public void onWorldLoad(WorldLoadEvent event) {
		reset();
	}

	@Listen
	public void onAttack(AttackEvent event) {
		if (lagEndsAt != 0L && preventDelayingAttacks) lag.release();
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || minecraft.world == null || minecraft.screen != null) {
			reset();
			return;
		}

		long now = System.currentTimeMillis();
		boolean held = IllusionClient.INPUT.held(minecraft.options.useKey);
		if (held != useHeld) {
			useHeld = held;
			restore();
			blocking = held && rightMousePressed;
			if (blocking) blockedAt = now;
		}

		if (lagEndsAt != 0L && (now >= lagEndsAt || preventDelayingAttacks && IllusionClient.INPUT.held(minecraft.options.attackKey))) {
			lag.release();
			lagEndsAt = 0L;
			if (blockAgainImmediately && shouldBlock(player)) block(true, now);
		}

		if (blocking) {
			if (!shouldBlock(player) || now - blockedAt >= maximumHoldDuration) unblock(now);
			return;
		}
		if (lagEndsAt == 0L && now - blockedAt >= maximumHurtTime && shouldBlock(player)) block(true, now);
	}

	private boolean shouldBlock(LocalClientPlayerEntity player) {
		if (!player.isAlive() || (rightMousePressed ? !useHeld : useHeld)) return false;
		if (!holdingSword(player)) return false;
		if (leftMousePressed && !IllusionClient.INPUT.held(minecraft.options.attackKey)) return false;
		if (damaged && player.damagedTimer == 0) return false;

		float untilHittable = Math.max(0, player.invulnerableTimer - player.invulnerableTicks / 2) * MS_PER_TICK;
		if (untilHittable > maximumHurtTime) return false;
		return targetInRange();
	}

	private boolean holdingSword(LocalClientPlayerEntity player) {
		ItemStack stack = player.getItemInHand();
		return stack != null && stack.getItem() instanceof SwordItem;
	}

	private boolean targetInRange() {
		return CombatUtils.best(other -> other.isAlive() && CombatUtils.isTarget(other) && CombatUtils.distanceTo(other) <= range,
			CombatUtils::distanceTo) != null;
	}

	private void unblock(long now) {
		block(false, now);
		if (random.nextFloat() * 100.0F < lagChance) {
			lag.hold((long) lagMaximumDuration);
			lagEndsAt = now + (long) lagMaximumDuration;
		}
	}

	private void block(boolean pressed, long now) {
		KeyBinding key = minecraft.options.useKey;
		int code = key.getKeyCode();
		if (code < 0) MouseUtils.toggle(code + 100);
		else KeyboardUtils.set(key, pressed);
		blocking = pressed;
		if (pressed) blockedAt = now;
	}

	private void restore() {
		KeyBinding key = minecraft.options.useKey;
		int code = key.getKeyCode();
		if (code < 0) MouseUtils.resync(code + 100);
		else KeyboardUtils.set(key, IllusionClient.INPUT.held(key));
	}

	private void reset() {
		if (blocking != useHeld) restore();
		blocking = false;
		useHeld = false;
		lag.release();
		lagEndsAt = 0L;
		blockedAt = 0L;
	}
}
