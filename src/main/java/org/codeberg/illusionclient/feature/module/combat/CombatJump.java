package org.codeberg.illusionclient.feature.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.input.InputEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.RangeSlider;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Section;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.client.Weapons;
import org.codeberg.illusionclient.util.CombatUtils;
import org.codeberg.illusionclient.util.RotationUtils;
import org.codeberg.illusionclient.util.SimulatedPlayer;

@ModuleInfo(name = "Combat Jump", category = Category.COMBAT)
@Description("Jumps into fights and times jumps in trades so your hits land first and as criticals.")
public class CombatJump extends Module {
	private static final double REACH = 3.0D;
	private static final double TARGET_FOV = 70.0D;
	private final Minecraft minecraft = Minecraft.getInstance();

	@Section("Engage")
	@Description("Jumps toward a target before a trade so you arrive at the edge of your reach with momentum.")
	@Check("Engage Jump")
	public boolean engage = true;

	@Description("Only jumps in while the target is this far away.")
	@Requires("engage")
	@RangeSlider(value = "Jump Distance", min = 0.0F, max = 16.0F)
	public float[] jumpDistance = {5.0F, 8.0F};

	@Description("Jumps if you'd end up this far from the target after the predicted ticks.")
	@Requires("engage")
	@RangeSlider(value = "Landing Distance", min = 0.0F, max = 6.0F)
	public float[] landingDistance = {3.05F, 3.25F};

	@Description("How far ahead your movement is predicted.")
	@Requires("engage")
	@Slider(value = "Predicted Ticks", min = 0.0F, max = 10.0F)
	public float predictedTicks = 6.0F;

	@Section("Trade")
	@Description("Jumps mid-trade so you're falling, in reach, right as the target can take damage again.")
	@Check("Trade Jump")
	public boolean trade = true;

	@Section("Prediction")
	@Description("How many ticks of the target's movement are predicted.")
	@Slider(value = "Enemy Prediction (ticks)", min = 0.0F, max = 10.0F)
	public float enemyPrediction = 1.5F;

	@Section("Conditions")
	@Description("Only jumps while sprinting.")
	@Check("Sprinting")
	public boolean sprinting = true;

	@Description("Only jumps while the attack key is held.")
	@Check("Left Mouse Pressed")
	public boolean leftMousePressed = false;

	@Description("Only jumps while holding a weapon.")
	@Check("Weapons Only")
	public boolean weaponsOnly = true;

	@Listen
	public void onInput(InputEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || minecraft.world == null || !conditionsMet(player)) return;

		PlayerEntity target = findTarget(player);
		if (target == null) return;

		int hittableIn = IllusionClient.COMBAT.ticksUntilDamageable(target);
		if (trade && hittableIn > 0 ? shouldTradeJump(player, target, hittableIn) : engage && shouldEngageJump(player, target)) {
			IllusionClient.INPUT.client.jump = true;
		}
	}

	private boolean shouldEngageJump(LocalClientPlayerEntity player, PlayerEntity target) {
		double distance = CombatUtils.distanceTo(target);
		if (distance < jumpDistance[0] || distance > jumpDistance[1]) return false;

		int ticks = Math.round(predictedTicks);
		SimulatedPlayer self = simulateJump(player, ticks);
		double landing = CombatUtils.distanceTo(self.eyePosition(), predictedShape(target, ticks));
		return landing >= landingDistance[0] && landing <= landingDistance[1];
	}

	private boolean shouldTradeJump(LocalClientPlayerEntity player, PlayerEntity target, int ticks) {
		SimulatedPlayer self = simulateJump(player, ticks);
		if (self.onGround || self.velocityY >= 0.0D) return false;
		return CombatUtils.distanceTo(self.eyePosition(), predictedShape(target, ticks)) <= REACH;
	}

	private static SimulatedPlayer simulateJump(LocalClientPlayerEntity player, int ticks) {
		SimulatedPlayer self = SimulatedPlayer.of(player);
		self.jumping = true;
		for (int tick = 0; tick < ticks; tick++) {
			self.tick();
			self.jumping = false;
		}
		return self;
	}

	private Box predictedShape(PlayerEntity target, int ticks) {
		double lead = Math.min(ticks, enemyPrediction);
		return target.getShape().moved((target.x - target.prevX) * lead, 0.0D, (target.z - target.prevZ) * lead);
	}

	private PlayerEntity findTarget(LocalClientPlayerEntity player) {
		HitResult crosshair = minecraft.crosshairTarget;
		if (crosshair != null && crosshair.entity instanceof PlayerEntity hovered && valid(hovered)) return hovered;

		double range = Math.max(jumpDistance[1], REACH);
		return CombatUtils.best(other -> valid(other)
			&& CombatUtils.distanceTo(other) <= range
			&& RotationUtils.angleTo(other, player.yaw, player.pitch) <= TARGET_FOV, CombatUtils::distanceTo);
	}

	private static boolean valid(PlayerEntity player) {
		return player.isAlive() && !player.isSleeping() && CombatUtils.isTarget(player);
	}

	private boolean conditionsMet(LocalClientPlayerEntity player) {
		if (minecraft.screen != null || !player.isAlive() || !player.onGround || player.input.jumping) return false;
		if (IllusionClient.INPUT.player.forwardAxis() <= 0.0F) return false;
		if (sprinting && !player.isSprinting()) return false;
		if (leftMousePressed && !IllusionClient.INPUT.held(minecraft.options.attackKey)) return false;
		return !weaponsOnly || Weapons.isHolding(player);
	}
}
