package org.codeberg.illusionclient.util.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.util.RotationUtils;

import java.util.Locale;

public final class Rotator {
	private static final float SECONDS_PER_TICK = 0.05F;
	private static final float RETURNED = 3.0F;

	private float yawRemainder;
	private float pitchRemainder;
	private float turned;

	public float[] steps(float yawDiff, float pitchDiff, float horizontalSpeed, float verticalSpeed, float delta, String mode) {
		if (mode.equals("Lock-on")) return new float[]{yawDiff, pitchDiff};

		float eased = RotationUtils.easePitch(pitchDiff, yawDiff);
		float[] steps = RotationUtils.aimSteps(yawDiff, eased, horizontalSpeed, verticalSpeed, delta, mode);
		steps[1] += RotationUtils.pitchWobble(Math.abs(steps[0]));
		return steps;
	}

	public boolean turnSilently(float[] goal, float horizontalSpeed, float verticalSpeed, String mode, String strafe) {
		LocalClientPlayerEntity player = Minecraft.getInstance().player;
		float yaw = IllusionClient.ROTATION.sentYaw();
		float pitch = IllusionClient.ROTATION.sentPitch();
		float yawDiff = MathHelper.wrapDegrees((goal == null ? player.yaw : goal[0]) - yaw);
		float pitchDiff = (goal == null ? player.pitch : goal[1]) - pitch;
		turned = 0.0F;
		if (goal == null && Math.hypot(yawDiff, pitchDiff) <= RETURNED) return false;

		float[] steps = steps(yawDiff, pitchDiff, horizontalSpeed, verticalSpeed, SECONDS_PER_TICK, mode);
		IllusionClient.ROTATION.rotate(yaw + steps[0], pitch + steps[1], RotationManager.Strafe.valueOf(strafe.toUpperCase(Locale.ROOT)));
		turned = Math.abs(steps[0]) + Math.abs(steps[1]);
		return true;
	}

	public void turnCamera(LocalClientPlayerEntity player, float yawStep, float pitchStep, float yawLimit, float pitchLimit) {
		float scale = RotationUtils.mouseScale();
		float gcd = scale * 0.15F;
		float yawTotal = yawStep + yawRemainder;
		int yawCounts = RotationUtils.mouseCounts(yawTotal, yawLimit, gcd);
		yawRemainder = yawTotal - yawCounts * gcd;
		float pitchTotal = pitchStep + pitchRemainder;
		int pitchCounts = RotationUtils.mouseCounts(pitchTotal, pitchLimit, gcd);
		pitchRemainder = pitchTotal - pitchCounts * gcd;
		if (yawCounts != 0 || pitchCounts != 0) player.updateLocalPlayerCamera(yawCounts * scale, -pitchCounts * scale);
	}

	public void clearRemainder() {
		yawRemainder = pitchRemainder = 0.0F;
	}

	public float turned() {
		return turned;
	}
}
