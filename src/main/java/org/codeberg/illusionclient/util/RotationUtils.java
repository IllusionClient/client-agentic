package org.codeberg.illusionclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class RotationUtils {
	public static float[] rotationTo(Vec3d point) {
		Vec3d eye = Minecraft.getInstance().player.getEyePosition(1.0F);
		double x = point.x - eye.x;
		double y = point.y - eye.y;
		double z = point.z - eye.z;
		return new float[]{yaw(x, z), pitch(x, y, z)};
	}

	public static double angleTo(Entity entity, float yaw, float pitch) {
		Vec3d eye = Minecraft.getInstance().player.getEyePosition(1.0F);
		Box box = entity.getShape();
		double x = (box.minX + box.maxX) / 2.0D - eye.x;
		double y = (box.minY + box.maxY) / 2.0D - eye.y;
		double z = (box.minZ + box.maxZ) / 2.0D - eye.z;
		return Math.hypot(MathHelper.wrapDegrees(yaw(x, z) - yaw), pitch(x, y, z) - pitch);
	}

	public static float mouseScale() {
		float sensitivity = Minecraft.getInstance().options.mouseSensitivity * 0.6F + 0.2F;
		return sensitivity * sensitivity * sensitivity * 8.0F;
	}

	public static int mouseCounts(float degrees, float limit, float gcd) {
		int max = (int) (limit / gcd);
		return Math.clamp((int) (degrees / gcd), Math.min(max, 0), Math.max(max, 0));
	}

	private static float yaw(double x, double z) {
		return (float) Math.toDegrees(Math.atan2(z, x)) - 90.0F;
	}

	private static float pitch(double x, double y, double z) {
		return (float) -Math.toDegrees(Math.atan2(y, Math.sqrt(x * x + z * z)));
	}
}
