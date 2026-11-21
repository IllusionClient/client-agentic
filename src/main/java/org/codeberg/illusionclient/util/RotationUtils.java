package org.codeberg.illusionclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class RotationUtils {
	public static final double NOISE_PER_DEGREE = 0.03D;
	private static final double CLOSE_RANGE = 3.5D;
	private static final double EDGE_INSET = 0.05D;

	public static float[] rotationTo(Vec3d point) {
		return rotationTo(Minecraft.getInstance().player.getEyePosition(1.0F), point);
	}

	public static float[] rotationTo(Vec3d eye, Vec3d point) {
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

	public static Vec3d look(float yaw, float pitch) {
		double yawRadians = Math.toRadians(yaw);
		double pitchRadians = Math.toRadians(pitch);
		double horizontal = Math.cos(pitchRadians);
		return new Vec3d(-Math.sin(yawRadians) * horizontal, -Math.sin(pitchRadians), Math.cos(yawRadians) * horizontal);
	}

	public static Vec3d aimPoint(Entity target, Box box, float horizontalMultipoint, float verticalMultipoint, float randomization, double noiseSeed) {
		Vec3d eye = Minecraft.getInstance().player.getEyePosition(1.0F);
		float near = (float) Math.min(1.0D, CombatUtils.distanceTo(box) / CLOSE_RANGE);
		float spread = randomization / 100.0F * (0.1F + 0.9F * near);
		float multipoint = horizontalMultipoint * near;
		double x = axis(eye.x, (box.minX + box.maxX) / 2.0D, box.minX, box.maxX, multipoint, spread * 0.4F, NoiseUtils.noise(noiseSeed));
		double y = axis(eye.y, box.minY + target.getEyeHeight(), box.minY, box.maxY, verticalMultipoint, spread * 0.3F, NoiseUtils.noise(noiseSeed + 10000.0D));
		double z = axis(eye.z, (box.minZ + box.maxZ) / 2.0D, box.minZ, box.maxZ, multipoint, spread * 0.4F, NoiseUtils.noise(noiseSeed + 20000.0D));
		return new Vec3d(x, y, z);
	}

	public static float easePitch(float pitchDiff, float yawDiff) {
		return pitchDiff * Math.max(0.0F, 1.0F - Math.abs(yawDiff) / NoiseUtils.wander(40.0F, 120.0F, 5.0F, 0));
	}

	public static float[] aimSteps(float yawDiff, float pitchDiff, float horizontalSpeed, float verticalSpeed, float delta, String mode) {
		return aimSteps(yawDiff, pitchDiff, horizontalSpeed, verticalSpeed, delta, mode.equals("Linear") ? 0.0F : 1.0F);
	}

	public static float[] aimSteps(float yawDiff, float pitchDiff, float horizontalSpeed, float verticalSpeed, float delta, float acceleration) {
		float drift = NoiseUtils.wander(0.95F, 1.05F, 60.0F, 1);
		return new float[]{
			step(yawDiff, horizontalSpeed * drift * NoiseUtils.wander(0.9F, 1.1F, 1.0F, 2), delta, acceleration),
			step(pitchDiff, verticalSpeed * drift * NoiseUtils.wander(0.9F, 1.1F, 1.0F, 3), delta, acceleration)
		};
	}

	public static float pitchWobble(float yawDegrees) {
		return yawDegrees * NoiseUtils.wander(-0.1F, 0.1F, 1.0F, 4);
	}

	private static float step(float diff, float speed, float delta, float acceleration) {
		float limit = speed * 6.0F * delta * Math.min(1.0F, 0.4F + Math.abs(diff) / 15.0F);
		float linear = Math.clamp(diff, -limit, limit);
		float eased = diff * (1.0F - (float) Math.pow(1.0F - Math.min(1.0F, speed / 100.0F), delta * 20.0F));
		return linear + (eased - linear) * acceleration;
	}

	private static double axis(double eye, double center, double min, double max, float multipoint, float spread, float noise) {
		double size = max - min;
		double inset = Math.min(EDGE_INSET, size / 2.0D);
		double reach = Math.min(size * spread, size / 2.0D - inset);
		double closest = Math.clamp(eye, min + inset + reach, max - inset - reach);
		double point = center + (closest - center) * multipoint / 100.0F + reach * noise;
		return Math.clamp(point, min + inset, max - inset);
	}

	private static float yaw(double x, double z) {
		return (float) Math.toDegrees(Math.atan2(z, x)) - 90.0F;
	}

	private static float pitch(double x, double y, double z) {
		return (float) -Math.toDegrees(Math.atan2(y, Math.sqrt(x * x + z * z)));
	}
}
