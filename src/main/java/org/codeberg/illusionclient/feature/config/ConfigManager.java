package org.codeberg.illusionclient.feature.config;

import com.google.gson.*;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.feature.config.api.*;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.profiles.ProfileModule;
import org.codeberg.illusionclient.feature.module.tags.TagModule;
import org.codeberg.illusionclient.feature.module.visual.Notifications;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class ConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final String VERSION_KEY = "configVersion";
	private static final String STATS_KEY = "stats";
	private static final String GUI_KEY = "gui";
	private static final String PROFILE_KEY = "profile";
	private static final String FORK_KEY = "forkProfile";
	private static final String PROFILES_KEY = "profiles";
	public static final String DEFAULT_PROFILE = "Default";
	private static final String RESERVED_CHARACTERS = "\\/:*?\"<>|";
	private static final Map<String, String> RENAMED_LABELS = Map.of("Randomize Inventory", "Randomize Inventory Speed");
	private static final List<Consumer<JsonObject>> MIGRATIONS = List.of(
		ConfigManager::keyByFieldName,
		ConfigManager::keyByClassName,
		ConfigManager::weaponsAsSelections,
		ConfigManager::forkWithoutProfile,
		ConfigManager::autoSoupAsAutoHeal,
		ConfigManager::noSlowOldNcp,
		ConfigManager::pingFixAsModule,
		ConfigManager::pauseDurationAsAttackDelay
	);
	private static final int VERSION = MIGRATIONS.size();

	private final Path file = FabricLoader.getInstance().getConfigDir()
		.resolve(IllusionClient.ID).resolve("settings.json");

	private final Path profiles = file.resolveSibling("profiles");

	private final JsonObject defaults = writeModules();
	private boolean loading;
	private JsonObject profileSettings = new JsonObject();

	public JsonObject gui = new JsonObject();
	public String profile;

	private static void keyByFieldName(JsonObject root) {
		for (Module module : IllusionClient.MODULES.modules) {
			JsonElement entry = root.get(module.name);
			if (entry == null || !entry.isJsonObject()) continue;

			JsonElement settings = entry.getAsJsonObject().get("settings");
			if (settings == null || !settings.isJsonObject()) continue;

			JsonObject labelled = settings.getAsJsonObject();
			JsonObject keyed = new JsonObject();
			for (Option option : module.options) {
				JsonElement value = labelled.get(option.name);
				if (value == null && RENAMED_LABELS.containsKey(option.name)) {
					value = labelled.get(RENAMED_LABELS.get(option.name));
				}
				if (value != null) keyed.add(option.key, value);
			}
			entry.getAsJsonObject().add("settings", keyed);
		}
	}

	private static void keyByClassName(JsonObject root) {
		Map<String, JsonElement> entries = new HashMap<>();
		for (Module module : IllusionClient.MODULES.modules) {
			JsonElement entry = root.remove(module.name);
			if (entry != null) entries.put(module.key, entry);
		}
		entries.forEach(root::add);
	}

	private static void weaponsAsSelections(JsonObject root) {
		JsonElement entry = root.get("Weapons");
		if (entry == null || !entry.isJsonObject() || !(entry.getAsJsonObject().get("settings") instanceof JsonObject settings) || !settings.has("swords")) return;

		settings.add("items", selected(settings, Map.of("swords", "Swords", "axes", "Axes", "sticks", "Sticks", "fists", "Fists")));
		settings.add("enchantments", selected(settings, Map.of("sharpness", "Sharpness", "knockback", "Knockback", "fireAspect", "Fire Aspect")));
	}

	private static void forkWithoutProfile(JsonObject root) {
		if (!root.has(PROFILE_KEY)) root.addProperty(FORK_KEY, true);
	}

	private static void noSlowOldNcp(JsonObject root) {
		if (root.get("NoSlow") instanceof JsonObject entry && entry.get("settings") instanceof JsonObject settings
			&& settings.get("mode") instanceof JsonPrimitive mode && mode.getAsString().equals("NCP")) {
			settings.addProperty("mode", "Old NCP");
		}
	}

	private static void pingFixAsModule(JsonObject root) {
		if (root.get("Backtrack") instanceof JsonObject entry && entry.get("settings") instanceof JsonObject settings
			&& settings.remove("pingFix") instanceof JsonPrimitive pingFix && pingFix.getAsBoolean()) {
			JsonObject module = new JsonObject();
			module.addProperty("enabled", true);
			root.add("PingFix", module);
		}
	}

	private static void pauseDurationAsAttackDelay(JsonObject root) {
		if (!(root.get("HitSelect") instanceof JsonObject hitSelect) || !(hitSelect.get("settings") instanceof JsonObject settings)
			|| !(settings.remove("pauseDuration") instanceof JsonPrimitive delay)) return;

		JsonObject attackDelay = new JsonObject();
		JsonObject attackSettings = new JsonObject();
		attackSettings.add("delay", delay);
		attackDelay.add("settings", attackSettings);
		root.add("AttackDelay", attackDelay);
	}

	private static void autoSoupAsAutoHeal(JsonObject root) {
		if (root.get(STATS_KEY) instanceof JsonObject stats && stats.get("toggles") instanceof JsonObject toggles && toggles.has("AutoSoup")) {
			toggles.add("AutoHeal", toggles.remove("AutoSoup"));
		}

		if (!(root.remove("AutoSoup") instanceof JsonObject entry)) return;
		root.add("AutoHeal", entry);
		if (entry.get("settings") instanceof JsonObject settings && settings.remove("item") instanceof JsonPrimitive item) {
			JsonArray items = new JsonArray();
			items.add(item);
			settings.add("items", items);
		}
	}

	private static JsonArray selected(JsonObject settings, Map<String, String> checks) {
		JsonArray selected = new JsonArray();
		checks.forEach((key, name) -> {
			JsonElement value = settings.remove(key);
			if (value != null && value.isJsonPrimitive() && value.getAsBoolean()) selected.add(name);
		});
		return selected;
	}

	private static int readVersion(JsonObject root) {
		JsonElement version = root.get(VERSION_KEY);
		if (version == null) return 0;

		int number = version.getAsInt();
		if (number < 0) throw new IllegalArgumentException("invalid " + VERSION_KEY + " " + number);
		return number;
	}

	private static void load(Module module, JsonObject entry) {
		JsonElement settings = entry.get("settings");
		if (settings != null && settings.isJsonObject()) {
			for (Option option : module.options) {
				JsonElement value = settings.getAsJsonObject().get(option.key);
				if (value != null) guarded(module, option.key, () -> option.set(read(value, option)));
			}
		}

		JsonElement bind = entry.get("bind");
		if (bind != null) {
			guarded(module, "bind", () -> {
				int key = bind.getAsInt();
				if (key < Module.mouseBind(1) || key >= Keyboard.KEYBOARD_SIZE) throw new IllegalArgumentException("unknown key " + key);
				module.setBind(key);
			});
		}

		JsonElement hold = entry.get("hold");
		if (hold != null) guarded(module, "hold", () -> module.setHold(readBoolean(hold)));

		JsonElement enabled = entry.get("enabled");
		if (enabled != null) guarded(module, "enabled", () -> module.setEnabled(readBoolean(enabled)));
	}

	private static void loadStats(JsonObject stats) {
		IllusionClient.STATS.previousPlaytime = readCount(stats, "playtime");
		IllusionClient.STATS.autoClicks = readCount(stats, "autoClicks");
		IllusionClient.STATS.bestCombo = readCount(stats, "bestCombo");
		readCounts(stats, "toggles", IllusionClient.STATS.toggles);
	}

	private static void readCounts(JsonObject stats, String key, Object2LongMap<String> counts) {
		JsonElement value = stats.get(key);
		if (value == null || !value.isJsonObject()) return;

		JsonObject object = value.getAsJsonObject();
		for (String entry : object.keySet()) counts.put(entry, readCount(object, entry));
	}

	private static JsonObject writeCounts(Object2LongMap<String> counts) {
		JsonObject object = new JsonObject();
		for (Object2LongMap.Entry<String> entry : counts.object2LongEntrySet()) object.addProperty(entry.getKey(), entry.getLongValue());
		return object;
	}

	private static long readCount(JsonObject stats, String key) {
		JsonElement value = stats.get(key);
		if (value == null) return 0;

		try {
			long count = value.getAsLong();
			if (count < 0) throw new IllegalArgumentException("negative count " + count);
			return count;
		} catch (RuntimeException e) {
			LOGGER.warn("Ignoring invalid '{}' in stats: {}", key, e.toString());
			return 0;
		}
	}

	private static void guarded(Module module, String key, Runnable action) {
		try {
			action.run();
		} catch (RuntimeException e) {
			LOGGER.warn("Ignoring invalid '{}' for module {}: {}", key, module.name, e.toString());
		}
	}

	private static Object read(JsonElement value, Option option) {
		if (option.info instanceof Check) return readBoolean(value);
		if (option.info instanceof ColorPicker picker) return new Color(value.getAsInt(), picker.alpha());

		if (option.info instanceof Slider slider) {
			float number = value.getAsFloat();
			if (Float.isNaN(number)) throw new IllegalArgumentException("not a number");
			return Math.clamp(number, slider.min(), slider.max());
		}

		if (option.info instanceof RangeSlider slider) {
			JsonArray array = value.getAsJsonArray();
			if (array.size() != 2) throw new IllegalArgumentException("expected two numbers, got " + value);

			float low = array.get(0).getAsFloat();
			float high = array.get(1).getAsFloat();
			if (Float.isNaN(low) || Float.isNaN(high)) throw new IllegalArgumentException("not a number");

			low = Math.clamp(low, slider.min(), slider.max());
			high = Math.clamp(high, slider.min(), slider.max());
			return new float[]{Math.min(low, high), Math.max(low, high)};
		}

		if (option.info instanceof MultiSelect) {
			List<String> choices = Arrays.asList(option.choices());
			Set<String> selected = new LinkedHashSet<>();
			for (JsonElement element : value.getAsJsonArray()) {
				if (choices.contains(element.getAsString())) selected.add(element.getAsString());
			}
			return selected;
		}

		String choice = value.getAsString();
		if (!Arrays.asList(option.choices()).contains(choice)) {
			throw new IllegalArgumentException("unknown option " + choice);
		}
		return choice;
	}

	private static boolean readBoolean(JsonElement value) {
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
			throw new IllegalArgumentException("expected true or false, got " + value);
		}
		return value.getAsBoolean();
	}

	private static JsonObject write(Module module) {
		JsonObject settings = new JsonObject();
		for (Option option : module.options) {
			Object value = option.get();
			settings.add(option.key, value instanceof Color color
				? new JsonPrimitive(color.getRGB())
				: GSON.toJsonTree(value));
		}

		JsonObject entry = new JsonObject();
		entry.addProperty("enabled", module.isEnabled());
		entry.addProperty("bind", module.getBind());
		entry.addProperty("hold", module.isHold());
		entry.add("settings", settings);
		return entry;
	}

	private static JsonObject writeModules() {
		JsonObject root = new JsonObject();
		for (Module module : IllusionClient.MODULES.modules) {
			if (!(module instanceof ProfileModule || module instanceof TagModule)) root.add(module.key, write(module));
		}
		return root;
	}

	private void loadModules(JsonObject root, Set<String> keep) {
		loading = true;
		try {
			for (Module module : IllusionClient.MODULES.modules) {
				if (module instanceof ProfileModule || keep.contains(module.category.title())) continue;

				JsonElement entry = root.get(module.key);
				if (entry != null && entry.isJsonObject()) load(module, entry.getAsJsonObject());
			}
		} finally {
			loading = false;
		}
	}

	public synchronized void edited(Object source) {
		if (source instanceof TagModule tag) {
			tag.save();
			return;
		}
		if (profile == null && !loading && !(source instanceof Module module && module.category == Category.PROFILES)) createProfile();
	}

	private void syncProfiles() {
		List<Module> modules = IllusionClient.MODULES.modules;
		Map<String, ProfileModule> existing = new LinkedHashMap<>();
		for (Module module : modules) {
			if (module instanceof ProfileModule entry) existing.put(entry.profile, entry);
		}

		List<String> names = new ArrayList<>(profiles());
		names.addFirst(DEFAULT_PROFILE);
		if (!names.equals(List.copyOf(existing.keySet()))) {
			modules.removeIf(module -> module instanceof ProfileModule);
			for (String name : names) {
				ProfileModule module = existing.get(name);
				if (module == null) {
					module = new ProfileModule(name);
					JsonObject entry = profileSettings.get(name) instanceof JsonObject settings ? settings : new JsonObject();
					load(module, entry);
					module.created = date(entry, "created", name);
					module.updated = date(entry, "updated", name);
				}
				modules.add(module);
			}
			IllusionClient.MODULES.version++;
		}

		String active = profile == null ? DEFAULT_PROFILE : profile;
		for (Module module : modules) {
			if (module instanceof ProfileModule entry) entry.setEnabled(entry.profile.equals(active));
		}
	}

	private long date(JsonObject entry, String key, String name) {
		if (entry.get(key) instanceof JsonPrimitive value && value.isNumber()) return value.getAsLong();
		if (name.equals(DEFAULT_PROFILE)) return 0;
		try {
			return Files.getLastModifiedTime(profiles.resolve(name + ".json")).toMillis();
		} catch (IOException _) {
			return 0;
		}
	}

	private ProfileModule module(String name) {
		for (Module module : IllusionClient.MODULES.modules) {
			if (module instanceof ProfileModule entry && entry.profile.equals(name)) return entry;
		}
		return null;
	}

	private Set<String> kept(String name) {
		ProfileModule module = module(name);
		return module == null ? Set.of() : module.keep;
	}

	public synchronized void save() {
		JsonObject root = new JsonObject();
		root.addProperty(VERSION_KEY, VERSION);

		writeModules().asMap().forEach(root::add);

		profileSettings = new JsonObject();
		for (Module module : IllusionClient.MODULES.modules) {
			if (!(module instanceof ProfileModule entry)) continue;

			JsonObject settings = write(entry);
			settings.remove("enabled");
			if (entry.created > 0) settings.addProperty("created", entry.created);
			if (entry.updated > 0) settings.addProperty("updated", entry.updated);
			profileSettings.add(entry.profile, settings);
		}
		root.add(PROFILES_KEY, profileSettings);

		JsonObject stats = new JsonObject();
		stats.addProperty("playtime", IllusionClient.STATS.playtime());
		stats.addProperty("autoClicks", IllusionClient.STATS.autoClicks);
		stats.addProperty("bestCombo", IllusionClient.STATS.bestCombo);
		stats.add("toggles", writeCounts(IllusionClient.STATS.toggles));
		root.add(STATS_KEY, stats);
		root.add(GUI_KEY, gui);
		if (profile != null) root.addProperty(PROFILE_KEY, profile);
		write(file, root);
	}

	private static boolean write(Path target, JsonObject root) {
		Path temp = target.resolveSibling(target.getFileName() + ".tmp");
		try {
			Files.createDirectories(target.getParent());
			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}

			try {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException _) {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		} catch (IOException e) {
			LOGGER.error("Failed to save {}", target, e);
			return false;
		}
	}

	public List<String> profiles() {
		try (var files = Files.list(profiles)) {
			return files.map(path -> path.getFileName().toString())
				.filter(name -> name.endsWith(".json"))
				.map(name -> name.substring(0, name.length() - 5))
				.filter(name -> !name.equals(DEFAULT_PROFILE))
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();
		} catch (IOException _) {
			return List.of();
		}
	}

	public void createProfile() {
		createProfile("Profile");
	}

	public synchronized void createProfile(String prefix) {
		saveProfile(freeName(prefix));
	}

	public synchronized boolean createNamedProfile(String name) {
		if (name.isEmpty()) createProfile();
		else if (usable(name)) saveProfile(name);
		else return false;
		return true;
	}

	public synchronized boolean generateProfile(String name, String tag, JsonObject modules) {
		if (name.isEmpty()) name = freeName(tag);
		else if (!usable(name)) return false;

		if (!write(profiles.resolve(name + ".json"), modules)) return false;
		syncProfiles();
		module(name).tag = tag;
		loadProfile(name);
		return true;
	}

	public synchronized boolean renameProfile(String from, String to) {
		if (to.equals(from)) return true;
		if (from.equals(DEFAULT_PROFILE) || !usable(to)) return false;

		save();
		try {
			Files.move(profiles.resolve(from + ".json"), profiles.resolve(to + ".json"));
		} catch (IOException e) {
			LOGGER.error("Failed to rename profile {} to {}", from, to, e);
			return false;
		}
		if (profileSettings.remove(from) instanceof JsonObject entry) profileSettings.add(to, entry);
		if (from.equals(profile)) profile = to;
		syncProfiles();
		save();
		return true;
	}

	private String freeName(String prefix) {
		List<String> existing = profiles();
		int number = 1;
		while (existing.contains(prefix + " " + number)) number++;
		return prefix + " " + number;
	}

	private boolean usable(String name) {
		String problem;
		if (name.isBlank() || !name.equals(name.strip())) problem = "Profile names can't be blank or start or end with a space";
		else if (name.equalsIgnoreCase(DEFAULT_PROFILE) || name.endsWith(".") || name.chars().anyMatch(chr -> RESERVED_CHARACTERS.indexOf(chr) >= 0)) problem = "Profile names can't be Default, end with a dot or contain " + RESERVED_CHARACTERS;
		else if (profiles().stream().anyMatch(name::equalsIgnoreCase)) problem = "A profile called " + name + " already exists";
		else return true;

		Notifications.post(problem);
		return false;
	}

	public synchronized void saveProfile(String name) {
		if (!write(profiles.resolve(name + ".json"), writeModules())) return;
		profile = name;
		syncProfiles();
		module(name).updated = System.currentTimeMillis();
		save();
	}

	public JsonObject defaults() {
		return defaults.deepCopy();
	}

	public JsonObject snapshot() {
		return writeModules();
	}

	public synchronized void loadProfile(String name) {
		Set<String> keep = kept(name);
		if (name.equals(DEFAULT_PROFILE)) {
			loadModules(defaults, keep);
		} else {
			if (!profiles().contains(name)) return;

			try (Reader reader = Files.newBufferedReader(profiles.resolve(name + ".json"), StandardCharsets.UTF_8)) {
				JsonObject settings = JsonParser.parseReader(reader).getAsJsonObject();
				autoSoupAsAutoHeal(settings);
				loadModules(settings, keep);
			} catch (IOException | RuntimeException e) {
				LOGGER.error("Failed to load profile {}", name, e);
				return;
			}
		}
		profile = name.equals(DEFAULT_PROFILE) ? null : name;
		save();
		syncProfiles();
		Notifications.profileLoaded(name);
	}

	public synchronized void deleteProfile(String name) {
		try {
			Files.deleteIfExists(profiles.resolve(name + ".json"));
		} catch (IOException e) {
			LOGGER.error("Failed to delete profile {}", name, e);
			return;
		}
		profileSettings.remove(name);
		if (name.equals(profile)) loadProfile(DEFAULT_PROFILE);
		else syncProfiles();
	}

	public synchronized void load() {
		read();
		syncProfiles();
	}

	private void read() {
		if (!Files.exists(file)) return;

		JsonObject root;
		int version;
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			root = JsonParser.parseReader(reader).getAsJsonObject();
			version = readVersion(root);
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Failed to read {}, using defaults and keeping a copy at {}", file, backup("broken"), e);
			return;
		}

		if (version > VERSION) {
			LOGGER.warn("{} was written by a newer version ({} > {}), loading what still matches and keeping a copy at {}",
				file, version, VERSION, backup("v" + version));
		} else if (version < VERSION) {
			Path copy = backup("v" + version);
			try {
				for (int i = version; i < VERSION; i++) MIGRATIONS.get(i).accept(root);
			} catch (RuntimeException e) {
				LOGGER.error("Failed to migrate {} from version {}, using defaults and keeping a copy at {}", file, version, copy, e);
				return;
			}
			LOGGER.info("Migrated {} from version {} to {}, the original is at {}", file, version, VERSION, copy);
		}

		loadModules(root, Set.of());

		if (root.get(PROFILES_KEY) instanceof JsonObject entries) profileSettings = entries;

		JsonElement active = root.get(PROFILE_KEY);
		if (active != null && active.isJsonPrimitive() && profiles().contains(active.getAsString())) profile = active.getAsString();

		JsonElement stats = root.get(STATS_KEY);
		if (stats != null && stats.isJsonObject()) loadStats(stats.getAsJsonObject());

		JsonElement layout = root.get(GUI_KEY);
		if (layout != null && layout.isJsonObject()) gui = layout.getAsJsonObject();

		if (root.has(FORK_KEY)) createProfile();
		else if (version < VERSION) save();
	}

	private Path backup(String suffix) {
		Path copy = file.resolveSibling(file.getFileName() + "." + suffix);
		try {
			Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOGGER.error("Failed to back up {}", file, e);
		}
		return copy;
	}
}
