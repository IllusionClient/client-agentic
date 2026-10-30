package org.codeberg.illusionclient.feature.module.player;

import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;

@ModuleInfo(name = "Fast Mine", category = Category.PLAYER)
@Description("Mines blocks faster and shortens the pause after breaking one.")
public class FastMine extends Module {
	@Description("Multiplies mining speed. Vanilla servers accept up to 1.42x.")
	@Slider(value = "Speed", min = 1.0F, max = 2.0F)
	public float speed = 1.0F;

	@Description("Ticks to wait after breaking a block. Vanilla waits 5.")
	@Slider(value = "Break Delay", min = 0.0F, max = 5.0F)
	public float breakDelay = 5.0F;

	@Description("Counts the break delay down even while not mining.")
	@Check("Always Decrease Break Delay")
	public boolean alwaysDecrease = true;

	@Override
	public String details() {
		return number(speed) + "x";
	}

	private static FastMine active() {
		FastMine module = IllusionClient.MODULES.get(FastMine.class);
		return module.isEnabled() ? module : null;
	}

	public static float miningSpeed(float speed) {
		FastMine module = active();
		return module == null ? speed : speed * module.speed;
	}

	public static int breakDelay(int delay) {
		FastMine module = active();
		return module == null ? delay : Math.min(delay, Math.round(module.breakDelay));
	}

	public static boolean alwaysDecrease() {
		FastMine module = active();
		return module != null && module.alwaysDecrease;
	}
}
