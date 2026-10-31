package org.codeberg.illusionclient.util.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.Input;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.codeberg.illusionclient.util.RotationUtils;

public final class RotationManager {
	public enum Strafe { SILENT, STRICT, OFF }

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
		if (!active || strafe != Strafe.SILENT) return;

		float forward = input.movementForward;
		float sideways = input.movementSideways;
		float magnitude = Math.max(Math.abs(forward), Math.abs(sideways));
		if (magnitude == 0.0F) return;

		double radians = Math.toRadians(minecraft.player.yaw - yaw);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		input.movementForward = Math.round((forward * cos + sideways * sin) / magnitude) * magnitude;
		input.movementSideways = Math.round((sideways * cos - forward * sin) / magnitude) * magnitude;
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
