package org.codeberg.illusionclient.docs;

import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.feature.config.api.MultiSelect;
import org.codeberg.illusionclient.feature.config.api.Option;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.ModuleManager;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.ui.Draw;
import org.codeberg.illusionclient.util.lag.LagManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class DocsGenerator {
	static void main(String[] args) throws IOException {
		Path output = Path.of(args[0]);
		String version = args[1];
		String channel = args[2];

		IllusionClient.LAG = new LagManager();
		Map<Category, List<Module>> byCategory = new TreeMap<>();
		for (Module module : ModuleManager.builtIn()) {
			if (module.description == null) {
				throw new IllegalStateException(module.getClass().getName() + " is missing @Description");
			}
			byCategory.computeIfAbsent(module.category, _ -> new ArrayList<>()).add(module);
		}
		byCategory.values().forEach(modules -> modules.sort(Comparator.comparing(module -> module.name)));

		StringBuilder icons = new StringBuilder();
		for (String icon : List.of("logo", "search")) icons.append(icon(icon));
		StringBuilder nav = new StringBuilder();
		StringBuilder body = new StringBuilder();
		for (Map.Entry<Category, List<Module>> entry : byCategory.entrySet()) {
			Category category = entry.getKey();
			List<Module> modules = entry.getValue();
			String title = category.title();
			String id = category.name().toLowerCase(Locale.ROOT);
			icons.append(icon(id));
			nav.append("<div class=\"group\">\n<a class=\"entry category\" href=\"#").append(id).append("\"><span class=\"icon\" style=\"--icon: var(--icon-").append(id).append(")\"></span>")
				.append(title).append("<span class=\"count\">").append(modules.size()).append("</span></a>\n");
			body.append("<section class=\"category\" id=\"").append(id).append("\">\n")
				.append("<h2><span class=\"icon\" style=\"--icon: var(--icon-").append(id).append(")\"></span>").append(title).append("</h2>\n");
			for (Module module : modules) {
				nav.append("<a class=\"entry\" href=\"#").append(module.key).append("\">").append(escape(module.name)).append("</a>\n");
				module(body, module);
			}
			nav.append("</div>\n");
			body.append("</section>\n");
		}
		int count = byCategory.values().stream().mapToInt(List::size).sum();

		String page = template()
			.replace("{{colors}}", colors())
			.replace("{{icons}}", icons)
			.replace("{{count}}", String.valueOf(count))
			.replace("{{version}}", escape(version))
			.replace("{{stable}}", channel.equals("stable") ? " class=\"active\" aria-current=\"page\"" : "")
			.replace("{{nightly}}", channel.equals("nightly") ? " class=\"active\" aria-current=\"page\"" : "")
			.replace("{{nav}}", nav)
			.replace("{{body}}", body);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, page);
	}

	private static void module(StringBuilder out, Module module) {
		out.append("<article class=\"module\" id=\"").append(module.key).append("\">\n")
			.append("<header class=\"module-head\">\n<div>\n<h3><a href=\"#").append(module.key).append("\">").append(escape(module.name)).append("</a></h3>\n")
			.append(description(module.description)).append("\n</div>\n</header>\n");

		if (module.options.isEmpty()) {
			out.append("</article>\n");
			return;
		}

		out.append("<div class=\"settings\">\n");
		String section = null;
		for (Option option : module.options) {
			if (option.section() != null && !option.section().equals(section)) {
				section = option.section();
				out.append("<h4 class=\"section\">").append(escape(section)).append("</h4>\n");
			}
			String heading = section == null ? "h4" : "h5";
			out.append("<div class=\"setting\" data-search=\"").append(escape((option.name + " " + text(option.description)).toLowerCase(Locale.ROOT))).append("\">\n")
				.append("<").append(heading).append(">").append(escape(option.name)).append("</").append(heading).append(">").append(description(option.description));
			if (!option.requirements().isEmpty()) out.append("<p class=\"requires\">").append(requirements(option)).append("</p>");
			out.append("\n</div>\n");
		}
		out.append("</div>\n</article>\n");
	}

	private static String list(String[] values) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			if (i > 0) out.append(i == values.length - 1 ? " or " : ", ");
			out.append("<em>").append(escape(values[i])).append("</em>");
		}
		return out.toString();
	}

	private static String description(Description description) {
		if (description == null) return "";
		return "<p>" + escape(description.value()) + "</p>";
	}

	private static String text(Description description) {
		return description == null ? "" : description.value();
	}

	private static String requirements(Option option) {
		StringBuilder out = new StringBuilder("Shown when ");
		List<Option.Requirement> requirements = option.requirements();
		for (int i = 0; i < requirements.size(); i++) {
			if (i > 0) out.append(" and ");
			Option required = requirements.get(i).option();
			List<String> values = requirements.get(i).values();
			out.append("<strong>").append(escape(required.name)).append("</strong>");
			if (values.isEmpty()) out.append(required.info instanceof Slider ? " is above 0" : " is on");
			else out.append(required.info instanceof MultiSelect ? " includes " : " is ").append(list(values.toArray(String[]::new)));
		}
		return out.append(".").toString();
	}

	private static String colors() {
		int seed = 0xFF29E38F;
		Object[][] roles = {
			{"seed", seed}, {"seed-wash", seed & 0xFFFFFF | 0x60 << 24},
			{"surface", Draw.SURFACE}, {"surface-low", Draw.SURFACE_LOW}, {"container", Draw.CONTAINER},
			{"container-high", Draw.CONTAINER_HIGH}, {"container-highest", Draw.CONTAINER_HIGHEST},
			{"primary", Draw.PRIMARY}, {"secondary-container", Draw.SECONDARY_CONTAINER}, {"on-secondary-container", Draw.ON_SECONDARY_CONTAINER},
			{"tertiary-container", Draw.TERTIARY_CONTAINER}, {"on-tertiary-container", Draw.ON_TERTIARY_CONTAINER}, {"on-accent", Draw.ON_ACCENT},
			{"divider", Draw.DIVIDER}, {"text", Draw.TEXT}, {"muted", Draw.MUTED}, {"faint", Draw.FAINT}
		};
		StringBuilder out = new StringBuilder();
		for (Object[] role : roles) {
			int color = (int) role[1];
			int alpha = color >>> 24;
			out.append("\t--").append(role[0]).append(": ");
			if (alpha == 0xFF) out.append(String.format("#%06X", color & 0xFFFFFF));
			else out.append(String.format(Locale.ROOT, "rgb(%d %d %d / %.2f)", color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, alpha / 255.0F));
			out.append(";\n");
		}
		return out.toString();
	}

	private static String escape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	private static String icon(String name) throws IOException {
		try (InputStream in = DocsGenerator.class.getResourceAsStream("/assets/illusion/textures/gui/icons/smooth/" + name + ".png")) {
			return "--icon-" + name + ": url(\"data:image/png;base64," + Base64.getEncoder().encodeToString(in.readAllBytes()) + "\");\n";
		}
	}

	private static String template() throws IOException {
		try (InputStream in = DocsGenerator.class.getResourceAsStream("/template.html")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
