package org.codeberg.illusionclient.feature.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.ui.component.Component;
import org.codeberg.illusionclient.feature.ui.component.Frame;
import org.codeberg.illusionclient.feature.ui.component.KeybindComponent;
import org.lwjgl.input.Mouse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class FramesGui extends ClickScreen {
	private static final Logger LOGGER = LoggerFactory.getLogger(FramesGui.class);
	private static final String STATE = "frames";

	private final List<Frame> frames = new ObjectArrayList<>();

	public FramesGui() {
		for (Category category : Category.values()) frames.add(new Frame(category));

		JsonElement saved = IllusionClient.CONFIG.gui.get(STATE);
		if (saved == null || !saved.isJsonObject()) return;
		for (Frame frame : frames) {
			JsonElement entry = saved.getAsJsonObject().get(frame.category.name());
			if (entry == null) continue;
			try {
				frame.load(entry.getAsJsonObject());
			} catch (RuntimeException e) {
				LOGGER.warn("Ignoring invalid saved layout for the {} frame: {}", frame.category, e.toString());
			}
		}
	}

	@Override
	public void init() {
		int x = Component.PADDING, y = Component.PADDING, row = 0;
		for (Frame frame : frames) {
			if (!frame.placed()) {
				if (x > Component.PADDING && x + Component.WIDTH > width - Component.PADDING) {
					x = Component.PADDING;
					y += row + Component.PADDING;
					row = 0;
				}
				frame.moveTo(x, y);
				row = Math.max(row, frame.height());
				x += Component.WIDTH + Component.PADDING;
			}
			frame.clamp(width, height);
		}
		for (Frame frame : frames) relocate(frame);
	}

	public void resetPositions() {
		for (Frame frame : frames) frame.unplace();
		if (minecraft != null && minecraft.screen == this) init();
		save();
	}

	private void pushAway(Frame grown) {
		for (Frame frame : frames) {
			if (frame != grown && !frame.dragging() && frame.overlaps(grown)) relocate(frame);
		}
	}

	private void relocate(Frame frame) {
		int frameHeight = frame.height();
		if (free(frame, frame.x(), frame.y(), frameHeight)) return;

		IntList xs = new IntArrayList(), ys = new IntArrayList();
		xs.add(frame.x());
		ys.add(frame.y());
		for (Frame other : frames) {
			if (other == frame || other.dragging()) continue;
			xs.add(other.x() - frame.width() - Component.PADDING);
			xs.add(other.x() + other.width() + Component.PADDING);
			ys.add(other.y() - frameHeight - Component.PADDING);
			ys.add(other.y() + other.height() + Component.PADDING);
		}

		int maxX = Math.max(0, width - frame.width()), minY = Math.min(0, frame.y()), maxY = Math.max(0, height - Frame.TITLE);
		long best = Long.MAX_VALUE;
		int bestX = frame.x(), bestY = frame.y();
		for (int i = 0; i < xs.size(); i++) {
			int x = Math.clamp(xs.getInt(i), 0, maxX);
			for (int j = 0; j < ys.size(); j++) {
				int y = Math.clamp(ys.getInt(j), minY, maxY);
				long dx = x - frame.x(), dy = y - frame.y();
				long distance = dx * dx + dy * dy;
				if (distance < best && free(frame, x, y, frameHeight)) {
					best = distance;
					bestX = x;
					bestY = y;
				}
			}
		}
		frame.moveTo(bestX, bestY);
	}

	private boolean free(Frame frame, int x, int y, int frameHeight) {
		for (Frame other : frames) {
			if (other != frame && !other.dragging() && other.overlaps(x, y, frame.width(), frameHeight)) return false;
		}
		return true;
	}

	@Override
	protected void renderScaled(int mouseX, int mouseY, float tickDelta) {
		Draw.accent();
		Draw.rect(0, 0, width, height, Draw.SCRIM);
		renderWatermark();
		Draw.flush();

		for (Frame frame : frames) {
			if (!frame.dragging() && frame.grew()) pushAway(frame);
		}
		for (Frame frame : frames) {
			frame.render(mouseX, mouseY);
			Draw.flush();
		}

		Tooltip.render(mouseX, mouseY, width, height);
		Draw.flush();
	}

	private void renderWatermark() {
		int textX = width - Component.PADDING - Draw.width(IllusionClient.NAME);
		int y = height - Component.PADDING - Icon.LOGO.height;
		Icon.LOGO.draw(textX - 5 - Icon.LOGO.width, y, Draw.accent().getRGB());
		Draw.text(IllusionClient.NAME, textX, y, Draw.TEXT);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		if (KeybindComponent.mouseBound(button)) return;
		for (Frame frame : frames) {
			frame.mouseClicked(mouseX, mouseY, button);
		}
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int button) {
		for (Frame frame : frames) {
			boolean dropped = frame.dragging();
			frame.mouseReleased();
			if (dropped) {
				frame.clamp(width, height);
				relocate(frame);
			}
		}
	}

	@Override
	public void handleMouse() {
		super.handleMouse();

		int wheel = Mouse.getEventDWheel();
		if (wheel == 0) return;

		int mouseX = Mouse.getEventX() * width / minecraft.width;
		for (Frame frame : frames) {
			frame.scroll(mouseX, Integer.signum(wheel) * Component.HEIGHT);
		}
	}

	@Override
	protected void keyPressed(char chr, int key) {
		if (KeybindComponent.keyBound(key)) return;
		for (Frame frame : frames) {
			if (frame.keyPressed(key)) return;
		}
		super.keyPressed(chr, key);
	}

	@Override
	public void removed() {
		KeybindComponent.stopListening();
		for (Frame frame : frames) frame.mouseReleased();
		save();
	}

	private void save() {
		JsonObject state = new JsonObject();
		for (Frame frame : frames) state.add(frame.category.name(), frame.save());
		IllusionClient.CONFIG.gui.add(STATE, state);
		IllusionClient.CONFIG.save();
	}

	@Override
	public boolean shouldPauseGame() {
		return false;
	}
}
