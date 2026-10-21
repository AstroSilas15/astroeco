package net.astro.eco;

/** Kuerzt Geldbetraege: 1500 -> 1.5K, 2000000 -> 2M. */
public final class Money {
    private Money() {}

    public static String format(long v) {
        if (v < 1000) return Long.toString(v);
        if (v < 1_000_000L) return unit(v, 1_000L, "K");
        if (v < 1_000_000_000L) return unit(v, 1_000_000L, "M");
        if (v < 1_000_000_000_000L) return unit(v, 1_000_000_000L, "B");
        return unit(v, 1_000_000_000_000L, "T");
    }

    private static String unit(long v, long div, String suffix) {
        long whole = v / div;
        long tenth = (v % div) / (div / 10);
        return tenth == 0 ? whole + suffix : whole + "." + tenth + suffix;
    }
}
