package org.codeberg.illusionclient.util.combat;

import java.util.HashMap;
import java.util.Map;
import org.codeberg.illusionclient.event.api.Listen;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityEventS2CPacket;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.network.PacketReceiveEvent;
import org.codeberg.illusionclient.event.network.PacketSendEvent;
import org.codeberg.illusionclient.event.player.PlayerTickEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.util.CombatUtils;

public final class CombatManager {
	public static final int HURT_COOLDOWN = 10;
	private static final byte DEATH = 3;
	private static final int HIT_WINDOW = 10;
	private static final int CONFIRM_SLACK = 3;
	private static final int UNKNOWN = Integer.MIN_VALUE / 2;

	private final Minecraft minecraft = Minecraft.getInstance();
	private final Map<Integer, Hurt> hurts = new HashMap<>();
	private Entity target;
	private int combo;
	private int sinceAttack = Integer.MAX_VALUE;
	private int tick;

	private static final class Hurt {
		int tick = UNKNOWN;
		int unconfirmed = UNKNOWN;
		int beforeUnconfirmed = UNKNOWN;
	}

	public CombatManager() {
		IllusionClient.BUS.subscribe(this);
	}

	public int combo() {
		return combo;
	}

	public int hurtTime(Entity entity) {
		Hurt hurt = entity == null ? null : hurts.get(entity.getNetworkId());
		return hurt == null || hurt.tick == UNKNOWN ? Integer.MAX_VALUE : tick - hurt.tick;
	}

	public boolean canDamage(Entity entity) {
		return hurtTime(entity) >= HURT_COOLDOWN;
	}

	public int ticksUntilDamageable(Entity entity) {
		return Math.max(0, HURT_COOLDOWN - hurtTime(entity));
	}

	public boolean landed(Entity entity) {
		Hurt hurt = entity == null ? null : hurts.get(entity.getNetworkId());
		return hurt != null && hurt.unconfirmed == tick;
	}

	@Listen
	public void onWorldLoad(WorldLoadEvent event) {
		reset();
		hurts.clear();
	}

	@Listen
	public void onPacketSend(PacketSendEvent event) {
		if (!(event.packet() instanceof PlayerInteractEntityC2SPacket packet) || minecraft.world == null) return;
		if (packet.getAction() != PlayerInteractEntityC2SPacket.Action.ATTACK) return;

		Entity attacked = packet.getInteractTarget(minecraft.world);
		if (attacked == null) return;
		if (attacked != target) combo = 0;
		target = attacked;
		sinceAttack = 0;

		if (!canDamage(attacked)) return;
		Hurt hurt = hurts.computeIfAbsent(attacked.getNetworkId(), _ -> new Hurt());
		hurt.beforeUnconfirmed = hurt.tick;
		hurt.tick = hurt.unconfirmed = tick;
	}

	@Listen
	public void onPacketReceive(PacketReceiveEvent event) {
		Packet<?> received = event.packet();
		if (!(received instanceof EntityEventS2CPacket packet) || minecraft.world == null) return;

		Entity entity = packet.getEntity(minecraft.world);
		if (entity == null) return;

		if (packet.getEvent() == CombatUtils.HURT) hurt(entity);
		if (entity == minecraft.player) {
			if (packet.getEvent() == CombatUtils.HURT || packet.getEvent() == DEATH) combo = 0;
		} else if (entity == target) {
			if (packet.getEvent() == CombatUtils.HURT && sinceAttack <= HIT_WINDOW) combo++;
			else if (packet.getEvent() == DEATH) reset();
		}
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		if (minecraft.world == null || minecraft.player == null) {
			reset();
			hurts.clear();
			return;
		}

		tick++;
		if (sinceAttack < Integer.MAX_VALUE) sinceAttack++;
		if (target != null && target.removed) reset();

		int timeout = latency() + CONFIRM_SLACK;
		hurts.entrySet().removeIf(entry -> {
			Hurt hurt = entry.getValue();
			if (hurt.unconfirmed != UNKNOWN && tick - hurt.unconfirmed > timeout) {
				hurt.tick = hurt.beforeUnconfirmed;
				hurt.unconfirmed = UNKNOWN;
			}
			return hurt.unconfirmed == UNKNOWN && (hurt.tick == UNKNOWN || tick - hurt.tick > HURT_COOLDOWN * 2) || minecraft.world.getEntity(entry.getKey()) == null;
		});
	}

	private void hurt(Entity entity) {
		Hurt hurt = hurts.computeIfAbsent(entity.getNetworkId(), _ -> new Hurt());
		int reported = tick - latency();
		if (hurt.unconfirmed != UNKNOWN && reported - hurt.unconfirmed < HURT_COOLDOWN - CONFIRM_SLACK) {
			hurt.unconfirmed = UNKNOWN;
			return;
		}
		if (hurt.tick == UNKNOWN || reported - hurt.tick >= HURT_COOLDOWN - CONFIRM_SLACK) hurt.tick = Math.max(hurt.tick, reported);
		hurt.unconfirmed = UNKNOWN;
	}

	private int latency() {
		return Math.max(0, Math.round(IllusionClient.STATS.ping() / 50.0F));
	}

	private void reset() {
		target = null;
		combo = 0;
		sinceAttack = Integer.MAX_VALUE;
	}
}
