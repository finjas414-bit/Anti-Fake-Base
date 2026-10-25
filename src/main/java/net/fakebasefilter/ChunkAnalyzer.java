package net.fakebasefilter;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.inventory.Inventory;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ChunkAnalyzer {

    private ChunkAnalyzer() {}

    /** Bloecke, die nur mit Untergrund/Wand stehen duerfen. Ist canPlaceAt false, wurde der Block "unmoeglich" gesetzt. */
    private static boolean needsSupport(BlockState s) {
        return s.isIn(BlockTags.RAILS)
                || s.isIn(BlockTags.WOOL_CARPETS)
                || s.isIn(BlockTags.PRESSURE_PLATES)
                || s.isIn(BlockTags.BUTTONS)
                || s.isIn(BlockTags.DOORS)
                || s.isOf(Blocks.TORCH) || s.isOf(Blocks.WALL_TORCH)
                || s.isOf(Blocks.REDSTONE_TORCH) || s.isOf(Blocks.REDSTONE_WALL_TORCH)
                || s.isOf(Blocks.REDSTONE_WIRE)
                || s.isOf(Blocks.REPEATER) || s.isOf(Blocks.COMPARATOR)
                || s.isOf(Blocks.LADDER);
    }

    public static Verdict analyze(ClientWorld world, WorldChunk chunk) {
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;

        // 1) Container (Kisten, Fässer, Shulker, Ofen, Trichter ...) einsammeln
        List<BlockPos> containers = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
            if (e.getValue() instanceof Inventory) containers.add(e.getKey());
        }

        // Natuerliche Strukturen (Dungeon-Spawner) ausnehmen
        containers.removeIf(p -> nearSpawner(world, p));

        List<String> reasons = new ArrayList<>();
        int sx = cx << 4, sy = 0, sz = cz << 4;
        if (!containers.isEmpty()) {
            BlockPos first = containers.get(0);
            sx = first.getX(); sy = first.getY(); sz = first.getZ();
        }

        // 2) Unmoeglich platzierte Bloecke
        int floating = 0;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        ChunkSection[] sections = chunk.getSectionArray();
        int bottomSection = chunk.getBottomSectionCoord();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection sec = sections[i];
            if (sec == null || sec.isEmpty() || !sec.hasAny(ChunkAnalyzer::needsSupport)) continue;
            int baseY = (bottomSection + i) << 4;
            for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) for (int ly = 0; ly < 16; ly++) {
                BlockState s = sec.getBlockState(lx, ly, lz);
                if (!needsSupport(s)) continue;
                pos.set((cx << 4) + lx, baseY + ly, (cz << 4) + lz);
                if (!horizontalChunksLoaded(world, pos)) continue;
                if (!s.canPlaceAt(world, pos)) {
                    floating++;
                    if (floating == 1 && containers.isEmpty()) { sx = pos.getX(); sy = pos.getY(); sz = pos.getZ(); }
                }
            }
        }

        // 3) Frei schwebende Container (rundherum nur Luft)
        int isolated = 0;
        for (BlockPos p : containers) {
            boolean allAir = true;
            for (net.minecraft.util.math.Direction d : net.minecraft.util.math.Direction.values()) {
                BlockState n = world.getBlockState(p.offset(d));
                if (!n.isAir()) { allAir = false; break; }
            }
            if (allAir) isolated++;
        }
        floating += isolated;

        if (floating >= Config.floatMin) {
            reasons.add("schwebende/unmoegliche Bloecke: " + floating);
        }

        // 4) Container mitten in einer Hoehle (viel natuerliche Hoehlenluft rundherum)
        int caveContainers = 0;
        int checked = 0;
        for (BlockPos p : containers) {
            if (checked++ >= Config.maxCaveChecks) break;
            if (caveAirAround(world, p, 3) >= Config.caveAirMin) caveContainers++;
        }
        int denom = Math.min(containers.size(), Config.maxCaveChecks);
        if (denom > 0 && (double) caveContainers / denom >= Config.caveRatio) {
            reasons.add("Container mitten in Hoehle: " + caveContainers + "/" + denom);
        }

        return new Verdict(cx, cz, containers.size(), floating, caveContainers, sx, sy, sz, List.copyOf(reasons));
    }

    private static int caveAirAround(ClientWorld world, BlockPos c, int r) {
        int n = 0;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
            if (world.getBlockState(m).isOf(Blocks.CAVE_AIR)) n++;
        }
        return n;
    }

    private static boolean nearSpawner(ClientWorld world, BlockPos c) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = -6; x <= 6; x++) for (int y = -4; y <= 4; y++) for (int z = -6; z <= 6; z++) {
            m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
            if (world.getBlockState(m).isOf(Blocks.SPAWNER)) return true;
        }
        return false;
    }

    /** Nur pruefen, wenn die 4 Nachbarchunks da sind, sonst gibt es Fehlalarme am Chunkrand. */
    private static boolean horizontalChunksLoaded(ClientWorld world, BlockPos p) {
        int lx = p.getX() & 15, lz = p.getZ() & 15;
        if (lx != 0 && lx != 15 && lz != 0 && lz != 15) return true;
        var cm = world.getChunkManager();
        return cm.isChunkLoaded((p.getX() - 1) >> 4, p.getZ() >> 4)
                && cm.isChunkLoaded((p.getX() + 1) >> 4, p.getZ() >> 4)
                && cm.isChunkLoaded(p.getX() >> 4, (p.getZ() - 1) >> 4)
                && cm.isChunkLoaded(p.getX() >> 4, (p.getZ() + 1) >> 4);
    }
}
