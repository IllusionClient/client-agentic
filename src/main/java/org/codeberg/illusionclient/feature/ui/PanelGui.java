package org.codeberg.illusionclient.feature.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.client.ClickGuiModule;
import org.codeberg.illusionclient.feature.ui.component.Component;
import org.codeberg.illusionclient.feature.ui.component.KeybindComponent;
import org.codeberg.illusionclient.feature.ui.component.ModuleComponent;
import org.codeberg.illusionclient.util.stats.Statistics;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PanelGui extends ClickGui {
	private static final Logger LOGGER = LoggerFactory.getLogger(PanelGui.class);
	private static final String STATE = "panel";
	private static final int MAX_WIDTH = 460;
	private static final int MAX_HEIGHT = 290;
	private static final int SIDEBAR = 124;
	private static final int TITLE = 30;
	private static final int SEARCH = 18;
	private static final int ENTRY = 20;
	private static final int PADDING = 10;
	private static final int HEADER = 40;
	private static final int CARD = 38;
	private static final int CARD_GAP = 4;
	private static final int KEY_Y = (CARD - ModuleComponent.KEY_HEIGHT) / 2;
	private static final int LINE_GAP = 4;
	private static final int STAT = 20;
	private static final int STAT_GAP = 2;
	private static final int CHIP_HEIGHT = 16;
	private static final int SCROLL_STEP = 16;
	private static final int MAX_QUERY = 24;
	private static final int BACK_BUTTON = 3;
	private static final int FORWARD_BUTTON = 4;
	private static final float RADIUS = 8.0F;
	private static final Category[] CATEGORIES = Category.values();
	private static final String[] STAT_NAMES = {"Playtime", "Auto clicks", "Best combo", "Most toggled", "Modules on", "FPS", "Ping"};

	private final Map<Category, List<Module>> modules = new EnumMap<>(Category.class);
	private final Reference2ObjectMap<Module, List<Component>> components = new Reference2ObjectOpenHashMap<>();
	private final List<Card> cards = new ObjectArrayList<>();
	private final List<Chip> chips = new ObjectArrayList<>();
	private final List<Page> history = new ObjectArrayList<>();
	private final List<Component> visibleOptions = new ObjectArrayList<>();
	private String matchedQuery;
	private List<Module> matched = List.of();
	private final TextField search = new TextField(MAX_QUERY);
	private int x = Integer.MIN_VALUE, y, panelWidth, panelHeight;
	private int dragX, dragY;
	private boolean dragging;
	private int page;
	private Category category;
	private Module module;
	private int contentScroll, contentHeight;
	private final long[] statKeys = new long[STAT_NAMES.length];
	private final String[] statValues = new String[STAT_NAMES.length];
	private Module statModule;
	private String username, welcome;
	private int headerCount = -1, headerOn = -1, headerSize = -1;
	private String headerLabel;

	public PanelGui() {
		for (Category category : CATEGORIES) {
			modules.put(category, IllusionClient.MODULES.modules.stream().filter(module -> module.category == category).toList());
		}
		history.add(new Page(null, null));
		load();
	}

	private static boolean within(int mouseX, int mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	private boolean changed(int index, long key) {
		if (statValues[index] != null && statKeys[index] == key) return false;
		statKeys[index] = key;
		return true;
	}

	private static String duration(long millis) {
		long minutes = millis / 60000;
		return minutes < 60 ? minutes + "m " + millis / 1000 % 60 + "s" : minutes / 60 + "h " + minutes % 60 + "m";
	}

	private static String normalize(String text) {
		return text.replace(" ", "").toLowerCase(Locale.ROOT);
	}

	@Override
	protected void init() {
		panelWidth = Math.min(MAX_WIDTH, width - PADDING * 2);
		panelHeight = Math.min(MAX_HEIGHT, height - PADDING * 2);
		if (x == Integer.MIN_VALUE) {
			x = (width - panelWidth) / 2;
			y = (height - panelHeight) / 2;
		}
		clampPosition();
	}

	private void load() {
		JsonElement saved = IllusionClient.CONFIG.gui.get(STATE);
		if (saved == null) return;

		try {
			JsonObject state = saved.getAsJsonObject();
			Category savedCategory = state.has("category") ? Category.valueOf(state.get("category").getAsString()) : null;
			String moduleKey = state.has("module") ? state.get("module").getAsString() : null;
			Module savedModule = moduleKey == null ? null
				: IllusionClient.MODULES.modules.stream().filter(entry -> entry.key.equals(moduleKey)).findFirst().orElse(null);

			if (state.has("x")) {
				x = state.get("x").getAsInt();
				y = state.get("y").getAsInt();
			}
			if (savedCategory != null) {
				history.add(new Page(savedCategory, savedModule != null && savedModule.category == savedCategory ? savedModule : null));
				page = 1;
				category = history.get(page).category;
				module = history.get(page).module;
			}
		} catch (RuntimeException e) {
			LOGGER.warn("Ignoring invalid saved layout for the panel: {}", e.toString());
		}
	}

	@Override
	protected void renderScaled(int mouseX, int mouseY, float tickDelta) {
		Draw.accent();
		if (dragging) {
			x = mouseX - dragX;
			y = mouseY - dragY;
			clampPosition();
		}

		Draw.rect(0, 0, width, height, Draw.SCRIM);
		Draw.shadow(x, y + 2, panelWidth, panelHeight, RADIUS, 10.0F, Draw.PANEL_SHADOW);
		Draw.rounded(x, y, panelWidth, panelHeight, RADIUS, Draw.SURFACE);

		renderSidebar(mouseX, mouseY);
		renderHeader(mouseX, mouseY);
		renderContent(mouseX, mouseY);

		Tooltip.render(mouseX, mouseY, width, height);
		Draw.flush();
	}

	private void renderSidebar(int mouseX, int mouseY) {
		Draw.rounded(x, y, SIDEBAR, panelHeight, RADIUS, Draw.SURFACE_LOW);
		Draw.rect(x + SIDEBAR - (int) RADIUS, y, (int) RADIUS, panelHeight, Draw.SURFACE_LOW);

		Icon.LOGO.draw(x + PADDING, y + (TITLE - Icon.LOGO.height) / 2, Draw.accent().getRGB());
		Draw.text(IllusionClient.NAME, x + PADDING + 14, Draw.textY(y, TITLE), Draw.TEXT);
		Draw.rect(x + PADDING, y + TITLE - 1, SIDEBAR - PADDING * 2, 1, Draw.DIVIDER);

		renderSearch(mouseX, mouseY);

		boolean browsing = search.isEmpty();
		int rowY = listTop();
		entry(Icon.HOME, "Home", browsing && category == null, mouseX, mouseY, rowY);
		for (Category entry : CATEGORIES) {
			rowY += ENTRY;
			entry(Icon.of(entry), entry.title(), browsing && category == entry, mouseX, mouseY, rowY);
		}
	}

	private void renderSearch(int mouseX, int mouseY) {
		int boxX = searchLeft();
		int boxY = searchTop();
		int boxWidth = searchWidth();
		boolean focused = search.focused();
		boolean hovered = within(mouseX, mouseY, boxX, boxY, boxWidth, SEARCH);

		Draw.rounded(boxX, boxY, boxWidth, SEARCH, SEARCH / 2.0F, hovered && !focused ? Draw.CONTAINER_HIGHEST : Draw.CONTAINER_HIGH);
		if (focused) Draw.roundedOutline(boxX, boxY, boxWidth, SEARCH, SEARCH / 2.0F, 1.0F, Draw.accent().getRGB());

		int iconX = searchIconLeft();
		Icon.SEARCH.draw(iconX, boxY + (SEARCH - Icon.SEARCH.height) / 2, focused ? Draw.MUTED : Draw.FAINT);
		search.render(searchTextLeft(), Draw.textY(boxY, SEARCH), iconX - 6 - searchTextLeft(), "Find a module", mouseX);
	}

	private void entry(Icon icon, String name, boolean active, int mouseX, int mouseY, int rowY) {
		int left = x + 6;
		int width = SIDEBAR - 12;
		boolean hovered = within(mouseX, mouseY, left, rowY, width, ENTRY);

		float pill = (ENTRY - 2) / 2.0F;
		if (active) Draw.rounded(left, rowY + 1, width, ENTRY - 2, pill, Draw.SECONDARY_CONTAINER);
		else if (hovered) Draw.rounded(left, rowY + 1, width, ENTRY - 2, pill, Draw.CONTAINER_HIGH);

		int color = active ? Draw.ON_SECONDARY_CONTAINER : hovered ? Draw.TEXT : Draw.MUTED;
		icon.draw(left + 8, rowY + (ENTRY - icon.height) / 2, color);
		Draw.text(name, left + 8 + icon.width + 7, Draw.textY(rowY, ENTRY), color);
	}

	private void renderHeader(int mouseX, int mouseY) {
		int left = contentLeft();
		int right = left + contentWidth();
		float textY = Draw.textY(y, TITLE);

		if (!search.isEmpty()) {
			int count = matches().size();
			Draw.text("Search", left, textY, Draw.TEXT);
			if (count != headerCount || headerOn != -1) {
				headerCount = count;
				headerOn = -1;
				headerLabel = count + (count == 1 ? " result" : " results");
			}
			Draw.textRight(headerLabel, right, textY, Draw.FAINT, Draw.Weight.REGULAR);
		} else if (module != null) {
			String parent = module.category.title();
			int parentWidth = Draw.width(parent);
			Draw.text(parent, left, textY, crumbHovered(mouseX, mouseY) ? Draw.TEXT : Draw.MUTED);
			Icon.CHEVRON.draw(left + parentWidth + 6, y + (TITLE - Icon.CHEVRON.height) / 2, Draw.FAINT);
			Draw.text(module.name, left + parentWidth + 6 + Icon.CHEVRON.width + 6, textY, Draw.TEXT);
		} else if (category != null) {
			List<Module> list = modules.get(category);
			int on = enabledCount(list);
			Draw.text(category.title(), left, textY, Draw.TEXT);
			if (on != headerOn || list.size() != headerSize) {
				headerOn = on;
				headerSize = list.size();
				headerCount = -1;
				headerLabel = on + " of " + list.size() + " on";
			}
			Draw.textRight(headerLabel, right, textY, Draw.FAINT, Draw.Weight.REGULAR);
		} else {
			Draw.text("Home", left, textY, Draw.TEXT);
		}
	}

	private void renderContent(int mouseX, int mouseY) {
		int left = contentLeft();
		int top = contentTop();
		int width = contentWidth();
		int viewport = contentViewport();
		boolean inside = within(mouseX, mouseY, left, top, width, viewport);
		contentScroll = Math.clamp(contentScroll, 0, Math.max(0, contentHeight - viewport));

		cards.clear();
		chips.clear();
		scissor(left, top, width, viewport);
		int scrolled = top - contentScroll;
		if (!search.isEmpty()) contentHeight = renderSearchResults(scrolled, inside, mouseX, mouseY);
		else if (module != null) contentHeight = renderModule(module, scrolled, mouseX, mouseY);
		else if (category != null) contentHeight = renderCards(modules.get(category), scrolled, inside, mouseX, mouseY);
		else contentHeight = renderHome(scrolled, inside, mouseX, mouseY);
		endScissor();
		if (contentScroll > 0) {
			Draw.corner(left + width, top, Component.RADIUS, 0, Draw.SURFACE);
			Draw.corner(left, top, Component.RADIUS, 1, Draw.SURFACE);
		}
		if (contentScroll < contentHeight - viewport) {
			Draw.corner(left, top + viewport, Component.RADIUS, 2, Draw.SURFACE);
			Draw.corner(left + width, top + viewport, Component.RADIUS, 3, Draw.SURFACE);
		}

		if (!inside) Tooltip.hide();
	}

	private int renderSearchResults(int top, boolean inside, int mouseX, int mouseY) {
		List<Module> matches = matches();
		if (!matches.isEmpty()) return renderCards(matches, top, inside, mouseX, mouseY);

		int left = contentLeft();
		Draw.text("No modules match \"" + search.text() + "\".", left, top + 2, Draw.TEXT);
		Draw.text("Check the spelling, or try part of the name.", left, top + 14, Draw.MUTED, Draw.Weight.REGULAR);
		return 22;
	}

	private int renderCards(List<Module> list, int top, boolean inside, int mouseX, int mouseY) {
		int left = contentLeft();
		int width = contentWidth();
		int cardY = top;
		for (Module entry : list) {
			card(entry, left, cardY, width, inside && within(mouseX, mouseY, left, cardY, width, CARD), mouseX, mouseY);
			cards.add(new Card(entry, cardY));
			cardY += CARD + CARD_GAP;
		}
		return cardY - CARD_GAP - top;
	}

	private void card(Module entry, int left, int top, int width, boolean hovered, int mouseX, int mouseY) {
		boolean enabled = entry.isEnabled();
		Draw.rounded(left, top, width, CARD, Component.RADIUS, ModuleComponent.fill(entry, hovered));

		int chevronX = chevronLeft(left, width);
		boolean chevronHovered = hovered && mouseX >= chevronX - PADDING;
		Icon.CHEVRON.draw(chevronX, top + (CARD - Icon.CHEVRON.height) / 2, chevronHovered ? Draw.TEXT : hovered ? Draw.MUTED : Draw.FAINT);

		ModuleComponent.drawObsMarker(entry, chevronLeft(left, width) - PADDING, top + KEY_Y, mouseX, mouseY, PADDING);
		int textRight = keyRight(entry, left, width) - PADDING + 6;
		boolean listening = KeybindComponent.listening(entry);
		String key = ModuleComponent.keyLabel(entry, hovered, listening);
		if (key != null) {
			int keyX = keyLeft(entry, key, left, width);
			int keyWidth = keyRight(entry, left, width) - keyX;
			boolean keyHovered = hovered && within(mouseX, mouseY, keyX, top + KEY_Y, keyWidth, ModuleComponent.KEY_HEIGHT);
			ModuleComponent.drawKey(keyX, top + KEY_Y, key, listening, entry.getBind() != Keyboard.KEY_NONE, keyHovered);
			textRight = keyX - PADDING;

			if (ModuleComponent.showHold(entry, hovered)) {
				int holdX = holdLeft(keyX);
				boolean holdHovered = hovered && within(mouseX, mouseY, holdX, top + KEY_Y, keyX - 6 - holdX, ModuleComponent.KEY_HEIGHT);
				ModuleComponent.drawKey(holdX, top + KEY_Y, ModuleComponent.HOLD, false, entry.isHold(), holdHovered);
				textRight = holdX - PADDING;
			}
		}

		int textX = left + PADDING;
		int nameColor = enabled ? Draw.ON_PRIMARY_CONTAINER : Draw.TEXT;
		if (entry.description == null) {
			Draw.text(entry.name, textX, Draw.textY(top, CARD), nameColor);
			return;
		}

		String text = entry.description.value();
		String trimmed = Draw.ellipsize(text, textRight - textX, Draw.Weight.REGULAR);
		float nameY = twoLineY(top, CARD);
		Draw.text(entry.name, textX, nameY, nameColor);
		Draw.text(trimmed, textX, nameY + Draw.textHeight() + LINE_GAP, Draw.MUTED, Draw.Weight.REGULAR);
		if (hovered && !trimmed.equals(text)) Tooltip.show(entry.description);
	}

	private int renderModule(Module module, int top, int mouseX, int mouseY) {
		int left = contentLeft();
		int width = contentWidth();
		boolean hovered = within(mouseX, mouseY, left, top, width, HEADER);
		boolean enabled = module.isEnabled();
		Draw.rounded(left, top, width, HEADER, Component.RADIUS, 0.0F, ModuleComponent.fill(module, hovered));
		float nameY = module.description == null ? Draw.textY(top, HEADER) : twoLineY(top, HEADER);
		Draw.text(module.name, left + PADDING, nameY, enabled ? Draw.ON_PRIMARY_CONTAINER : Draw.TEXT);

		int textRight = ModuleComponent.drawObsMarker(module, left + width - PADDING, top + (HEADER - ModuleComponent.KEY_HEIGHT) / 2, mouseX, mouseY, PADDING);

		if (module.description != null) {
			String text = module.description.value();
			String trimmed = Draw.ellipsize(text, textRight - left - PADDING, Draw.Weight.REGULAR);
			Draw.text(trimmed, left + PADDING, nameY + Draw.textHeight() + LINE_GAP, enabled ? Draw.ON_PRIMARY_CONTAINER : Draw.MUTED, Draw.Weight.REGULAR);
			if (hovered && !trimmed.equals(text)) Tooltip.show(module.description);
		}

		int rowY = top + HEADER;
		List<Component> visible = visibleOptions(module);
		for (Component component : visible) {
			component.place(left, rowY, width, component == visible.getLast());
			component.render(mouseX, mouseY);
			rowY += component.height();
		}
		return rowY - top;
	}

	private int renderHome(int top, boolean inside, int mouseX, int mouseY) {
		int left = contentLeft();
		int width = contentWidth();
		String name = minecraft.getSession().getUsername();
		if (!name.equals(username)) {
			username = name;
			welcome = "Welcome back, " + name;
		}
		Draw.text(welcome, left, top + 2, Draw.TEXT);
		Draw.text("Left-click toggles a module, right-click opens its settings.", left, top + 14, Draw.MUTED, Draw.Weight.REGULAR);

		List<Module> all = IllusionClient.MODULES.modules;
		int enabled = enabledCount(all);
		Statistics statistics = IllusionClient.STATS;
		int ping = statistics.ping();
		Module mostToggled = statistics.mostToggled();
		long playtime = statistics.playtime();
		long toggles = mostToggled == null ? -1 : statistics.toggles.getLong(mostToggled.key);
		int fps = Minecraft.getCurrentFps();
		String[] stats = statValues;
		if (changed(0, playtime / 1000)) stats[0] = duration(playtime);
		if (changed(1, statistics.autoClicks)) stats[1] = String.format("%,d", statistics.autoClicks);
		if (changed(2, statistics.bestCombo)) stats[2] = String.format("%,d", statistics.bestCombo);
		if (changed(3, toggles) | mostToggled != statModule) {
			statModule = mostToggled;
			stats[3] = mostToggled == null ? "None" : mostToggled.name + " (" + String.format("%,d", toggles) + ")";
		}
		if (changed(4, enabled)) stats[4] = enabled + " of " + all.size();
		if (changed(5, fps)) stats[5] = String.valueOf(fps);
		if (changed(6, ping)) stats[6] = ping < 0 ? "Offline" : ping + " ms";

		Draw.text("Session", left, top + 36, Draw.TEXT);
		int rowY = top + 50;
		for (int i = 0; i < stats.length; i++) {
			float topRadius = i == 0 ? Component.RADIUS : 0.0F;
			float bottomRadius = i == stats.length - 1 ? Component.RADIUS : 0.0F;
			Draw.rounded(left, rowY, width, STAT, topRadius, bottomRadius, Draw.CONTAINER_HIGH);
			float textY = Draw.textY(rowY, STAT);
			Draw.text(STAT_NAMES[i], left + PADDING, textY, Draw.MUTED, Draw.Weight.REGULAR);
			Draw.textRight(stats[i], left + width - PADDING, textY, Draw.TEXT);
			rowY += STAT + STAT_GAP;
		}

		int labelY = rowY + 16;
		Draw.text("Active modules", left, labelY, Draw.TEXT);

		int accent = Draw.accent().getRGB();
		int chipX = left;
		int chipY = labelY + 14;
		for (Module entry : all) {
			if (!entry.isEnabled()) continue;

			int chipWidth = Draw.width(entry.name) + 22;
			if (chipX + chipWidth > left + width && chipX > left) {
				chipX = left;
				chipY += CHIP_HEIGHT + 4;
			}

			boolean hovered = inside && within(mouseX, mouseY, chipX, chipY, chipWidth, CHIP_HEIGHT);
			Draw.rounded(chipX, chipY, chipWidth, CHIP_HEIGHT, CHIP_HEIGHT / 2.0F, hovered ? Draw.PRIMARY_CONTAINER : Draw.SECONDARY_CONTAINER);
			Draw.rounded(chipX + 7, chipY + CHIP_HEIGHT / 2 - 2, 4, 4, 2.0F, accent);
			Draw.text(entry.name, chipX + 15, Draw.textY(chipY, CHIP_HEIGHT), hovered ? Draw.ON_PRIMARY_CONTAINER : Draw.ON_SECONDARY_CONTAINER);
			chips.add(new Chip(entry, chipX, chipY, chipWidth));
			chipX += chipWidth + 4;
		}
		if (chips.isEmpty()) Draw.text("Nothing is on. Turn modules on from their category.", left, Draw.textY(chipY, CHIP_HEIGHT), Draw.MUTED, Draw.Weight.REGULAR);

		return chipY + CHIP_HEIGHT - top;
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		if (KeybindComponent.mouseBound(button)) return;
		if (button == BACK_BUTTON) {
			back();
			return;
		}
		if (button == FORWARD_BUTTON) {
			forward();
			return;
		}

		if (within(mouseX, mouseY, searchLeft(), searchTop(), searchWidth(), SEARCH)) {
			if (button == 0) search.mouseClicked(mouseX, searchTextLeft());
			else search.focus();
			return;
		}
		search.blur();

		if (module != null && search.isEmpty() && crumbHovered(mouseX, mouseY)) {
			show(module.category);
			return;
		}

		if (within(mouseX, mouseY, x, y, panelWidth, TITLE)) {
			if (button == 0) {
				dragging = true;
				dragX = mouseX - x;
				dragY = mouseY - y;
			}
			return;
		}

		if (within(mouseX, mouseY, x, listTop(), SIDEBAR, ENTRY * (CATEGORIES.length + 1))) {
			int index = (mouseY - listTop()) / ENTRY;
			show(index == 0 ? null : CATEGORIES[index - 1]);
			return;
		}

		if (!within(mouseX, mouseY, contentLeft(), contentTop(), contentWidth(), contentViewport())) return;

		if (!search.isEmpty() || module == null && category != null) {
			for (Card card : cards) {
				if (!within(mouseX, mouseY, contentLeft(), card.y, contentWidth(), CARD)) continue;

				String key = ModuleComponent.keyLabel(card.module, true, false);
				int keyX = keyLeft(card.module, key, contentLeft(), contentWidth());
				boolean onKey = within(mouseX, mouseY, keyX, card.y + KEY_Y, keyRight(card.module, contentLeft(), contentWidth()) - keyX, ModuleComponent.KEY_HEIGHT);
				int holdX = holdLeft(keyX);
				boolean onHold = ModuleComponent.showHold(card.module, true) && within(mouseX, mouseY, holdX, card.y + KEY_Y, keyX - 6 - holdX, ModuleComponent.KEY_HEIGHT);
				boolean onChevron = mouseX >= chevronLeft(contentLeft(), contentWidth()) - PADDING;
				if (button == 2 || button == 0 && onKey) KeybindComponent.listen(card.module);
				else if (button == 0 && onHold) card.module.setHold(!card.module.isHold());
				else if (button == 1 || button == 0 && (onChevron || card.module instanceof ClickGuiModule)) open(card.module);
				else if (button == 0) card.module.toggle();
				return;
			}
		} else if (module == null) {
			for (Chip chip : chips) {
				if (within(mouseX, mouseY, chip.x, chip.y, chip.width, CHIP_HEIGHT)) {
					open(chip.module);
					return;
				}
			}
		} else if (within(mouseX, mouseY, contentLeft(), contentTop() - contentScroll, contentWidth(), HEADER)) {
			if (button == 0 && !(module instanceof ClickGuiModule)) module.toggle();
		} else {
			for (Component component : visibleOptions(module)) component.mouseClicked(mouseX, mouseY, button);
		}
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int button) {
		release();
	}

	@Override
	protected void scrolled(int mouseX, int direction) {
		if (mouseX >= x + SIDEBAR) contentScroll -= direction * SCROLL_STEP;
	}

	@Override
	protected void keyPressed(char chr, int key) {
		if (KeybindComponent.keyBound(key)) return;

		if (module != null && search.isEmpty()) {
			for (Component component : visibleOptions(module)) {
				if (component.keyPressed(key)) return;
			}
		}

		if (key == Keyboard.KEY_ESCAPE) {
			if (search.focused() || !search.isEmpty()) clearSearch();
			else super.keyPressed(chr, key);
			return;
		}

		if (search.focused()) {
			if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
				List<Module> matches = search.isEmpty() ? List.of() : matches();
				if (!matches.isEmpty()) open(matches.getFirst());
				return;
			}

			String before = search.text();
			if (search.keyPressed(chr, key)) {
				if (!before.equals(search.text())) contentScroll = 0;
				return;
			}
		}

		if (Screen.isControlDown() && (key == Keyboard.KEY_F || key == Keyboard.KEY_E)) {
			search.focus();
			search.keyPressed('\0', Keyboard.KEY_A);
		} else if (Screen.isAltDown() && key == Keyboard.KEY_LEFT || key == Keyboard.KEY_BACK) {
			back();
		} else if (Screen.isAltDown() && key == Keyboard.KEY_RIGHT) {
			forward();
		} else if (chr > ' ' && chr <= '~' && !Screen.isControlDown()) {
			search.focus();
			search.keyPressed(chr, key);
			contentScroll = 0;
		} else {
			super.keyPressed(chr, key);
		}
	}

	@Override
	protected boolean capturesBind() {
		return search.focused();
	}

	@Override
	protected void removed() {
		release();
		save();
	}

	public void resetPosition() {
		x = Integer.MIN_VALUE;
		if (isCurrent()) init();
		save();
	}

	private void save() {
		JsonObject state = new JsonObject();
		if (x != Integer.MIN_VALUE) {
			state.addProperty("x", x);
			state.addProperty("y", y);
		}
		if (category != null) state.addProperty("category", category.name());
		if (module != null) state.addProperty("module", module.key);
		IllusionClient.CONFIG.gui.add(STATE, state);
		IllusionClient.CONFIG.save();
	}

	private void show(Category category) {
		navigate(new Page(category, null));
	}

	private void open(Module module) {
		navigate(new Page(module.category, module));
	}

	private void navigate(Page target) {
		clearSearch();
		if (target.equals(history.get(page))) return;

		history.subList(page + 1, history.size()).clear();
		history.add(target);
		page++;
		apply();
	}

	private void back() {
		if (!search.isEmpty()) {
			clearSearch();
		} else if (page > 0) {
			page--;
			apply();
		}
	}

	private void forward() {
		if (page >= history.size() - 1) return;

		clearSearch();
		page++;
		apply();
	}

	private void apply() {
		release();
		KeybindComponent.stopListening();
		category = history.get(page).category;
		module = history.get(page).module;
		contentScroll = 0;
	}

	private void clearSearch() {
		if (!search.isEmpty()) contentScroll = 0;
		search.clear();
	}

	private void release() {
		dragging = false;
		search.mouseReleased();
		if (module == null) return;

		for (Component component : options(module)) component.mouseReleased();
	}

	private static int enabledCount(List<Module> modules) {
		int count = 0;
		for (Module module : modules) {
			if (module.isEnabled()) count++;
		}
		return count;
	}

	private List<Module> matches() {
		if (search.text().equals(matchedQuery)) return matched;

		matchedQuery = search.text();
		String needle = normalize(matchedQuery);
		matched = IllusionClient.MODULES.modules.stream()
			.filter(entry -> rank(entry, needle) < 3)
			.sorted(Comparator.comparingInt(entry -> rank(entry, needle)))
			.toList();
		return matched;
	}

	private int rank(Module entry, String needle) {
		String name = normalize(entry.name);
		if (name.startsWith(needle)) return 0;
		if (name.contains(needle)) return 1;
		if (entry.description != null && normalize(entry.description.value()).contains(needle)) return 2;
		return 3;
	}

	private List<Component> options(Module module) {
		return components.computeIfAbsent(module, ModuleComponent::options);
	}

	private List<Component> visibleOptions(Module module) {
		visibleOptions.clear();
		for (Component component : options(module)) {
			if (component.visible()) visibleOptions.add(component);
		}
		return visibleOptions;
	}

	private boolean crumbHovered(int mouseX, int mouseY) {
		return within(mouseX, mouseY, contentLeft(), y + 6, Draw.width(module.category.title()), TITLE - 12);
	}

	private void clampPosition() {
		x = Math.clamp(x, 0, width - panelWidth);
		y = Math.clamp(y, 0, height - panelHeight);
	}

	private void scissor(int left, int top, int width, int height) {
		Draw.flush();
		int scale = Math.round(minecraft.width / (float) this.width);
		GL11.glEnable(GL11.GL_SCISSOR_TEST);
		GL11.glScissor(left * scale, minecraft.height - (top + height) * scale, width * scale, height * scale);
	}

	private void endScissor() {
		Draw.flush();
		GL11.glDisable(GL11.GL_SCISSOR_TEST);
	}

	private static float twoLineY(int top, int height) {
		return Draw.textY(top, height) - (Draw.textHeight() + LINE_GAP) / 2.0F;
	}

	private int keyRight(Module module, int left, int width) {
		return chevronLeft(left, width) - PADDING - ModuleComponent.obsMarkerSpace(module, PADDING);
	}

	private int keyLeft(Module module, String label, int left, int width) {
		return keyRight(module, left, width) - Draw.width(label) - 10;
	}

	private int holdLeft(int keyLeft) {
		return keyLeft - 6 - Draw.width(ModuleComponent.HOLD) - 10;
	}

	private int chevronLeft(int left, int width) {
		return left + width - PADDING - Icon.CHEVRON.width;
	}

	private int searchLeft() {
		return x + 6;
	}

	private int searchTop() {
		return y + TITLE + 6;
	}

	private int searchWidth() {
		return SIDEBAR - 12;
	}

	private int searchTextLeft() {
		return searchLeft() + 6;
	}

	private int searchIconLeft() {
		return searchLeft() + searchWidth() - 6 - Icon.SEARCH.width;
	}

	private int listTop() {
		return searchTop() + SEARCH + 6;
	}

	private int contentLeft() {
		return x + SIDEBAR + PADDING;
	}

	private int contentTop() {
		return y + TITLE;
	}

	private int contentWidth() {
		return panelWidth - SIDEBAR - PADDING * 2;
	}

	private int contentViewport() {
		return panelHeight - TITLE - PADDING;
	}

	private record Page(Category category, Module module) {
	}

	private record Card(Module module, int y) {
	}

	private record Chip(Module module, int x, int y, int width) {
	}
}
