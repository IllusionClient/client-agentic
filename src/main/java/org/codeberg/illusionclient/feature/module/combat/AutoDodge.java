package org.codeberg.illusionclient.feature.module.combat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FishingBobberEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.ExperienceBottleEntity;
import net.minecraft.entity.projectile.PotionEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ThrownEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.input.InputEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Section;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.util.CombatUtils;
import org.codeberg.illusionclient.util.SimulatedPlayer;
import org.codeberg.illusionclient.util.input.InputState;

@ModuleInfo(name = "Auto Dodge", category = Category.COMBAT)
@Description("Strafes or jumps out of the way of incoming arrows, fireballs, fishing rods and thrown items.")
public class AutoDodge extends Module {
	private static final double HIT_MARGIN = 0.3D;
	private static final double MAX_DROP = 1.0D;
	private static final double MIN_SPEED = 0.05D;
	private static final double REACH_PADDING = 4.0D;
	private static final int[][] DODGES = {{1, 0}, {-1, 0}, {0, 1}, {1, 1}, {-1, 1}};
	private final Minecraft minecraft = Minecraft.getInstance();

	@Section("Projectiles")
	@Description("Dodges arrows.")
	@Check("Arrows")
	public boolean arrows = true;

	@Description("Dodges fishing rod bobbers.")
	@Check("Fishing Rods")
	public boolean rods = true;

	@Description("Dodges snowballs, eggs, ender pearls and thrown potions.")
	@Check("Throwables")
	public boolean throwables = true;

	@Description("Dodges ghast and blaze fireballs.")
	@Check("Fireballs")
	public boolean fireballs = true;

	@Section("Dodge")
	@Description("Strafes left or right to dodge.")
	@Check("Strafe")
	public boolean strafe = true;

	@Description("Jumps to dodge.")
	@Check("Jump")
	public boolean jump = true;

	@Description("How far ahead projectiles and your movement are predicted.")
	@Slider(value = "Prediction (ticks)", min = 5.0F, max = 40.0F, step = 1.0F)
	public float prediction = 20.0F;

	@Description("Extra room kept between you and a projectile's path.")
	@Slider(value = "Margin", min = 0.0F, max = 1.0F, step = 0.05F)
	public float margin = 0.2F;

	private int last;

	@Listen
	public void onInput(InputEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || minecraft.world == null || minecraft.screen != null || !player.isAlive()) return;

		int ticks = Math.round(prediction);
		List<Vec3d[]> paths = incoming(player, ticks);
		InputState client = IllusionClient.INPUT.client;
		int side = (int) client.sidewaysAxis();
		if (paths.isEmpty() || safe(player, client, paths, ticks, side, client.jump, false)) return;

		for (int i = 0; i < DODGES.length; i++) {
			int index = (last + i) % DODGES.length;
			int dodgeSide = DODGES[index][0];
			boolean dodgeJump = DODGES[index][1] == 1;
			if (dodgeSide != 0 && !strafe || dodgeJump && (!jump || !player.onGround)) continue;
			if (dodgeSide == 0) dodgeSide = side;
			if (!safe(player, client, paths, ticks, dodgeSide, dodgeJump || client.jump, true)) continue;

			last = index;
			client.left = dodgeSide > 0;
			client.right = dodgeSide < 0;
			if (dodgeJump) client.jump = true;
			return;
		}
	}

	private boolean safe(LocalClientPlayerEntity player, InputState client, List<Vec3d[]> paths, int ticks, int side, boolean jumping, boolean careful) {
		float scale = client.sneak ? 0.3F : 1.0F;
		SimulatedPlayer self = SimulatedPlayer.of(player);
		self.forward = client.forwardAxis() * scale;
		self.sideways = side * scale;
		self.sneaking = client.sneak;
		self.jumping = jumping;
		double floor = self.y() - MAX_DROP;
		double grow = HIT_MARGIN + margin;
		for (int tick = 0; tick < ticks; tick++) {
			self.tick();
			if (careful && self.y() < floor) return false;

			Box box = self.shape.grown(grow, grow, grow);
			for (Vec3d[] path : paths) {
				if (tick + 1 < path.length && box.clip(path[tick], path[tick + 1]) != null) return false;
			}
		}
		return true;
	}

	private List<Vec3d[]> incoming(LocalClientPlayerEntity player, int ticks) {
		List<Vec3d[]> paths = new ArrayList<>();
		for (Entity entity : minecraft.world.entities) {
			double drag;
			double gravity;
			double vx = entity.velocityX;
			double vy = entity.velocityY;
			double vz = entity.velocityZ;
			Entity owner;
			if (entity instanceof ArrowEntity arrow && arrows) {
				drag = 0.99D;
				gravity = 0.05D;
				owner = arrow.shooter;
			} else if (entity instanceof FishingBobberEntity bobber && rods && bobber.hookedEntity == null) {
				drag = 0.92D;
				gravity = 0.04D * drag;
				owner = bobber.thrower;
			} else if (entity instanceof ThrownEntity thrown && throwables) {
				drag = 0.99D;
				gravity = thrown instanceof PotionEntity ? 0.05D : thrown instanceof ExperienceBottleEntity ? 0.07D : 0.03D;
				owner = thrown.getThrower();
			} else if (entity instanceof ProjectileEntity fireball && fireballs) {
				drag = 1.0D;
				gravity = 0.0D;
				vx = entity.x - entity.prevX;
				vy = entity.y - entity.prevY;
				vz = entity.z - entity.prevZ;
				owner = fireball.shooter;
			} else {
				continue;
			}

			if (owner == player || owner instanceof PlayerEntity shooter && !CombatUtils.isTarget(shooter)) continue;
			double moved = Math.abs(entity.x - entity.prevX) + Math.abs(entity.y - entity.prevY) + Math.abs(entity.z - entity.prevZ);
			if (moved < MIN_SPEED) continue;

			double dx = player.x - entity.x;
			double dy = player.y + player.height / 2.0F - entity.y;
			double dz = player.z - entity.z;
			if (dx * vx + dy * vy + dz * vz <= 0.0D) continue;

			double reach = Math.sqrt(vx * vx + vy * vy + vz * vz) * ticks + REACH_PADDING;
			if (dx * dx + dy * dy + dz * dz > reach * reach) continue;

			paths.add(path(entity, vx, vy, vz, drag, gravity, ticks));
		}
		return paths;
	}

	private Vec3d[] path(Entity entity, double vx, double vy, double vz, double drag, double gravity, int ticks) {
		Vec3d[] path = new Vec3d[ticks + 1];
		path[0] = new Vec3d(entity.x, entity.y, entity.z);
		for (int tick = 0; tick < ticks; tick++) {
			Vec3d next = path[tick].add(vx, vy, vz);
			HitResult wall = minecraft.world.rayTrace(path[tick], next);
			if (wall != null) {
				path[tick + 1] = wall.facePos;
				return Arrays.copyOf(path, tick + 2);
			}
			path[tick + 1] = next;
			vx *= drag;
			vy = vy * drag - gravity;
			vz *= drag;
		}
		return path;
	}
}
