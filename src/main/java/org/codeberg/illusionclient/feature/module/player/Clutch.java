package org.codeberg.illusionclient.feature.module.player;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockWithBlockEntity;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.input.InputEvent;
import org.codeberg.illusionclient.event.player.PickEvent;
import org.codeberg.illusionclient.event.render.Render3DEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.Section;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.util.ItemUtils;
import org.codeberg.illusionclient.util.NoiseUtils;
import org.codeberg.illusionclient.util.RotationUtils;
import org.codeberg.illusionclient.util.SimulatedPlayer;
import org.codeberg.illusionclient.util.input.InputState;

import java.util.List;
import java.util.Random;

@ModuleInfo(name = "Clutch", category = Category.PLAYER)
@Description("Places blocks under you when you're about to fall.")
public class Clutch extends Module {
	private static final int PREDICT_TICKS = 2;
	private static final double FACE_INSET = 0.05D;
	private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
	private final Minecraft minecraft = Minecraft.getInstance();
	private final Random random = new Random();
	private final Object2IntOpenHashMap<BlockPos> depths = new Object2IntOpenHashMap<>();

	@Section("General")
	@Description("How many blocks it may place to reach something to build off.")
	@Slider(value = "Range", min = 1.0F, max = 6.0F)
	public float range = 3.0F;

	@Description("Only builds off blocks within this angle of your crosshair.")
	@Slider(value = "FOV", min = 30.0F, max = 360.0F)
	public float fov = 360.0F;

	@Description("How many empty blocks must be below you before it helps.")
	@Slider(value = "Minimum Height", min = 1.0F, max = 10.0F)
	public float minimumHeight = 3.0F;

	@Description("The most blocks placed per second.")
	@Slider(value = "Click Speed", min = 5.0F, max = 20.0F)
	public float clickSpeed = 14.0F;

	@Description("Varies aim point, turn speed and timing. More is less consistent.")
	@Slider(value = "Randomization", min = 0.0F, max = 100.0F)
	public float randomization = 30.0F;

	@Section("Blocks")
	@Description("Swaps to hotbar blocks.")
	@Combo(value = "Select Blocks", options = {"No", "On depletion", "Always"})
	public String selectBlocks = "Always";

	@Description("Only places against the sides of blocks, so you stay at the same height.")
	@Check("Only Place Sideways")
	public boolean onlySideways = false;

	@Section("Aim")
	@Description("Steady turn speed in degrees per second.")
	@Slider(value = "Base Speed", min = 90.0F, max = 1800.0F)
	public float baseSpeed = 720.0F;

	@Description("Turns faster the further there is to go.")
	@Slider(value = "Acceleration", min = 0.0F, max = 100.0F)
	public float acceleration = 50.0F;

	@Description("How much Acceleration counts over Base Speed. 0 is only Base Speed.")
	@Slider(value = "Acceleration Strength", min = 0.0F, max = 100.0F)
	public float accelerationStrength = 0.0F;

	@Description("Aims at the point on the block closest to your crosshair.")
	@Check("Multipoint")
	public boolean multipoint = false;

	@Section("Snap-back")
	@Description("How long to wait after placing before turning back.")
	@Slider(value = "Delay (ms)", min = 0.0F, max = 500.0F)
	public float snapDelay = 100.0F;

	@Description("How long turning back takes.")
	@Slider(value = "Duration (ms)", min = 0.0F, max = 500.0F)
	public float snapDuration = 150.0F;

	@Description("Turns back instantly when you jump, so the jump goes where you were looking.")
	@Check("Keep Jump Direction")
	public boolean keepJumpDirection = false;

	@Description("Turns itself off once it has turned back.")
	@Check("Disable Afterwards")
	public boolean disableAfterwards = false;

	@Section("Conditions")
	@Description("Only starts while you are in the air.")
	@Check("In Air")
	public boolean inAir = true;

	@Description("Only starts if you were hit since you last stood on the ground.")
	@Check("Took Damage")
	public boolean tookDamage = false;

	@Description("Only starts while you are moving backward, like from knockback.")
	@Check("Moving Backward")
	public boolean movingBackward = false;

	private boolean active;
	private boolean damaged;
	private float originYaw;
	private float originPitch;
	private float yawRemainder;
	private float pitchRemainder;
	private Plan plan;
	private BlockPos aimedCell;
	private float offsetU;
	private float offsetV;
	private long lastFrame;
	private long plannedAt;
	private long nextClickAt;
	private int groundY = Integer.MAX_VALUE;
	private Block lastBlock;
	private int lastSlot = -1;

	private record Plan(BlockPos cell, BlockPos support, Direction face, int depth, double angle) {
	}

