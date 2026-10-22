package org.codeberg.illusionclient.util;

import net.fabricmc.loader.api.FabricLoader;
import org.codeberg.illusionclient.IllusionClient;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class ClickProfile {
	public static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve(IllusionClient.ID).resolve("clicks");
	private static final String EXTENSION = ".txt";

	public final List<float[]> streaks;

	public ClickProfile(List<float[]> streaks) {
		this.streaks = streaks;
	}

	public static String[] names() {
		if (!Files.isDirectory(DIRECTORY)) return new String[0];
		try (Stream<Path> files = Files.list(DIRECTORY)) {
			return files.map(file -> file.getFileName().toString())
				.filter(name -> name.endsWith(EXTENSION))
				.map(name -> name.substring(0, name.length() - EXTENSION.length()))
				.sorted()
				.toArray(String[]::new);
		} catch (IOException e) {
			IllusionClient.LOGGER.error("Failed to list click profiles in {}", DIRECTORY, e);
			return new String[0];
		}
	}

	public static ClickProfile load(String name) {
		Path file = DIRECTORY.resolve(name + EXTENSION);
		List<float[]> streaks = new ArrayList<>();
		List<Float> streak = new ArrayList<>();
		try {
			int number = 0;
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				number++;
				line = line.strip();
				if (line.startsWith("#")) continue;
				if (line.isEmpty()) {
					add(streaks, streak);
					continue;
				}

				String[] parts = line.split("\\s+");
				if (parts.length != 2) throw new IllegalArgumentException("line " + number + " needs a hold and a gap");
				for (String part : parts) {
					float ms = Float.parseFloat(part);
					if (!(ms > 0.0F && ms < 1000.0F)) throw new IllegalArgumentException("line " + number + " has " + part + "ms");
					streak.add(ms);
				}
			}
			add(streaks, streak);
		} catch (IOException | RuntimeException e) {
			IllusionClient.LOGGER.error("Failed to load click profile {}", file, e);
			return null;
		}
		return streaks.isEmpty() ? null : new ClickProfile(streaks);
	}

	private static void add(List<float[]> streaks, List<Float> streak) {
		if (streak.isEmpty()) return;
		float[] clicks = new float[streak.size()];
		for (int i = 0; i < clicks.length; i++) clicks[i] = streak.get(i);
		streaks.add(clicks);
		streak.clear();
	}

	public void save(String name) {
		Path file = DIRECTORY.resolve(name + EXTENSION);
		try {
			Files.createDirectories(DIRECTORY);
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				writer.write(String.format(Locale.ROOT, "# %d clicks at %.1f cps, one click per line as hold and gap in ms\n", clicks(), cps()));
				for (float[] streak : streaks) {
					writer.write('\n');
					for (int i = 0; i < streak.length; i += 2) {
						writer.write(String.format(Locale.ROOT, "%.1f %.1f\n", streak[i], streak[i + 1]));
					}
				}
			}
		} catch (IOException e) {
			IllusionClient.LOGGER.error("Failed to save click profile {}", file, e);
		}
	}

	public int clicks() {
		int clicks = 0;
		for (float[] streak : streaks) clicks += streak.length / 2;
		return clicks;
	}

	public float cps() {
		float ms = 0.0F;
		for (float[] streak : streaks) {
			for (float value : streak) ms += value;
		}
		return ms > 0.0F ? clicks() * 1000.0F / ms : 0.0F;
	}
}
