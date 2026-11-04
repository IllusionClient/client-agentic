package org.codeberg.illusionclient.util.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.Input;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.codeberg.illusionclient.util.MoveUtils;
import org.codeberg.illusionclient.util.RotationUtils;
import org.codeberg.illusionclient.util.SimulatedPlayer;

public final class RotationManager {
	public enum Strafe { SILENT, STRICT, OFF }

	private static final double MAX_DRIFT = 0.5D;
	private static final double DRIFT_TOLERANCE = 0.03D;
	private static final double PROGRESS_WEIGHT = 0.001D;
	private static final double MIN_PROGRESS = 0.4D;

	private final Minecraft minecraft = Minecraft.getInstance();
	private boolean active;
	private float yaw;
	private float pitch;
	private Strafe strafe = Strafe.OFF;
	private float sentYaw;
	private float sentPitch;
	private boolean lastActive;
	private boolean model;
	private final float[] modelRotation = new float[6];
	private final float[] savedRotation = new float[6];
	private boolean steering;
	private double drift;
	private double lastX;
	private double lastZ;

	public void rotate(float yaw, float pitch, Strafe strafe) {
		float gcd = RotationUtils.mouseScale() * 0.15F;
		this.yaw = sentYaw + Math.round(MathHelper.wrapDegrees(yaw - sentYaw) / gcd) * gcd;
		this.pitch = Math.clamp(sentPitch + Math.round((pitch - sentPitch) / gcd) * gcd, -90.0F, 90.0F);
		this.strafe = strafe;
		active = true;
	}

	public boolean active() {
		return active;
	}

	public float sentYaw() {
		return sentYaw;
	}

	public float sentPitch() {
		return sentPitch;
	}

	public float yaw(float yaw) {
		return active ? this.yaw : yaw;
	}

	public float pitch(float pitch) {
		return active ? this.pitch : pitch;
	}

	public float moveYaw(float yaw) {
		return active && strafe != Strafe.OFF ? this.yaw : yaw;
	}

	public void correctInput(Input input) {
		LocalClientPlayerEntity player = minecraft.player;
		float magnitude = Math.max(Math.abs(input.movementForward), Math.abs(input.movementSideways));
		if (!active || strafe != Strafe.SILENT || magnitude == 0.0F) {
			steering = false;
			drift = 0.0D;
			return;
		}

		double[] heading = MoveUtils.inputDirection(player.yaw, input.movementForward, input.movementSideways);
		double length = Math.hypot(heading[0], heading[1]);
		double headingX = heading[0] / length;
		double headingZ = heading[1] / length;
		if (steering) drift = Math.clamp(drift + sideways(player.x - lastX, player.z - lastZ, headingX, headingZ), -MAX_DRIFT, MAX_DRIFT);
		steering = true;
		lastX = player.x;
		lastZ = player.z;

		float bestForward = 0.0F;
		float bestSideways = 0.0F;
		double bestCost = Double.MAX_VALUE;
		for (int forward = -1; forward <= 1; forward++) {
			for (int sideways = -1; sideways <= 1; sideways++) {
				double[] motion = MoveUtils.inputDirection(yaw, forward, sideways);
				double progress = (motion[0] * headingX + motion[1] * headingZ) / Math.max(1.0D, Math.hypot(forward, sideways));
				if (progress < MIN_PROGRESS) continue;

				SimulatedPlayer simulated = SimulatedPlayer.of(player);
				simulated.yaw = yaw;
				simulated.forward = forward * magnitude;
				simulated.sideways = sideways * magnitude;
				simulated.tick();
				double error = Math.abs(drift + sideways(simulated.x() - player.x, simulated.z() - player.z, headingX, headingZ));
				double cost = Math.max(error, DRIFT_TOLERANCE) - progress * PROGRESS_WEIGHT;
				if (cost < bestCost) {
					bestCost = cost;
					bestForward = forward * magnitude;
					bestSideways = sideways * magnitude;
				}
			}
		}
		input.movementForward = bestForward;
		input.movementSideways = bestSideways;
	}

	private static double sideways(double x, double z, double headingX, double headingZ) {
		return x * headingZ - z * headingX;
	}

	public void sent(float yaw, float pitch) {
		sentYaw = yaw;
		sentPitch = pitch;
		LocalClientPlayerEntity player = minecraft.player;
		model = active || lastActive;
		lastActive = active;
		modelRotation[0] = modelRotation[1];
		modelRotation[1] = yaw;
		modelRotation[2] = modelRotation[3];
		modelRotation[3] = yaw + Math.clamp(MathHelper.wrapDegrees(player.bodyYaw - yaw), -75.0F, 75.0F);
		modelRotation[4] = modelRotation[5];
		modelRotation[5] = pitch;
		if (active) align(player);
		active = false;
	}

	public boolean swapModelRotation(LivingEntity entity) {
		if (!model || entity != minecraft.player) return false;

		savedRotation[0] = entity.lastHeadYaw;
		savedRotation[1] = entity.headYaw;
		savedRotation[2] = entity.lastBodyYaw;
		savedRotation[3] = entity.bodyYaw;
		savedRotation[4] = entity.lastPitch;
		savedRotation[5] = entity.pitch;
		setModelRotation(entity, modelRotation);
		return true;
	}

	public void restoreModelRotation(LivingEntity entity) {
		setModelRotation(entity, savedRotation);
	}

	private static void setModelRotation(LivingEntity entity, float[] rotation) {
		entity.lastHeadYaw = rotation[0];
		entity.headYaw = rotation[1];
		entity.lastBodyYaw = rotation[2];
		entity.bodyYaw = rotation[3];
		entity.lastPitch = rotation[4];
		entity.pitch = rotation[5];
	}

	private void align(LocalClientPlayerEntity player) {
		float gcd = RotationUtils.mouseScale() * 0.15F;
		float yawShift = sentYaw + Math.round(MathHelper.wrapDegrees(player.yaw - sentYaw) / gcd) * gcd - player.yaw;
		float pitch = Math.clamp(sentPitch + Math.round((player.pitch - sentPitch) / gcd) * gcd, -90.0F, 90.0F);
		player.yaw += yawShift;
		player.lastYaw += yawShift;
		player.easedYaw += yawShift;
		player.lastEasedYaw += yawShift;
		player.lastPitch += pitch - player.pitch;
		player.pitch = pitch;
	}
}
