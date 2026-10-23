package org.codeberg.illusionclient.util.bed;

import org.codeberg.illusionclient.event.api.Listen;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.world.BlockChangeEvent;
import org.codeberg.illusionclient.event.world.ChunkLoadEvent;
import org.codeberg.illusionclient.event.world.ChunkUnloadEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.util.BlockUtils;

import java.util.Collection;
import java.util.Collections;

public final class BedManager {
	private static final int FACING_BITS = 3;
	private static final int OCCUPIED_BIT = 4;
	private static final int HEAD_BIT = 8;
	private static final int HEAD_MASK = ~(FACING_BITS | OCCUPIED_BIT);

	private final Long2ObjectMap<Bed> beds = new Long2ObjectOpenHashMap<>();
	private final Collection<Bed> view = Collections.unmodifiableCollection(beds.values());

	public BedManager() {
		IllusionClient.BUS.subscribe(this);
	}

	public Collection<Bed> beds() {
		return view;
	}

	@Listen
	public void onWorldLoad(WorldLoadEvent event) {
		beds.clear();
	}

	@Listen
	public void onChunkLoad(ChunkLoadEvent event) {
		WorldChunk chunk = event.chunk();
		beds.values().removeIf(bed -> bed.x >> 4 == chunk.chunkX && bed.z >> 4 == chunk.chunkZ);
		BlockUtils.scan(chunk, HEAD_MASK, Lookup.BED_HEAD, this::add);
		for (Bed bed : beds.values()) {
			if (Math.abs((bed.x >> 4) - chunk.chunkX) <= 1 && Math.abs((bed.z >> 4) - chunk.chunkZ) <= 1) bed.dirty = true;
		}
	}

	@Listen
	public void onChunkUnload(ChunkUnloadEvent event) {
		beds.values().removeIf(bed -> bed.x >> 4 == event.chunkX() && bed.z >> 4 == event.chunkZ());
	}

	@Listen
	public void onBlockChange(BlockChangeEvent event) {
		BlockPos pos = event.pos();
		if (isHead(BlockUtils.state(event.oldState()))) beds.remove(pos.toLong());
		int state = BlockUtils.state(event.newState());
		if (isHead(state)) add(pos.getX(), pos.getY(), pos.getZ(), state);

		for (Bed bed : beds.values()) {
			if (pos.getY() < bed.y - 1) continue;
			if (pos.getX() < Math.min(bed.x, bed.footX) - 1 || pos.getX() > Math.max(bed.x, bed.footX) + 1) continue;
			if (pos.getZ() < Math.min(bed.z, bed.footZ) - 1 || pos.getZ() > Math.max(bed.z, bed.footZ) + 1) continue;
			bed.dirty = true;
		}
	}

	private static boolean isHead(int state) {
		return (state & HEAD_MASK) == Lookup.BED_HEAD;
	}

	private void add(int x, int y, int z, int state) {
		beds.put(new BlockPos(x, y, z).toLong(), new Bed(x, y, z, BlockUtils.meta(state) & FACING_BITS));
	}

	private static final class Lookup {
		static final int BED_HEAD = BlockUtils.state(Block.getId(Blocks.BED), HEAD_BIT);
	}
}
