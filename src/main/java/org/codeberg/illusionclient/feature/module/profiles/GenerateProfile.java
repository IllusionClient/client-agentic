package org.codeberg.illusionclient.feature.module.profiles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.feature.config.api.Option;
import org.codeberg.illusionclient.feature.config.api.RangeSlider;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.combat.AimAssist;
import org.codeberg.illusionclient.feature.module.combat.AutoBlock;
import org.codeberg.illusionclient.feature.module.combat.AutoClicker;
import org.codeberg.illusionclient.feature.module.combat.Backtrack;
import org.codeberg.illusionclient.feature.module.combat.KnockbackDelay;
import org.codeberg.illusionclient.feature.module.combat.LagRange;
import org.codeberg.illusionclient.feature.module.combat.NoHitDelay;
import org.codeberg.illusionclient.feature.module.combat.SprintReset;
import org.codeberg.illusionclient.feature.module.combat.TimerRange;
import org.codeberg.illusionclient.feature.module.combat.Velocity;

import java.util.List;
import java.util.Map;

@Description("Makes a new profile with combat modules set up as legit or as blatant as you want. Your other settings carry over.")
@ModuleInfo(name = "Generate Profile", category = Category.PROFILES, bindable = false, toggleable = false)
public class GenerateProfile extends Module {
	private static final float SEMI = 1.0F / 3.0F, BLATANT = 2.0F / 3.0F;
	private static final List<Tweak> TWEAKS = List.of(
		new Tweak(AutoClicker.class, 0.0F, Map.of("targetCps", true)),
		new Tweak(AimAssist.class, 0.0F, Map.of("horizontalSpeed", true, "verticalSpeed", true, "randomization", false, "range", true, "fov", true)),
		new Tweak(SprintReset.class, 0.0F, Map.of()),
		new Tweak(Velocity.class, 0.0F, Map.of("horizontal", false, "vertical", false)),
		new Tweak(AutoBlock.class, SEMI, Map.of("range", true, "lagChance", true)),
		new Tweak(Backtrack.class, SEMI, Map.of("maximumDelay", true)),
		new Tweak(KnockbackDelay.class, SEMI, Map.of("maximumDelay", true)),
		new Tweak(LagRange.class, BLATANT, Map.of("maximumDelay", true)),
		new Tweak(TimerRange.class, BLATANT, Map.of()),
		new Tweak(NoHitDelay.class, BLATANT, Map.of())
	);

	@Description("0 keeps every combat setting at its default. 100 pushes them as far as they go and turns on the riskiest modules.")
	@Slider(value = "Blatancy (%)", min = 0.0F, max = 100.0F, step = 1.0F)
	public float blatancy = 30.0F;

	private static String tag(float level) {
		return level < SEMI ? "Closet" : level < BLATANT ? "Semi" : "Blatant";
	}

	private static JsonElement push(Option option, JsonElement value, boolean up, float level) {
		return switch (option.info) {
			case Slider slider -> new JsonPrimitive(towards(value.getAsFloat(), up ? slider.max() : slider.min(), level, slider.step()));
			case RangeSlider slider -> {
				JsonArray range = value.getAsJsonArray().deepCopy();
				int end = up ? 1 : 0;
				range.set(end, new JsonPrimitive(towards(range.get(end).getAsFloat(), up ? slider.max() : slider.min(), level, 0.1F)));
				yield range;
			}
			default -> value;
		};
	}

	private static float towards(float from, float to, float level, float step) {
		float inverse = 1.0F / step;
		return Math.round((from + (to - from) * level) * inverse) / inverse;
	}

	public boolean generate(String name) {
		float level = blatancy / 100.0F;
		JsonObject defaults = IllusionClient.CONFIG.defaults();
		JsonObject root = IllusionClient.CONFIG.snapshot();
		for (Module module : IllusionClient.MODULES.modules) {
			if (module.category == Category.COMBAT && defaults.has(module.key)) root.add(module.key, defaults.get(module.key));
		}

		for (Tweak tweak : TWEAKS) {
			Module module = IllusionClient.MODULES.get(tweak.module);
			JsonObject entry = root.getAsJsonObject(module.key);
			entry.addProperty("enabled", level >= tweak.from);
			JsonObject settings = entry.getAsJsonObject("settings");
			for (Option option : module.options) {
				Boolean up = tweak.options.get(option.key);
				if (up != null) settings.add(option.key, push(option, settings.get(option.key), up, level));
			}
		}
		return IllusionClient.CONFIG.generateProfile(name, tag(level), root);
	}

	private record Tweak(Class<? extends Module> module, float from, Map<String, Boolean> options) {
	}
}
