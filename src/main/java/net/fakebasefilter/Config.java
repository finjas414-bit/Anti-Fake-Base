package net.fakebasefilter;

/** Schwellwerte, per /fbf aenderbar. */
public final class Config {
    /** Ab so vielen unmoeglich platzierten Bloecken (Schienen, Teppiche, Fackeln ...) gilt der Chunk als Fake. */
    public static int floatMin = 2;
    /** Mindestanzahl Hoehlenluft-Bloecke im 7x7x7-Wuerfel um einen Container, damit er als "in Hoehle" zaehlt. */
    public static int caveAirMin = 70;
    /** Anteil der Container eines Chunks, die in einer Hoehle liegen muessen, damit der Chunk als Fake gilt. */
    public static double caveRatio = 0.6;
    /** Ticks warten nach Chunk-Load, damit Nachbarchunks da sind. */
    public static int settleTicks = 40;
    /** Max. Container, die pro Chunk auf Hoehle geprueft werden. */
    public static int maxCaveChecks = 12;
    /** Meldungen im Chat. */
    public static boolean chat = true;

    private Config() {}
}
