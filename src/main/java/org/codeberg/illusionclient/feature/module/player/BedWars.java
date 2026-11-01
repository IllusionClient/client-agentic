package org.codeberg.illusionclient.feature.module.player;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockWithBlockEntity;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.player.MiningCooldownEvent;
import org.codeberg.illusionclient.event.player.PlayerTickEvent;
import org.codeberg.illusionclient.event.player.SwingEvent;
import org.codeberg.illusionclient.event.player.UseItemEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Section;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.client.AntiBot;
import org.codeberg.illusionclient.feature.module.client.Teams;
import org.codeberg.illusionclient.feature.module.visual.Notifications;
import org.codeberg.illusionclient.util.ItemUtils;
import org.codeberg.illusionclient.util.bed.Bed;

import java.util.List;

@ModuleInfo(name = "Bed Wars", category = Category.PLAYER)
@Description("Warns you about threats to your bed and stops misclicks around it. Your bed is the one you spawn next to.")
public class BedWars extends Module {
	private static final int GUARD_MARGIN = 2;
	private static final long ITEM_ALERT_COOLDOWN_MS = 15_000L;
	private final Minecraft minecraft = Minecraft.getInstance();

	@Section("Alerts")
	@Description("Warns you when an enemy gets close to your bed.")
	@Check("Bed Alert")
	public boolean bedAlert = true;

	@Description("How close an enemy has to get to your bed.")
	@Requires("bedAlert")
	@Slider(value = "Alert Distance", min = 5.0F, max = 40.0F)
	public float alertDistance = 15.0F;

	@Description("Warns you when an enemy holds a fireball, TNT, an invisibility potion, obsidian or an ender pearl.")
	@Check("Item Alerts")
	public boolean itemAlerts = true;

	@Description("Plays a sound with each alert.")
	@Check("Alert Sound")
	public boolean alertSound = true;

	@Section("Misclicks")
	@Description("Stops you from breaking your own bed.")
	@Check("Protect Bed")
	public boolean protectBed = true;

	@Description("Stops non-wool blocks from going just outside your bed defense.")
	@Check("Guard Defense")
	public boolean guardDefense = true;

	@Description("How many layers your bed defense has.")
	@Slider(value = "Defense Layers", min = 1.0F, max = 5.0F)
	public float defenseLayers = 3.0F;

	private final IntSet near = new IntOpenHashSet();
	private final Int2ObjectMap<String> held = new Int2ObjectOpenHashMap<>();
	private final Object2LongMap<String> itemAlerted = new Object2LongOpenHashMap<>();

	public static boolean atDefense(BlockPos pos) {
		Bed bed = IllusionClient.BEDS.own();
		return bed != null && pos.getY() >= bed.y
			&& bed.distance(pos.getX(), pos.getY(), pos.getZ()) <= Math.round(IllusionClient.MODULES.get(BedWars.class).defenseLayers);
	}

	@Override
	public void onDisable() {
		near.clear();
		held.clear();
		itemAlerted.clear();
	}

	@Listen
	public void onWorldLoad(WorldLoadEvent event) {
		near.clear();
		held.clear();
		itemAlerted.clear();
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		if (minecraft.world == null || minecraft.player == null) return;
		Bed bed = IllusionClient.BEDS.own();
		double range = alertDistance * alertDistance;

		for (PlayerEntity player : minecraft.world.players) {
			if (player == minecraft.player || Teams.isTeammate(player) || AntiBot.isBot(player)) continue;
			int id = player.getNetworkId();

			if (bedAlert && bed != null) {
				double x = bed.centerX() - player.x;
				double y = bed.y - player.y;
				double z = bed.centerZ() - player.z;
				boolean close = x * x + y * y + z * z <= range;
				if (!close) near.remove(id);
				else if (near.add(id)) alert(player.getName() + " is near your bed (" + Math.round(Math.sqrt(x * x + y * y + z * z)) + "m)");
			}

			if (itemAlerts) {
				String item = special(player.getItemInHand());
				if (item == null) held.remove(id);
				else if (!item.equals(held.put(id, item))) {
					long now = System.currentTimeMillis();
					String key = id + item;
					if (now - itemAlerted.getLong(key) >= ITEM_ALERT_COOLDOWN_MS) {
						itemAlerted.put(key, now);
						alert(player.getName() + " is holding " + item);
					}
				}
			}
		}
	}

	private void alert(String message) {
		if (IllusionClient.MODULES.get(Notifications.class).isEnabled()) Notifications.post(message);
		else minecraft.gui.getChat().addMessage(new LiteralText("[Illusion] " + Formatting.RED + message));
		if (alertSound) minecraft.player.playSound("note.pling", 1.0F, 1.0F);
	}

	private static String special(ItemStack stack) {
		if (stack == null) return null;
		Item item = stack.getItem();
		if (item == Items.FIRE_CHARGE) return "a fireball";
		if (item == Items.ENDER_PEARL) return "an ender pearl";
		if (item == Items.POTION && invisibility(stack)) return "an invisibility potion";
		Block block = ItemUtils.block(stack);
		if (block == Blocks.TNT) return "TNT";
		if (block == Blocks.OBSIDIAN) return "obsidian";
		return null;
	}

	private static boolean invisibility(ItemStack stack) {
		List<StatusEffectInstance> effects = Items.POTION.getPotionEffects(stack);
		if (effects == null) return false;
		for (StatusEffectInstance effect : effects) {
			if (effect.getId() == StatusEffect.INVISIBILITY.id) return true;
		}
		return false;
	}

	@Listen
	public void onSwing(SwingEvent event) {
		if (aimingAtOwnBed()) event.cancelled = true;
	}

	@Listen
	public void onMiningCooldown(MiningCooldownEvent event) {
		if (aimingAtOwnBed()) event.cooldown = Math.max(event.cooldown, 1);
	}

	private boolean aimingAtOwnBed() {
		HitResult target = minecraft.crosshairTarget;
		Bed bed = IllusionClient.BEDS.own();
		if (!protectBed || bed == null || target == null || target.type != HitResult.Type.BLOCK) return false;

		BlockPos pos = target.getPos();
		return pos.getY() == bed.y && (pos.getX() == bed.x && pos.getZ() == bed.z || pos.getX() == bed.footX && pos.getZ() == bed.footZ);
	}

	@Listen
	public void onUseItem(UseItemEvent event) {
		HitResult target = minecraft.crosshairTarget;
		Bed bed = IllusionClient.BEDS.own();
		if (!guardDefense || bed == null || target == null || target.type != HitResult.Type.BLOCK) return;

		Block block = ItemUtils.block(minecraft.player.inventory.getSelectedItem());
		if (block == null || block == Blocks.WOOL) return;

		if (minecraft.world.getBlockState(target.getPos()).getBlock() instanceof BlockWithBlockEntity) return;

		BlockPos pos = target.getPos().offset(target.face);
		int layers = Math.round(defenseLayers);
		if (bed.distance(pos.getX(), pos.getY(), pos.getZ()) <= layers + GUARD_MARGIN && !atDefense(pos)) event.cancelled = true;
	}
}
