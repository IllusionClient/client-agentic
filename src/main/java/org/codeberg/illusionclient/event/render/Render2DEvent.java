package org.codeberg.illusionclient.event.render;

import net.minecraft.client.render.Window;
import org.codeberg.illusionclient.event.Event;

public abstract class Render2DEvent implements Event {
	private Window window;

	private Render2DEvent() {
	}

	public Window window() {
		return window;
	}

	private static <T extends Render2DEvent> T set(T event, Window window) {
		Render2DEvent base = event;
		base.window = window;
		return event;
	}

	public static final class Pre extends Render2DEvent {
		private static final Pre INSTANCE = new Pre();

		public static Pre of(Window window) {
			return set(INSTANCE, window);
		}
	}

	public static final class Post extends Render2DEvent {
		private static final Post INSTANCE = new Post();

		public static Post of(Window window) {
			return set(INSTANCE, window);
		}
	}
}
