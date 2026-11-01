package org.codeberg.illusionclient.event.world;

import net.minecraft.world.chunk.WorldChunk;

public final class ChunkLoadEvent {
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
