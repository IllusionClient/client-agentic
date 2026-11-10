package org.codeberg.illusionclient.feature.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.FishingBobberEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.input.InputEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.RangeSlider;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.api.RotatingModule;
import org.codeberg.illusionclient.feature.module.client.Weapons;
import org.codeberg.illusionclient.util.CombatUtils;
import org.codeberg.illusionclient.util.RotationUtils;
import org.codeberg.illusionclient.util.SimulatedPlayer;

@ModuleInfo(name = "Auto Rod", category = Category.COMBAT)
@Description("Throws your fishing rod at opponents you can't reach with a sword hit, timed to land as they can take damage again. Swaps back as soon as the rod is going to miss.")
public class AutoRod extends RotatingModule {
	private static final double SPEED = 1.5D;
	private static final double DRAG = 0.92D;
	private static final double GRAVITY = 0.04D;
	private static final double HIT_MARGIN = 0.3D;
	private static final double REACH = 3.0D;
	private static final int MAX_FLIGHT = 20;
	private static final int HURT_TICKS = 10;
	private final Minecraft minecraft = Minecraft.getInstance();

	@Description("Only throws at opponents this far away.")
	@RangeSlider(value = "Range", min = 0.0F, max = 12.0F)
	public float[] range = {0.0F, 8.0F};

	@Description("Only throws at opponents within this many degrees of your crosshair.")
	@Slider(value = "FOV", min = 0.0F, max = 180.0F)
	public float fov = 60.0F;

	@Description("Throws this many ticks before the opponent can take damage again, so the rod still lands if they recover early.")
	@Slider(value = "Hurt Time Lead (ticks)", min = 0.0F, max = 5.0F, step = 1.0F)
	public float hurtTimeLead = 1.0F;

	@Description("Swaps back once the rod hits or is going to miss, or after this long.")
	@Slider(value = "Swap Back Delay (ms)", min = 50.0F, max = 1000.0F)
	public float swapBackDelay = 500.0F;

	@Description("Time between throws. Skipped when the thrown rod is going to miss.")
	@Slider(value = "Cooldown (ms)", min = 0.0F, max = 2000.0F)
	public float cooldown = 600.0F;

	@Description("Only throws while holding a weapon.")
	@Check("Weapons Only")
	public boolean weaponsOnly = true;

	private enum Stage { IDLE, AIMED, THROWN }

	private Stage stage = Stage.IDLE;
	private PlayerEntity target;
	private int previousSlot = -1;
	private int timer;
	private boolean spawned;
	private Vec3d hook;
	private Vec3d hookVelocity;

	@Override
	public void onDisable() {
		finish();
		stopRotating();
	}

	@Listen
	public void onWorldLoad(WorldLoadEvent event) {
		finish();
	}

	@Listen
	public void onInput(InputEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || minecraft.world == null) return;

