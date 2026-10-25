package net.fakebasefilter;

import net.minecraft.util.math.ChunkPos;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Oeffentliche Schnittstelle, falls ein anderer Mod/Addon die Ergebnisse nutzen will. */
public final class FakeBaseFilterApi {
    static final Map<Long, Verdict> VERDICTS = new ConcurrentHashMap<>();

    private FakeBaseFilterApi() {}

    public static boolean isFake(int chunkX, int chunkZ) {
        Verdict v = VERDICTS.get(ChunkPos.toLong(chunkX, chunkZ));
        return v != null && v.isFake();
    }

    public static boolean isRealCandidate(int chunkX, int chunkZ) {
        Verdict v = VERDICTS.get(ChunkPos.toLong(chunkX, chunkZ));
        return v != null && v.isRealCandidate();
    }

    public static Collection<Verdict> all() {
        return VERDICTS.values();
    }

    public static void clear() {
        VERDICTS.clear();
    }
}
