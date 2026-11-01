package org.codeberg.illusionclient.feature.module.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.text.LiteralText;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.input.KeyPressEvent;
import org.codeberg.illusionclient.event.network.PacketSendEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.tags.TagModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Description("Tags players. Type .tag <player> <tags> in chat, comma separated, or .tag <player> to clear.")
@ModuleInfo(name = "Tags", category = Category.CLIENT)
public class Tags extends Module {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final String FRIEND = "friend";
	private static final String COMMAND = ".tag";

	@Description("Middle click a player to toggle the friend tag.")
	@Check("Middle click friend")
	public boolean middleClick = true;

	@Description("Players with this tag are never targeted.")
	@Combo(value = "Ignore tag", source = "tagNames")
	public String ignoreTag = FRIEND;

	public static Set<String> of(PlayerEntity player) {
		return of(player.getName());
	}

	public static Set<String> of(String name) {
		return Store.TAGS.getOrDefault(name.toLowerCase(Locale.ROOT), Set.of());
	}

	public static boolean has(PlayerEntity player, String tag) {
		return of(player).contains(tag);
	}

	public static boolean isIgnored(PlayerEntity player) {
		Tags tags = IllusionClient.MODULES.get(Tags.class);
		return tags.isEnabled() && has(player, tags.ignoreTag);
	}

	public static Stream<String> names() {
		return Stream.concat(Stream.of(FRIEND), Store.TAGS.values().stream().flatMap(Set::stream)).distinct();
	}

	public String[] tagNames() {
		return Stream.concat(Stream.of("None"), names()).toArray(String[]::new);
	}

	public static void sync() {
		List<Module> modules = IllusionClient.MODULES.modules;
		Map<String, TagModule> existing = new HashMap<>();
		for (Module module : modules) {
			if (module instanceof TagModule entry) existing.put(entry.player, entry);
		}

		if (!existing.keySet().equals(Store.TAGS.keySet())) {
			modules.removeIf(module -> module instanceof TagModule);
			for (String name : Store.TAGS.keySet()) modules.add(existing.computeIfAbsent(name, TagModule::new));
			IllusionClient.MODULES.version++;
		}
		for (TagModule module : existing.values()) module.update(of(module.player));
	}

	@Override
	public String details() {
		return ignoreTag.equals("None") ? null : ignoreTag;
	}

	@Listen
	public void onKeyPress(KeyPressEvent event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!middleClick || !event.down() || event.key() != Module.mouseBind(2) || minecraft.screen != null) return;

		HitResult crosshair = minecraft.crosshairTarget;
		if (crosshair == null || !(crosshair.entity instanceof PlayerEntity player)) return;

		Set<String> tags = new TreeSet<>(of(player));
		if (!tags.remove(FRIEND)) tags.add(FRIEND);
		set(player.getName(), tags);
	}

	@Listen
	public void onPacketSend(PacketSendEvent event) {
		if (!(event.packet() instanceof ChatMessageC2SPacket chat)) return;

		String[] parts = chat.getMessage().strip().split("\\s+", 3);
		if (!parts[0].equalsIgnoreCase(COMMAND)) return;
		event.cancelled = true;

		if (parts.length < 2) {
			print("Usage: " + COMMAND + " <player> [tags, comma separated]");
			return;
		}

		Set<String> tags = parts.length < 3 ? Set.of() : Arrays.stream(parts[2].split(","))
			.map(tag -> tag.strip().toLowerCase(Locale.ROOT))
			.filter(tag -> !tag.isEmpty())
			.collect(Collectors.toCollection(TreeSet::new));
		set(parts[1], tags);
	}

	public static void set(String name, Set<String> tags) {
		if (tags.isEmpty()) Store.TAGS.remove(name.toLowerCase(Locale.ROOT));
		else Store.TAGS.put(name.toLowerCase(Locale.ROOT), tags);
		save();
		sync();
		print(tags.isEmpty() ? "Cleared tags for " + name : "Tagged " + name + ": " + String.join(", ", tags));
	}

	private static void print(String message) {
		Minecraft.getInstance().gui.getChat().addMessage(new LiteralText("[Illusion] " + message));
	}

	private static Map<String, Set<String>> load() {
		if (!Files.exists(Store.FILE)) return new TreeMap<>();
		try {
			Map<String, Set<String>> tags = GSON.fromJson(Files.readString(Store.FILE, StandardCharsets.UTF_8),
				new TypeToken<TreeMap<String, TreeSet<String>>>() {}.getType());
			return tags == null ? new TreeMap<>() : tags;
		} catch (IOException | RuntimeException e) {
			IllusionClient.LOGGER.error("Failed to load {}", Store.FILE, e);
			return new TreeMap<>();
		}
	}

	private static void save() {
		try {
			Files.createDirectories(Store.FILE.getParent());
			Files.writeString(Store.FILE, GSON.toJson(Store.TAGS), StandardCharsets.UTF_8);
		} catch (IOException e) {
			IllusionClient.LOGGER.error("Failed to save {}", Store.FILE, e);
		}
	}

	private static final class Store {
		static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(IllusionClient.ID).resolve("tags.json");
		static final Map<String, Set<String>> TAGS = load();
	}
}
