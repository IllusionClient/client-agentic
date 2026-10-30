package org.codeberg.illusionclient.util.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.Input;
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
		active = false;
	}
}
