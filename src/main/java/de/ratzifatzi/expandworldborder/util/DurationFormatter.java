package de.ratzifatzi.expandworldborder.util;

public final class DurationFormatter {

    private DurationFormatter() {
    }

    public static String format(long totalSeconds) {
        long safe = Math.max(0L, totalSeconds);
        long hours = safe / 3600L;
        long minutes = (safe % 3600L) / 60L;
        long seconds = safe % 60L;

        if (hours > 0L) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }
}
