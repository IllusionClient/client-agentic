package org.codeberg.illusionclient;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public record BuildData(String gitBranch, String gitHash, String fullGitHash, boolean production, String version) {
	private static final String FILE = "/ILLUSION_BUILD_DATA";
	private static final BuildData UNKNOWN = new BuildData("unknown", "unknown", "unknown", false, "unknown");

	public static final BuildData CURRENT = load();

	public String displayVersion() {
		return production ? version : version + "+" + gitHash;
	}

	private static BuildData load() {
		try (InputStream in = BuildData.class.getResourceAsStream(FILE)) {
			if (in == null) return UNKNOWN;
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			return new BuildData(
				json.get("gitBranch").getAsString(),
				json.get("gitHash").getAsString(),
				json.get("fullGitHash").getAsString(),
				json.get("production").getAsBoolean(),
				json.get("version").getAsString()
			);
		} catch (IOException | RuntimeException e) {
			IllusionClient.LOGGER.error("Failed to read {}", FILE, e);
			return UNKNOWN;
		}
	}
}
