package org.codeberg.illusionclient.util;

import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.codeberg.illusionclient.feature.module.client.AntiBot;
import org.codeberg.illusionclient.feature.module.client.Teams;

public final class CombatUtils {
	public static boolean isTarget(Entity entity) {
		if (!(entity instanceof PlayerEntity player)) return false;

		return !Teams.isTeammate(player) && !AntiBot.isBot(player);
	}

	public static double distanceTo(Entity entity) {
		return distanceTo(entity.getShape());
	}

	public static double distanceTo(Box box) {
		Vec3d eye = Minecraft.getInstance().player.getEyePosition(1.0F);
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

	public static boolean isHovered(Entity entity, double reach, double margin) {
		LocalClientPlayerEntity self = Minecraft.getInstance().player;
		Vec3d eye = self.getEyePosition(1.0F);
		Vec3d look = self.getRotationVector(1.0F);
		Vec3d end = eye.add(look.x * reach, look.y * reach, look.z * reach);
		return entity.getShape().grown(margin, margin, margin).clip(eye, end) != null;
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
}