	@Override
	public void onDisable() {
		active = damaged = false;
		groundY = Integer.MAX_VALUE;
		plan = null;
		depths.clear();
		aimedCell = null;
		lastBlock = null;
		lastSlot = -1;
	}

	@Listen
	public void onPick(PickEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || minecraft.world == null || minecraft.screen != null || !player.isAlive()) {
			active = false;
			plan = null;
			return;
		}
		if (player.onGround) groundY = MathHelper.floor(player.y);
		if (player.damagedTimer > 0) damaged = true;
		else if (player.onGround) damaged = false;

		SimulatedPlayer predicted = SimulatedPlayer.of(player);
		List<Box> path = new ObjectArrayList<>(PREDICT_TICKS + 1);
		path.add(player.getShape());
		for (int i = 0; i < PREDICT_TICKS; i++) {
			predicted.tick();
			path.add(predicted.shape);
		}
		int targetY = Math.min(groundY, MathHelper.floor(predicted.y())) - 1;
		boolean falling = falling(predicted, targetY);

		if (!active && falling && conditionsMet(player)) {
			active = true;
			originYaw = player.yaw;
			originPitch = player.pitch;
			yawRemainder = pitchRemainder = 0.0F;
		}
		selectBlocks(player.inventory);
		if (!active) return;

		if (keepJumpDirection && player.onGround && IllusionClient.INPUT.player.jump) {
			snapBack(player);
			plan = null;
			plannedAt = 0L;
			return;
		}

		plan = falling && ItemUtils.isBlock(player.inventory.getSelectedItem()) ? plan(player, path, targetY) : null;
		if (plan == null) return;

		long now = System.currentTimeMillis();
		plannedAt = now;
		if (!plan.cell.equals(aimedCell)) {
			aimedCell = plan.cell;
			offsetU = random.nextFloat() * 2.0F - 1.0F;
			offsetV = random.nextFloat() * 2.0F - 1.0F;
		}

		HitResult target = minecraft.crosshairTarget;
		if (now < nextClickAt || target == null || target.type != HitResult.Type.BLOCK) return;
		if (!useful(player, target)) return;

