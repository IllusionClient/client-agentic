package org.codeberg.illusionclient.feature.module;

import org.codeberg.illusionclient.event.api.Listen;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.input.KeyPressEvent;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.client.AntiBot;
import org.codeberg.illusionclient.feature.module.client.ClickGuiModule;
import org.codeberg.illusionclient.feature.module.client.ObsProof;
import org.codeberg.illusionclient.feature.module.client.Tags;
import org.codeberg.illusionclient.feature.module.client.Teams;
import org.codeberg.illusionclient.feature.module.combat.AimAssist;
import org.codeberg.illusionclient.feature.module.combat.AutoArmor;
import org.codeberg.illusionclient.feature.module.combat.AutoBlock;
import org.codeberg.illusionclient.feature.module.combat.AutoClicker;
import org.codeberg.illusionclient.feature.module.combat.AutoDodge;
import org.codeberg.illusionclient.feature.module.combat.AutoLava;
import org.codeberg.illusionclient.feature.module.combat.AutoRod;
import org.codeberg.illusionclient.feature.module.combat.AutoWeapon;
import org.codeberg.illusionclient.feature.module.combat.Backtrack;
import org.codeberg.illusionclient.feature.module.combat.CombatJump;
import org.codeberg.illusionclient.feature.module.combat.Criticals;
import org.codeberg.illusionclient.feature.module.combat.HitSelect;
import org.codeberg.illusionclient.feature.module.combat.KnockbackDelay;
import org.codeberg.illusionclient.feature.module.combat.LagRange;
import org.codeberg.illusionclient.feature.module.combat.NoHitDelay;
import org.codeberg.illusionclient.feature.module.combat.SprintReset;
import org.codeberg.illusionclient.feature.module.combat.TimerRange;
import org.codeberg.illusionclient.feature.module.combat.Velocity;
import org.codeberg.illusionclient.feature.module.client.Weapons;
import org.codeberg.illusionclient.feature.module.movement.InstantStop;
import org.codeberg.illusionclient.feature.module.movement.NoJumpDelay;
import org.codeberg.illusionclient.feature.module.movement.NoSlow;
import org.codeberg.illusionclient.feature.module.movement.NullMove;
import org.codeberg.illusionclient.feature.module.movement.Sprint;
import org.codeberg.illusionclient.feature.module.movement.Timer;
import org.codeberg.illusionclient.feature.module.player.AutoExtinguish;
import org.codeberg.illusionclient.feature.module.player.AutoHeal;
import org.codeberg.illusionclient.feature.module.player.AutoTool;
import org.codeberg.illusionclient.feature.module.player.BedWars;
import org.codeberg.illusionclient.feature.module.player.Blink;
import org.codeberg.illusionclient.feature.module.player.BlockIn;
import org.codeberg.illusionclient.feature.module.player.BridgeAssist;
import org.codeberg.illusionclient.feature.module.player.ChestStealer;
import org.codeberg.illusionclient.feature.module.player.Clutch;
import org.codeberg.illusionclient.feature.module.player.FastMine;
import org.codeberg.illusionclient.feature.module.player.FastPlace;
import org.codeberg.illusionclient.feature.module.player.InventoryCleaner;
import org.codeberg.illusionclient.feature.module.player.LiquidSteal;
import org.codeberg.illusionclient.feature.module.player.Refill;
import org.codeberg.illusionclient.feature.module.profiles.CreateProfile;
import org.codeberg.illusionclient.feature.module.profiles.GenerateProfile;
import org.codeberg.illusionclient.feature.module.visual.AntiDebuff;
import org.codeberg.illusionclient.feature.module.visual.Bedplates;
import org.codeberg.illusionclient.feature.module.visual.Chams;
import org.codeberg.illusionclient.feature.module.visual.ChestEsp;
import org.codeberg.illusionclient.feature.module.visual.Esp;
import org.codeberg.illusionclient.feature.module.visual.FakeBlock;
import org.codeberg.illusionclient.feature.module.visual.Indicators;
import org.codeberg.illusionclient.feature.module.visual.ItemEsp;
import org.codeberg.illusionclient.feature.module.visual.ModOverlay;
import org.codeberg.illusionclient.feature.module.visual.Nametags;
import org.codeberg.illusionclient.feature.module.visual.NoHurtCam;
import org.codeberg.illusionclient.feature.module.visual.Notifications;
import org.codeberg.illusionclient.feature.module.visual.Pointers;
import org.codeberg.illusionclient.util.SwapHook;

import java.util.Arrays;
import java.util.List;

public final class ModuleManager {
	public final List<Module> modules = builtIn();
	private final Reference2ObjectMap<Class<?>, Module> byType = new Reference2ObjectOpenHashMap<>();
	private Module slotOwner;
	public int version;

	public ModuleManager() {
		if (SwapHook.SUPPORTED) modules.add(new ObsProof());
		for (Module module : modules) byType.put(module.getClass(), module);
		IllusionClient.BUS.subscribe(this);
	}

	public static List<Module> builtIn() {
		return new ObjectArrayList<>(Arrays.asList(
			new AntiBot(),
			new ClickGuiModule(),
			new Tags(),
			new Teams(),
			new AimAssist(),
			new AutoArmor(),
			new AutoBlock(),
			new AutoClicker(),
			new AutoDodge(),
			new AutoLava(),
			new AutoRod(),
			new AutoWeapon(),
			new Backtrack(),
			new CombatJump(),
			new Criticals(),
			new HitSelect(),
			new KnockbackDelay(),
			new LagRange(),
			new NoHitDelay(),
			new SprintReset(),
			new TimerRange(),
			new Velocity(),
			new Weapons(),
			new InstantStop(),
			new NoJumpDelay(),
			new NoSlow(),
			new NullMove(),
			new Sprint(),
			new Timer(),
			new AutoExtinguish(),
			new AutoHeal(),
			new AutoTool(),
			new BedWars(),
			new Blink(),
			new BlockIn(),
			new BridgeAssist(),
			new ChestStealer(),
			new Clutch(),
			new FastMine(),
			new FastPlace(),
			new InventoryCleaner(),
			new LiquidSteal(),
			new Refill(),
			new AntiDebuff(),
			new Bedplates(),
			new Chams(),
			new ChestEsp(),
			new Esp(),
			new FakeBlock(),
			new Indicators(),
			new ItemEsp(),
			new ModOverlay(),
			new Nametags(),
			new NoHurtCam(),
			new Pointers(),
			new CreateProfile(),
			new GenerateProfile(),
			new Notifications()
		));
	}

	public <T extends Module> T get(Class<T> type) {
		Module module = byType.get(type);
		if (module == null) throw new IllegalArgumentException("Unregistered module " + type.getName());
		return type.cast(module);
	}

	public boolean claimSlot(Module module) {
		if (slotOwner != null && slotOwner != module) return false;
		slotOwner = module;
		return true;
	}

	public boolean slotClaimed() {
		return slotOwner != null;
	}

	public void releaseSlot(Module module) {
		if (slotOwner == module) slotOwner = null;
	}

	@Listen
	public void onKeyPress(KeyPressEvent event) {
		if (event.key() == 0) return;

		if (Minecraft.getInstance().screen != null && event.down()) return;

		for (Module module : List.copyOf(modules)) {
			if (module.getBind() != event.key()) continue;

			if (module.isHold()) {
				if (module.isEnabled() != event.down()) module.toggle();
			} else if (event.down()) {
				module.toggle();
			}
		}
	}
}
