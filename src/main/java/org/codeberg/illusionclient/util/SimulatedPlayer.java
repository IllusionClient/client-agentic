package org.codeberg.illusionclient.util;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.codeberg.illusionclient.mixin.EntityAccessor;
import org.codeberg.illusionclient.mixin.LivingEntityAccessor;

// TODO: Flight
// TODO: Riding
// TODO: Water Currents
public final class SimulatedPlayer {
	private static final float SPRINT_MULTIPLIER = 1.3F;
	private static final float SPRINT_FORWARD = 0.8F;

	private final LocalClientPlayerEntity player;
	private final ClientWorld world;
	private final float walkSpeed;
	private final float stepHeight;
	private final float flyingSpeed;
	private final float jumpBoost;
	private final int depthStrider;
	private final boolean canSprint;
	private final boolean sprintKey;
	private float airSpeed;

	public Box shape;
	public double velocityX;
	public double velocityY;
	public double velocityZ;
	public float yaw;
	public float forward;
	public float sideways;
	public boolean jumping;
	public boolean sneaking;
	public boolean sprinting;
	public boolean onGround;
	public boolean collidingHorizontally;
	public boolean collidingVertically;
	public boolean inWater;
	public boolean inCobweb;
	public int jumpingCooldown;
	public int ticks;

	private SimulatedPlayer(LocalClientPlayerEntity player) {
		this.player = player;
		this.world = Minecraft.getInstance().world;
		shape = player.getShape();
		velocityX = player.velocityX;
		velocityY = player.velocityY;
		velocityZ = player.velocityZ;
		yaw = player.yaw;
		forward = player.input.movementForward;
		sideways = player.input.movementSideways;
		jumping = player.input.jumping;
		sneaking = player.input.sneaking;
		sprinting = player.isSprinting();
		onGround = player.onGround;
		collidingHorizontally = player.collidingHorizontally;
		collidingVertically = player.collidingVertically;
		inWater = player.isInWater();
		inCobweb = ((EntityAccessor) player).illusion$inCobweb();
		jumpingCooldown = ((LivingEntityAccessor) player).illusion$jumpingCooldown();

		walkSpeed = sprinting ? player.getSpeed() / SPRINT_MULTIPLIER : player.getSpeed();
		stepHeight = player.stepHeight;
		airSpeed = player.speedInAir;
		flyingSpeed = sprinting ? airSpeed / SPRINT_MULTIPLIER : airSpeed;
		jumpBoost = player.hasStatusEffect(StatusEffect.JUMP_BOOST) ? (player.getEffectInstance(StatusEffect.JUMP_BOOST).getAmplifier() + 1) * 0.1F : 0.0F;
		depthStrider = Math.min(3, EnchantmentHelper.getDepthStriderLevel(player));
		canSprint = (player.getHungerManager().getFoodLevel() > 6.0F || player.abilities.canFly)
			&& !player.hasItemInUse()
			&& !player.hasStatusEffect(StatusEffect.BLINDNESS);
		sprintKey = Minecraft.getInstance().options.sprintKey.isPressed();
	}

	public static SimulatedPlayer of(LocalClientPlayerEntity player) {
		return new SimulatedPlayer(player);
	}

	public double x() {
		return (shape.minX + shape.maxX) / 2.0D;
	}

	public double y() {
		return shape.minY;
	}

	public double z() {
		return (shape.minZ + shape.maxZ) / 2.0D;
	}

	public float eyeHeight() {
		return sneaking ? 1.54F : 1.62F;
	}

	public Vec3d eyePosition() {
		return new Vec3d(x(), y() + eyeHeight(), z());
	}

	public void tick() {
		ticks++;
		inWater = world.containsMaterial(shape.grown(0.0D, -0.4F, 0.0D).contract(0.001D, 0.001D, 0.001D), Material.WATER);

		if (!sprinting && forward >= SPRINT_FORWARD && canSprint && sprintKey) sprinting = true;
		if (sprinting && (forward < SPRINT_FORWARD || collidingHorizontally || !canSprint)) sprinting = false;

		if (jumpingCooldown > 0) jumpingCooldown--;
		if (Math.abs(velocityX) < 0.005D) velocityX = 0.0D;
		if (Math.abs(velocityY) < 0.005D) velocityY = 0.0D;
		if (Math.abs(velocityZ) < 0.005D) velocityZ = 0.0D;

		if (!jumping) {
			jumpingCooldown = 0;
		} else if (inWater || isInLava()) {
			velocityY += 0.04F;
		} else if (onGround && jumpingCooldown == 0) {
			jump();
			jumpingCooldown = 10;
		}

		travel(sideways * 0.98F, forward * 0.98F);
		airSpeed = sprinting ? flyingSpeed + flyingSpeed * 0.3F : flyingSpeed;
	}

	public boolean isInLava() {
		return world.containsMaterial(shape.grown(-0.1F, -0.4F, -0.1F), Material.LAVA);
	}

