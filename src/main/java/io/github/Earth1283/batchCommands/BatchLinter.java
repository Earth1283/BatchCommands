package io.github.Earth1283.batchCommands;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collection;
import java.util.Set;

public class BatchLinter {

    private final BatchCommands plugin;

    public BatchLinter(BatchCommands plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks a command line for issues.
     * @param line The command line to check.
     * @param knownCommands A set of all known command aliases on the server (lowercase).
     * @return A warning message if an issue is found, or null if it's valid.
     */
    public String check(String line, Set<String> knownCommands) {
        FileConfiguration config = plugin.getLinterConfig();
        if (!config.getBoolean("enabled", true)) {
            return null;
        }

        if (line.trim().isEmpty() || line.startsWith("#") || line.toLowerCase().startsWith("!sleep")) {
            return null;
        }

        // Clean up command
        String temp = line.trim();
        if (temp.startsWith("/")) {
            temp = temp.substring(1);
        }
        String[] parts = temp.split("\\s+");
        if (parts.length == 0) return null;
        String cmdName = parts[0].toLowerCase();

        // Check 1: Existence
        if (config.getBoolean("rules.check-command-existence", true)) {
            if (!knownCommands.contains(cmdName)) {
                // It doesn't exist. Check for alternatives?
                if (config.getBoolean("rules.suggest-alternatives", true)) {
                    String suggestion = findClosestMatch(cmdName, knownCommands);
                    if (suggestion != null) {
                        return "Unknown command '" + cmdName + "'. Did you mean '" + suggestion + "'?";
                    }
                }
                return "Unknown command '" + cmdName + "'.";
            }
        }

        return null;
    }

    private String findClosestMatch(String target, Collection<String> candidates) {
        String bestMatch = null;
        int minDistance = Integer.MAX_VALUE;

        for (String candidate : candidates) {
            int distance = computeLevenshteinDistance(target, candidate);
            if (distance < minDistance) {
                minDistance = distance;
                bestMatch = candidate;
            }
        }

        // Threshold: Only suggest if distance is small enough (e.g., <= 3 or 30% of length)
        if (minDistance > 0 && minDistance <= 3) {
            return bestMatch;
        }
        return null;
    }

    // Standard Levenshtein Distance implementation
    private int computeLevenshteinDistance(String lhs, String rhs) {
        int[][] distance = new int[lhs.length() + 1][rhs.length() + 1];

        for (int i = 0; i <= lhs.length(); i++)
            distance[i][0] = i;
        for (int j = 1; j <= rhs.length(); j++)
            distance[0][j] = j;

        for (int i = 1; i <= lhs.length(); i++)
            for (int j = 1; j <= rhs.length(); j++)
                distance[i][j] = minimum(
                        distance[i - 1][j] + 1,
                        distance[i][j - 1] + 1,
                        distance[i - 1][j - 1] + ((lhs.charAt(i - 1) == rhs.charAt(j - 1)) ? 0 : 1));

        return distance[lhs.length()][rhs.length()];
    }

    private int minimum(int a, int b, int c) {
        return Math.min(Math.min(a, b), c);
    }
}
