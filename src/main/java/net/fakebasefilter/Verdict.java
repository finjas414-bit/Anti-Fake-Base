package net.fakebasefilter;

import java.util.List;

/** Ergebnis der Analyse eines Chunks. */
public record Verdict(int chunkX, int chunkZ, int containers, int floating, int caveContainers,
                      int sampleX, int sampleY, int sampleZ, List<String> reasons) {

    public boolean isFake() {
        return !reasons.isEmpty();
    }

    public boolean isRealCandidate() {
        return containers > 0 && !isFake();
    }
}