	public boolean isClimbing() {
		Block block = block(MathHelper.floor(x()), MathHelper.floor(shape.minY), MathHelper.floor(z()));
		return block == Blocks.LADDER || block == Blocks.VINE;
	}

	private float speed() {
		return sprinting ? walkSpeed * SPRINT_MULTIPLIER : walkSpeed;
	}

	private void jump() {
		velocityY = 0.42F + jumpBoost;
		if (sprinting) {
			float radians = yaw * (float) (Math.PI / 180.0);
			velocityX -= MathHelper.sin(radians) * 0.2F;
			velocityZ += MathHelper.cos(radians) * 0.2F;
		}
	}

	private void travel(float strafe, float forward) {
		if (inWater) {
			double startY = y();
			float drag = 0.8F;
			float acceleration = 0.02F;
			float strider = onGround ? depthStrider : depthStrider * 0.5F;
			if (strider > 0.0F) {
				drag += (0.54600006F - drag) * strider / 3.0F;
				acceleration += (speed() - acceleration) * strider / 3.0F;
			}

			accelerate(strafe, forward, acceleration);
			move(velocityX, velocityY, velocityZ);
			velocityX *= drag;
			velocityY *= 0.8F;
			velocityZ *= drag;
			velocityY -= 0.02D;
			climbOutOfLiquid(startY);
			return;
		}

		if (isInLava()) {
			double startY = y();
			accelerate(strafe, forward, 0.02F);
			move(velocityX, velocityY, velocityZ);
			velocityX *= 0.5D;
			velocityY *= 0.5D;
			velocityZ *= 0.5D;
			velocityY -= 0.02D;
			climbOutOfLiquid(startY);
			return;
		}

		float friction = friction();
		accelerate(strafe, forward, onGround ? speed() * (0.16277136F / (friction * friction * friction)) : airSpeed);
		friction = friction();

		if (isClimbing()) {
			velocityX = MathHelper.clamp(velocityX, -0.15F, 0.15F);
			velocityZ = MathHelper.clamp(velocityZ, -0.15F, 0.15F);
			if (velocityY < -0.15D) velocityY = -0.15D;
			if (sneaking && velocityY < 0.0D) velocityY = 0.0D;
		}

		move(velocityX, velocityY, velocityZ);
		if (collidingHorizontally && isClimbing()) velocityY = 0.2D;

		BlockPos column = new BlockPos((int) x(), 0, (int) z());
		if (!world.isChunkLoaded(column) || !world.getChunk(column).isLoaded()) {
			velocityY = y() > 0.0D ? -0.1D : 0.0D;
		} else {
			velocityY -= 0.08D;
		}

		velocityY *= 0.98F;
		velocityX *= friction;
		velocityZ *= friction;
	}

	private float friction() {
		if (!onGround) return 0.91F;
		return block(MathHelper.floor(x()), MathHelper.floor(shape.minY) - 1, MathHelper.floor(z())).slipperiness * 0.91F;
	}

	private void climbOutOfLiquid(double startY) {
		Box moved = shape.moved(velocityX, velocityY + 0.6F - y() + startY, velocityZ);
		if (collidingHorizontally && world.getCollisions(player, moved).isEmpty() && !world.containsLiquid(moved)) velocityY = 0.3F;
	}

	private void accelerate(float strafe, float forward, float acceleration) {
		float length = strafe * strafe + forward * forward;
		if (length < 1.0E-4F) return;

		float scale = acceleration / Math.max(1.0F, MathHelper.sqrt(length));
		strafe *= scale;
		forward *= scale;
		float sin = MathHelper.sin(yaw * (float) Math.PI / 180.0F);
		float cos = MathHelper.cos(yaw * (float) Math.PI / 180.0F);
		velocityX += strafe * cos - forward * sin;
		velocityZ += forward * cos + strafe * sin;
	}

