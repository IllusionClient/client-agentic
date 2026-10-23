package org.codeberg.illusionclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.codeberg.illusionclient.event.Event;
import org.codeberg.illusionclient.event.api.PubSub;
import org.codeberg.illusionclient.feature.config.ConfigManager;
import org.codeberg.illusionclient.feature.module.ModuleManager;
import org.codeberg.illusionclient.util.bed.BedManager;
import org.codeberg.illusionclient.util.combat.CombatManager;
import org.codeberg.illusionclient.util.input.InputManager;
import org.codeberg.illusionclient.util.lag.LagManager;
import org.codeberg.illusionclient.util.stats.Statistics;

public final class IllusionClient {
	public static final String ID = "illusion";
	public static final String NAME = "Illusion Client";

	public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

	public static final PubSub<Event> BUS = new PubSub<>(LOGGER::error);
	public static ModuleManager MODULES;
	public static ConfigManager CONFIG;
	public static InputManager INPUT;
	public static LagManager LAG;
	public static CombatManager COMBAT;
	public static BedManager BEDS;
	public static Statistics STATS;

	public static void init() {
		INPUT = new InputManager();
		STATS = new Statistics();
		LAG = new LagManager();
		COMBAT = new CombatManager();
		BEDS = new BedManager();
		MODULES = new ModuleManager();
		CONFIG = new ConfigManager();

		CONFIG.load();
		BUS.subscribe(STATS);
		Runtime.getRuntime().addShutdownHook(new Thread(CONFIG::save));
	}
}
