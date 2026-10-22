package org.codeberg.illusionclient.util.stats;

import io.github.nevalackin.radbus.Listen;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.PlayerTickEvent;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.client.ClickGuiModule;

public final class Statistics {
	private final long start = System.nanoTime();
	public final Object2LongMap<String> toggles = new Object2LongOpenHashMap<>();
	public long previousPlaytime;
	public long autoClicks;
	public long bestCombo;

	public long playtime() {
		return previousPlaytime + (System.nanoTime() - start) / 1_000_000L;
	}

	public void toggled(Module module) {
		if (!(module instanceof ClickGuiModule)) toggles.mergeLong(module.key, 1, Long::sum);
	}

	public Module mostToggled() {
		Module best = null;
		long max = 0;
		for (Module module : IllusionClient.MODULES.modules) {
			long count = toggles.getLong(module.key);
			if (count > max) {
				max = count;
				best = module;
			}
		}
		return best;
	}

	public int ping() {
		Minecraft minecraft = Minecraft.getInstance();
		ClientPlayNetworkHandler handler = minecraft.getNetworkHandler();
		if (handler == null || minecraft.player == null) return -1;

		PlayerInfo info = handler.getOnlinePlayer(minecraft.player.getUuid());
		return info == null ? -1 : info.getPing();
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		bestCombo = Math.max(bestCombo, IllusionClient.COMBAT.combo());
	}
}