		switch (stage) {
			case IDLE -> {
				if (timer > 0) timer--;
				else start(player);
			}
			case AIMED -> {
				ItemStack held = player.inventory.getSelectedItem();
				if (held == null || held.getItem() != Items.FISHING_ROD || !target.isAlive()) {
					finish();
					return;
				}
				if (!aimed()) {
					float[] rotation = lead(player, target);
					if (rotation == null || aimTimedOut()) finish();
					else aim(rotation);
					return;
				}
				minecraft.interactionManager.useItem(player, minecraft.world, held);
				cast(player);
				release();
				timer = ticks(swapBackDelay);
				stage = Stage.THROWN;
			}
			case THROWN -> {
				FishingBobberEntity bobber = player.fishingBobber;
				if (target.damagedTimer == HURT_TICKS || --timer <= 0 || bobber != null && bobber.hookedEntity != null || !trackHook(bobber)) {
					finish();
				} else if (!onCourse()) {
					finish();
					timer = 0;
				}
			}
		}
	}

	private void start(LocalClientPlayerEntity player) {
		if (minecraft.screen != null || !player.isAlive() || player.getItemInUse() != null || player.fishingBobber != null) return;
		if (weaponsOnly && !Weapons.isHolding(player)) return;

		int slot = rodSlot(player.inventory);
		if (slot == -1) return;

		PlayerEntity found = CombatUtils.best(other -> other.isAlive() && CombatUtils.isTarget(other)
			&& CombatUtils.distanceTo(other) >= range[0] && CombatUtils.distanceTo(other) <= range[1]
			&& RotationUtils.angleTo(other, player.yaw, player.pitch) <= fov, CombatUtils::distanceTo);
		float[] rotation = found == null ? null : lead(player, found);
		if (rotation == null || !IllusionClient.MODULES.claimSlot(this)) return;

		aim(rotation);
		target = found;
		previousSlot = player.inventory.selectedSlot;
		player.inventory.selectedSlot = slot;
		stage = Stage.AIMED;
	}

	private float[] lead(LocalClientPlayerEntity player, PlayerEntity found) {
		Vec3d eye = player.getEyePosition(1.0F);
		double vx = found.x - found.prevX;
		double vz = found.z - found.prevZ;
		double travelled = 0.0D;
		double speed = SPEED;
		double drop = 0.0D;
		double fall = 0.0D;
		int ticks = 0;
		while (travelled < Math.hypot(found.x + vx * ticks - eye.x, found.z + vz * ticks - eye.z)) {
			if (++ticks > MAX_FLIGHT) return null;
			travelled += speed;
			drop += fall;
			speed *= DRAG;
			fall = fall * DRAG - GRAVITY;
		}
		if (IllusionClient.COMBAT.ticksUntilDamageable(found) > ticks + hurtTimeLead) return null;
		if (meleeLands(player, found, ticks)) return null;

		Box box = found.getShape();
		Vec3d point = new Vec3d(
			(box.minX + box.maxX) / 2.0D + vx * ticks,
			(box.minY + box.maxY) / 2.0D - drop,
			(box.minZ + box.maxZ) / 2.0D + vz * ticks);
		return RotationUtils.rotationTo(eye, point);
	}

	private static boolean meleeLands(LocalClientPlayerEntity player, PlayerEntity found, int ticks) {
		int damageable = IllusionClient.COMBAT.ticksUntilDamageable(found);
		SimulatedPlayer self = SimulatedPlayer.of(player);
		Box box = found.getShape();
		double vx = found.x - found.prevX;
		double vz = found.z - found.prevZ;
		for (int tick = 0; tick <= ticks; tick++) {
			if (tick >= damageable && CombatUtils.distanceTo(self.eyePosition(), box) <= REACH) return true;
			self.tick();
			box = box.moved(vx, 0.0D, vz);
		}
		return false;
	}

	private void cast(LocalClientPlayerEntity player) {
		float yaw = aimYaw();
		Vec3d look = RotationUtils.look(yaw, aimPitch());
		double radians = Math.toRadians(yaw);
		hook = player.getEyePosition(1.0F).add(-Math.cos(radians) * 0.16D, -0.1D, -Math.sin(radians) * 0.16D);
		hookVelocity = new Vec3d(look.x * SPEED, look.y * SPEED, look.z * SPEED);
		spawned = false;
	}

	private boolean trackHook(FishingBobberEntity bobber) {
		if (bobber != null) {
			spawned = true;
			hook = new Vec3d(bobber.x, bobber.y, bobber.z);
			hookVelocity = new Vec3d(bobber.velocityX, bobber.velocityY, bobber.velocityZ);
			return true;
		}
		if (spawned) return false;

		hook = hook.add(hookVelocity);
		hookVelocity = new Vec3d(hookVelocity.x * DRAG, hookVelocity.y * DRAG - GRAVITY, hookVelocity.z * DRAG);
		return true;
	}

	private boolean onCourse() {
		Box box = target.getShape().grown(HIT_MARGIN, HIT_MARGIN, HIT_MARGIN);
		double tx = target.x - target.prevX;
		double tz = target.z - target.prevZ;
		Vec3d pos = hook;
		double vx = hookVelocity.x;
		double vy = hookVelocity.y;
		double vz = hookVelocity.z;
		if (box.contains(pos)) return true;
		for (int tick = 0; tick < MAX_FLIGHT; tick++) {
			Vec3d next = pos.add(vx, vy, vz);
			HitResult wall = minecraft.world.rayTrace(pos, next);
			if (box.clip(pos, wall == null ? next : wall.facePos) != null) return true;
			if (wall != null) return false;
			pos = next;
			box = box.moved(tx, 0.0D, tz);
			vx *= DRAG;
			vy = vy * DRAG - GRAVITY;
			vz *= DRAG;
		}
		return false;
	}

	private void finish() {
		if (previousSlot != -1 && minecraft.player != null) minecraft.player.inventory.selectedSlot = previousSlot;
		if (stage != Stage.IDLE) timer = ticks(cooldown);
		IllusionClient.MODULES.releaseSlot(this);
		release();
		previousSlot = -1;
		target = null;
		hook = null;
		hookVelocity = null;
		stage = Stage.IDLE;
	}

	private static int rodSlot(PlayerInventory inventory) {
		for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
			ItemStack stack = inventory.items[slot];
			if (stack != null && stack.getItem() == Items.FISHING_ROD) return slot;
		}
		return -1;
	}
}
