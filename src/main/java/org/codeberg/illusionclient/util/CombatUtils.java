package org.codeberg.illusionclient.util;

import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.feature.module.client.AntiBot;
import org.codeberg.illusionclient.feature.module.client.Tags;
import org.codeberg.illusionclient.feature.module.client.Teams;

public final class CombatUtils {
	public static final byte HURT = 2;
	private static final double[] SIGHT_HEIGHTS = {0.1D, 0.5D, 0.9D};

	public static boolean isTarget(Entity entity) {
		if (!(entity instanceof PlayerEntity player)) return false;

		return !Teams.isTeammate(player) && !AntiBot.isBot(player) && !Tags.isIgnored(player);
	}

	public static double distanceTo(Entity entity) {
		return distanceTo(entity.getShape());
	}

	public static double distanceTo(Box box) {
		return distanceTo(Minecraft.getInstance().player.getEyePosition(1.0F), box);
	}

	public static double distanceTo(Vec3d eye, Box box) {
		double x = eye.x - Math.clamp(eye.x, box.minX, box.maxX);
		double y = eye.y - Math.clamp(eye.y, box.minY, box.maxY);
		double z = eye.z - Math.clamp(eye.z, box.minZ, box.maxZ);
		return Math.sqrt(x * x + y * y + z * z);
	}

	public static PlayerEntity best(Predicate<PlayerEntity> filter, ToDoubleFunction<PlayerEntity> score) {
		Minecraft minecraft = Minecraft.getInstance();
		PlayerEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (PlayerEntity player : minecraft.world.players) {
			if (player == minecraft.player || !filter.test(player)) continue;

			double value = score.applyAsDouble(player);
			if (value < bestScore) {
				best = player;
				bestScore = value;
			}
		}
		return best;
	}

	public static double score(LivingEntity entity, String sort, float yaw, float pitch) {
		return switch (sort) {
			case "Health" -> entity.getHealth();
			case "Hurt time" -> entity.damagedTimer;
			case "Aim angle" -> RotationUtils.angleTo(entity, yaw, pitch);
			default -> distanceTo(entity);
		};
	}

	public static boolean isHovered(Entity entity, double reach, double margin) {
		return isHovered(entity, Minecraft.getInstance().player.getRotationVector(1.0F), reach, margin);
	}

	public static boolean isHovered(Entity entity, Vec3d look, double reach, double margin) {
		Vec3d eye = Minecraft.getInstance().player.getEyePosition(1.0F);
		Vec3d end = eye.add(look.x * reach, look.y * reach, look.z * reach);
		Box box = entity.getShape().grown(margin, margin, margin);
		return box.contains(eye) || box.clip(eye, end) != null;
	}

	public static boolean isBlocked(Entity entity, Vec3d look, double reach, double margin) {
		Minecraft minecraft = Minecraft.getInstance();
		Vec3d eye = minecraft.player.getEyePosition(1.0F);
		HitResult hit = entity.getShape().grown(margin, margin, margin).clip(eye, eye.add(look.x * reach, look.y * reach, look.z * reach));
		return hit != null && minecraft.world.rayTrace(eye, hit.facePos) != null;
	}

	public static boolean isVisible(Entity entity) {
		Minecraft minecraft = Minecraft.getInstance();
		Vec3d eye = minecraft.player.getEyePosition(1.0F);
		Box box = entity.getShape();
		Vec3d closest = new Vec3d(Math.clamp(eye.x, box.minX, box.maxX), Math.clamp(eye.y, box.minY, box.maxY), Math.clamp(eye.z, box.minZ, box.maxZ));
		if (minecraft.world.rayTrace(eye, closest) == null) return true;

		double x = (box.minX + box.maxX) / 2.0D;
		double z = (box.minZ + box.maxZ) / 2.0D;
		for (double height : SIGHT_HEIGHTS) {
			if (minecraft.world.rayTrace(eye, new Vec3d(x, box.minY + (box.maxY - box.minY) * height, z)) == null) return true;
		}
		return false;
	}

	public static Box predictedShape(Entity entity, float tickDelta, float leadTicks) {
		LocalClientPlayerEntity self = Minecraft.getInstance().player;
		float back = tickDelta - 1.0F;
		float lead = back + leadTicks;
		return entity.getShape().moved(
			(entity.x - entity.prevX) * lead - (self.x - self.prevX) * back,
			(entity.y - entity.prevY) * back - (self.y - self.prevY) * back,
			(entity.z - entity.prevZ) * lead - (self.z - self.prevZ) * back);
	}

	public static Vec3d predictKnockback(Entity target, LivingEntity attacker, float yaw) {
		double vx = (target.x - target.prevX) / 2.0D;
		double vy = (target.onGround ? -0.0784D : target.y - target.prevY) / 2.0D;
		double vz = (target.z - target.prevZ) / 2.0D;
		double dx = attacker.x - target.x;
		double dz = attacker.z - target.z;
		double length = Math.hypot(dx, dz);
		if (length >= 1.0E-4D) {
			vx -= dx / length * 0.4D;
			vz -= dz / length * 0.4D;
		}
		vy = Math.min(vy + 0.4D, 0.4D);

		int bonus = EnchantmentHelper.getKnockbackLevel(attacker) + (attacker.isSprinting() ? 1 : 0);
		if (bonus > 0) {
			double radians = Math.toRadians(yaw);
			vx -= Math.sin(radians) * bonus * 0.5D;
			vy += 0.1D;
			vz += Math.cos(radians) * bonus * 0.5D;
		}
		return new Vec3d(vx, vy, vz);
	}
}