		KeyBinding.click(minecraft.options.useKey.getKeyCode());
		float jitter = 1.0F + (random.nextFloat() - 0.5F) * randomization / 100.0F;
		nextClickAt = now + (long) (1000.0F / clickSpeed * jitter);
	}

	@Listen
	public void onInput(InputEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (!active || player == null) return;

		InputState input = IllusionClient.INPUT.client;
		float forward = input.forwardAxis();
		float sideways = input.sidewaysAxis();
		if (forward == 0.0F && sideways == 0.0F) return;

		double radians = Math.toRadians(originYaw - player.yaw);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		long newForward = Math.round(forward * cos + sideways * sin);
		long newSideways = Math.round(sideways * cos - forward * sin);
		input.forward = newForward > 0;
		input.back = newForward < 0;
		input.left = newSideways > 0;
		input.right = newSideways < 0;
	}

	@Listen
	public void onRender3D(Render3DEvent event) {
		long now = System.nanoTime();
		float delta = Math.min(0.1F, (now - lastFrame) / 1.0E9F);
		lastFrame = now;
		LocalClientPlayerEntity player = minecraft.player;
		if (!active || player == null) return;

		float gcd = RotationUtils.mouseScale() * 0.15F;
		int mouseY = minecraft.options.invertMouseY ? -minecraft.mouse.y : minecraft.mouse.y;
		originYaw += minecraft.mouse.x * gcd;
		originPitch = Math.clamp(originPitch - mouseY * gcd, -90.0F, 90.0F);

		long millis = System.currentTimeMillis();
		long returnStart = plannedAt + (long) snapDelay;
		if (plan == null && millis < returnStart) return;
		HitResult target = minecraft.crosshairTarget;
		if (plan != null && target != null && target.type == HitResult.Type.BLOCK && useful(player, target)) return;

		float[] goal = plan == null ? new float[]{originYaw, originPitch} : RotationUtils.rotationTo(aimPoint(plan, player));
		float yawDiff = MathHelper.wrapDegrees(goal[0] - player.yaw);
		float pitchDiff = goal[1] - player.pitch;
		float distance = (float) Math.hypot(yawDiff, pitchDiff);
		float step;
		if (plan != null) {
			float base = baseSpeed * delta;
			float accelerated = distance * (1.0F - (float) Math.pow(1.0F - acceleration / 100.0F * 0.99F, delta * 20.0F));
			float spread = randomization / 100.0F * 0.3F;
			step = (base + (accelerated - base) * accelerationStrength / 100.0F) * NoiseUtils.wander(1.0F - spread, 1.0F + spread, 0.5F, 0);
		} else if (distance < gcd) {
			finish();
			return;
		} else {
			float left = (returnStart + snapDuration - millis) / 1000.0F;
			step = left <= delta ? distance : distance * delta / left;
		}
		if (distance < 1.0E-4F) return;
		float scale = Math.min(1.0F, step / distance);
		turn(player, yawDiff * scale, pitchDiff * scale, yawDiff, pitchDiff);
	}

	private void finish() {
		active = false;
		aimedCell = null;
		if (disableAfterwards) setEnabled(false);
	}

	private void snapBack(LocalClientPlayerEntity player) {
		float yaw = MathHelper.wrapDegrees(originYaw - player.yaw);
		float pitch = originPitch - player.pitch;
		yawRemainder = pitchRemainder = 0.0F;
		turn(player, yaw, pitch, yaw, pitch);
		finish();
	}

	private void turn(LocalClientPlayerEntity player, float yawStep, float pitchStep, float yawLimit, float pitchLimit) {
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

	private boolean conditionsMet(LocalClientPlayerEntity player) {
		if (inAir && player.onGround) return false;
		if (tookDamage && !damaged) return false;
		if (!movingBackward) return true;

		float radians = player.yaw * (float) Math.PI / 180.0F;
		return player.velocityX * -MathHelper.sin(radians) + player.velocityZ * MathHelper.cos(radians) < 0.0D;
	}

	private boolean falling(SimulatedPlayer predicted, int targetY) {
		if (predicted.onGround) return false;
		for (BlockPos cell : footprint(predicted.shape, targetY)) {
			if (!empty(cell)) return false;
		}
		BlockPos below = new BlockPos(MathHelper.floor(predicted.x()), targetY, MathHelper.floor(predicted.z()));
		for (int i = 1; i < minimumHeight; i++) {
			if (!empty(below.down(i))) return false;
		}
		return true;
	}

	private boolean useful(LocalClientPlayerEntity player, HitResult target) {
		if (onlySideways && target.face.getAxis() == Direction.Axis.Y) return false;
		BlockPos cell = target.getPos().offset(target.face);
		return depths.containsKey(cell) && !player.getShape().intersects(cellBox(cell));
	}

	private Plan plan(LocalClientPlayerEntity player, List<Box> path, int targetY) {
		depths.clear();
		List<BlockPos> queue = new ObjectArrayList<>();
		for (Box shape : path) {
			for (BlockPos cell : footprint(shape, targetY)) {
				if (depths.containsKey(cell)) continue;
				depths.put(cell, 0);
				queue.add(cell);
			}
		}

		Box body = player.getShape();
		Plan best = null;
		Direction[] directions = onlySideways ? HORIZONTAL : Direction.values();
		for (int i = 0; i < queue.size(); i++) {
			BlockPos cell = queue.get(i);
			int depth = depths.getInt(cell);
			if (best != null && depth > best.depth) break;
			if (body.intersects(cellBox(cell))) continue;

			for (Direction direction : directions) {
				BlockPos next = cell.offset(direction);
				if (support(next)) {
					Plan candidate = candidate(player, cell, next, direction.getOpposite(), depth);
					if (candidate != null && (best == null || better(candidate, best))) best = candidate;
				} else if (depth + 1 < range && empty(next) && !depths.containsKey(next)) {
					depths.put(next, depth + 1);
					queue.add(next);
				}
			}
		}
		return best;
	}

	private boolean better(Plan candidate, Plan best) {
		if (retained(best)) return false;
		return retained(candidate) || candidate.angle < best.angle;
	}

	private boolean retained(Plan candidate) {
		return plan != null && candidate.support.equals(plan.support) && candidate.face == plan.face;
	}

	private Plan candidate(LocalClientPlayerEntity player, BlockPos cell, BlockPos support, Direction face, int depth) {
		Vec3d eye = player.getEyePosition(1.0F);
		Vec3d center = faceCenter(support, face);
		if ((eye.x - center.x) * face.getOffsetX() + (eye.y - center.y) * face.getOffsetY() + (eye.z - center.z) * face.getOffsetZ() <= 0.0D) return null;

		Plan plan = new Plan(cell, support, face, depth, 0.0D);
		Vec3d point = aimPoint(plan, player);
		if (eye.distanceTo(point) > minecraft.interactionManager.getReach()) return null;

		Vec3d inside = point.add(-face.getOffsetX() * 0.01D, -face.getOffsetY() * 0.01D, -face.getOffsetZ() * 0.01D);
		HitResult hit = minecraft.world.rayTrace(eye, inside);
		if (hit == null || hit.type != HitResult.Type.BLOCK || !hit.getPos().equals(support)) return null;

		float[] rotation = RotationUtils.rotationTo(point);
		if (angle(rotation, originYaw, originPitch) * 2.0D > fov) return null;
		return new Plan(cell, support, face, depth, angle(rotation, player.yaw, player.pitch));
	}

	private Vec3d aimPoint(Plan plan, LocalClientPlayerEntity player) {
		Direction face = plan.face;
		BlockPos support = plan.support;
		Vec3d center = faceCenter(support, face);
		Vec3d target = new Vec3d(face.getOffsetX() == 0 ? player.x : center.x, center.y, face.getOffsetZ() == 0 ? player.z : center.z);
		if (multipoint) {
			Vec3d eye = player.getEyePosition(1.0F);
			Vec3d look = player.getRotationVector(1.0F);
			double facing = look.x * face.getOffsetX() + look.y * face.getOffsetY() + look.z * face.getOffsetZ();
			double t = ((center.x - eye.x) * face.getOffsetX() + (center.y - eye.y) * face.getOffsetY() + (center.z - eye.z) * face.getOffsetZ()) / facing;
			if (facing < -1.0E-4D && t > 0.0D) target = eye.add(look.x * t, look.y * t, look.z * t);
		}

		double spread = randomization / 100.0F * (0.5D - FACE_INSET);
		double[] point = {target.x, target.y, target.z};
		int[] normal = {face.getOffsetX(), face.getOffsetY(), face.getOffsetZ()};
		int[] min = {support.getX(), support.getY(), support.getZ()};
		float[] offsets = {offsetU, offsetV};
		int free = 0;
		for (int axis = 0; axis < 3; axis++) {
			if (normal[axis] != 0) continue;
			point[axis] = Math.clamp(point[axis] + offsets[free++] * spread, min[axis] + FACE_INSET, min[axis] + 1.0D - FACE_INSET);
		}
		return new Vec3d(point[0], point[1], point[2]);
	}

	private void selectBlocks(PlayerInventory inventory) {
		boolean always = selectBlocks.equals("Always") && !ItemUtils.isBlock(inventory.getSelectedItem());
		boolean depleted = selectBlocks.equals("On depletion") && lastBlock != null && inventory.selectedSlot == lastSlot && inventory.getSelectedItem() == null;
		int slot = active && (always || depleted) ? ItemUtils.bestBlockSlot(inventory, lastBlock) : -1;
		if (slot != -1 && slot != inventory.selectedSlot && IllusionClient.MODULES.claimSlot(this)) {
			inventory.selectedSlot = slot;
			IllusionClient.MODULES.releaseSlot(this);
		}
		lastBlock = ItemUtils.block(inventory.getSelectedItem());
		lastSlot = inventory.selectedSlot;
	}

	private boolean empty(BlockPos pos) {
		return minecraft.world.getBlockState(pos).getBlock().getMaterial().isReplaceable();
	}

	private boolean support(BlockPos pos) {
		Block block = minecraft.world.getBlockState(pos).getBlock();
		return block.isCube() && !(block instanceof BlockWithBlockEntity) && block != Blocks.CRAFTING_TABLE;
	}

	private static List<BlockPos> footprint(Box shape, int y) {
		List<BlockPos> cells = new ObjectArrayList<>(4);
		for (int x = MathHelper.floor(shape.minX); x <= MathHelper.floor(shape.maxX - 1.0E-3D); x++) {
			for (int z = MathHelper.floor(shape.minZ); z <= MathHelper.floor(shape.maxZ - 1.0E-3D); z++) cells.add(new BlockPos(x, y, z));
		}
		return cells;
	}

	private static Box cellBox(BlockPos pos) {
		return new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
	}

	private static Vec3d faceCenter(BlockPos pos, Direction face) {
		return new Vec3d(pos.getX() + 0.5D + face.getOffsetX() * 0.5D, pos.getY() + 0.5D + face.getOffsetY() * 0.5D, pos.getZ() + 0.5D + face.getOffsetZ() * 0.5D);
	}

	private static double angle(float[] rotation, float yaw, float pitch) {
		double[] a = direction(rotation[0], rotation[1]);
		double[] b = direction(yaw, pitch);
		return Math.toDegrees(Math.acos(Math.clamp(a[0] * b[0] + a[1] * b[1] + a[2] * b[2], -1.0D, 1.0D)));
	}

	private static double[] direction(float yaw, float pitch) {
		double yawRad = Math.toRadians(yaw);
		double pitchRad = Math.toRadians(pitch);
		return new double[]{-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad)};
	}
}
