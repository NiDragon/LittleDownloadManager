package com.illusionist.ldm.util;

public class StringFormatUtil {
    public static String secondsToTime(long totalSeconds) {
        long days = (totalSeconds / 3600) / 24;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (days > 0) {
            return String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    public static String bytesToString(long bytesPerSecond) {
        double kilobytes = (double)bytesPerSecond / 1000;
        double megabytes = kilobytes / 1000;
        double gigabytes = megabytes / 1000;

        if (megabytes > 999) {
            return String.format("%.1f GB/s", gigabytes);
        } else if (kilobytes > 999) {
            return String.format("%.1f MB/s", megabytes);
        } else if (bytesPerSecond > 999) {
            return String.format("%.1f KB/s", kilobytes);
        } else {
            return String.format("%.1f B/s", (double)bytesPerSecond);
        }
    }
}