	private void move(double dx, double dy, double dz) {
		if (inCobweb) {
			inCobweb = false;
			dx *= 0.25D;
			dy *= 0.05F;
			dz *= 0.25D;
			velocityX = velocityY = velocityZ = 0.0D;
		}

		double wantedX = dx;
		double wantedY = dy;
		double wantedZ = dz;
		boolean edge = onGround && sneaking;
		if (edge) {
			double step = 0.05D;
			for (; dx != 0.0D && clear(shape.moved(dx, -1.0D, 0.0D)); wantedX = dx) dx = approachZero(dx, step);
			for (; dz != 0.0D && clear(shape.moved(0.0D, -1.0D, dz)); wantedZ = dz) dz = approachZero(dz, step);
			for (; dx != 0.0D && dz != 0.0D && clear(shape.moved(dx, -1.0D, dz)); wantedZ = dz) {
				dx = approachZero(dx, step);
				wantedX = dx;
				dz = approachZero(dz, step);
			}
		}

		List<Box> boxes = world.getCollisions(player, shape.expanded(dx, dy, dz));
		Box start = shape;
		for (Box box : boxes) dy = box.intersectY(shape, dy);
		shape = shape.moved(0.0D, dy, 0.0D);
		boolean canStep = onGround || wantedY != dy && wantedY < 0.0D;
		for (Box box : boxes) dx = box.intersectX(shape, dx);
		shape = shape.moved(dx, 0.0D, 0.0D);
		for (Box box : boxes) dz = box.intersectZ(shape, dz);
		shape = shape.moved(0.0D, 0.0D, dz);

		if (stepHeight > 0.0F && canStep && (wantedX != dx || wantedZ != dz)) {
			double flatX = dx;
			double flatY = dy;
			double flatZ = dz;
			Box flat = shape;
			shape = start;
			dy = stepHeight;
			List<Box> stepBoxes = world.getCollisions(player, shape.expanded(wantedX, dy, wantedZ));

			Box raised = shape;
			Box sweep = raised.expanded(wantedX, 0.0D, wantedZ);
			double raisedY = dy;
			for (Box box : stepBoxes) raisedY = box.intersectY(sweep, raisedY);
			raised = raised.moved(0.0D, raisedY, 0.0D);
			double raisedX = wantedX;
			for (Box box : stepBoxes) raisedX = box.intersectX(raised, raisedX);
			raised = raised.moved(raisedX, 0.0D, 0.0D);
			double raisedZ = wantedZ;
			for (Box box : stepBoxes) raisedZ = box.intersectZ(raised, raisedZ);
			raised = raised.moved(0.0D, 0.0D, raisedZ);

			Box lifted = shape;
			double liftedY = dy;
			for (Box box : stepBoxes) liftedY = box.intersectY(lifted, liftedY);
			lifted = lifted.moved(0.0D, liftedY, 0.0D);
			double liftedX = wantedX;
			for (Box box : stepBoxes) liftedX = box.intersectX(lifted, liftedX);
			lifted = lifted.moved(liftedX, 0.0D, 0.0D);
			double liftedZ = wantedZ;
			for (Box box : stepBoxes) liftedZ = box.intersectZ(lifted, liftedZ);
			lifted = lifted.moved(0.0D, 0.0D, liftedZ);

			if (raisedX * raisedX + raisedZ * raisedZ > liftedX * liftedX + liftedZ * liftedZ) {
				dx = raisedX;
				dz = raisedZ;
				dy = -raisedY;
				shape = raised;
			} else {
				dx = liftedX;
				dz = liftedZ;
				dy = -liftedY;
				shape = lifted;
			}

			for (Box box : stepBoxes) dy = box.intersectY(shape, dy);
			shape = shape.moved(0.0D, dy, 0.0D);
			if (flatX * flatX + flatZ * flatZ >= dx * dx + dz * dz) {
				dx = flatX;
				dy = flatY;
				dz = flatZ;
				shape = flat;
			}
		}

		collidingHorizontally = wantedX != dx || wantedZ != dz;
		collidingVertically = wantedY != dy;
		onGround = collidingVertically && wantedY < 0.0D;

		int blockX = MathHelper.floor(x());
		int blockY = MathHelper.floor(y() - 0.2F);
		int blockZ = MathHelper.floor(z());
		Block block = block(blockX, blockY, blockZ);
		if (block.getMaterial() == Material.AIR) {
			Block below = block(blockX, blockY - 1, blockZ);
			if (below instanceof FenceBlock || below instanceof WallBlock || below instanceof FenceGateBlock) block = below;
		}

		if (wantedX != dx) velocityX = 0.0D;
		if (wantedZ != dz) velocityZ = 0.0D;
		if (wantedY != dy) velocityY = block == Blocks.SLIME && !sneaking && velocityY < 0.0D ? -velocityY : 0.0D;
		if (!edge && onGround && block == Blocks.SLIME && Math.abs(velocityY) < 0.1D && !sneaking) {
			double slow = 0.4D + Math.abs(velocityY) * 0.2D;
			velocityX *= slow;
			velocityZ *= slow;
		}

		collideWithBlocks();
	}

	private void collideWithBlocks() {
		int minX = MathHelper.floor(shape.minX + 0.001D);
		int minY = MathHelper.floor(shape.minY + 0.001D);
		int minZ = MathHelper.floor(shape.minZ + 0.001D);
		int maxX = MathHelper.floor(shape.maxX - 0.001D);
		int maxY = MathHelper.floor(shape.maxY - 0.001D);
		int maxZ = MathHelper.floor(shape.maxZ - 0.001D);
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					Block block = block(x, y, z);
					if (block == Blocks.WEB) {
						inCobweb = true;
					} else if (block == Blocks.SOUL_SAND) {
						velocityX *= 0.4D;
						velocityZ *= 0.4D;
					}
				}
			}
		}
	}

	private boolean clear(Box box) {
		return world.getCollisions(player, box).isEmpty();
	}

	private Block block(int x, int y, int z) {
		return world.getBlockState(new BlockPos(x, y, z)).getBlock();
	}

	private static double approachZero(double value, double step) {
		if (value < step && value >= -step) return 0.0D;
		return value > 0.0D ? value - step : value + step;
	}
}
