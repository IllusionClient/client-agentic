package org.codeberg.illusionclient.event.world;

import net.minecraft.world.chunk.WorldChunk;
import org.codeberg.illusionclient.event.Event;

public final class ChunkLoadEvent implements Event {
	private static final ChunkLoadEvent INSTANCE = new ChunkLoadEvent();
	private WorldChunk chunk;

	private ChunkLoadEvent() {
	}

	public static ChunkLoadEvent of(WorldChunk chunk) {
		INSTANCE.chunk = chunk;
		return INSTANCE;
	}

	public WorldChunk chunk() {
		return chunk;
	}
}
