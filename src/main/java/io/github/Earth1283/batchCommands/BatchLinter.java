package io.github.Earth1283.batchCommands;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BatchLinter {

    private final BatchCommands plugin;

    public BatchLinter(BatchCommands plugin) {
        this.plugin = plugin;
    }

    public enum LintCheck {
        META_SYNTAX,
        UNKNOWN_META,
        BLACKLIST,
        EXISTENCE
    }

    public static class LinterTimeoutException extends Exception {}

    public static class LinterResult {
        public final String warning;
        public final Map<LintCheck, Long> timings;

        public LinterResult(String warning, Map<LintCheck, Long> timings) {
            this.warning = warning;
            this.timings = timings;
        }
    }

    /**
     * Checks a command line for issues.
     * @param line The command line to check.
     * @param knownCommands A set of all known command aliases on the server (lowercase).
     * @param blacklist A list of blacklisted commands.
     * @param order The order of checks to perform.
     * @param deadline The timestamp (ms) by which linting must finish.
     * @return A LinterResult containing any warning and timing data.
     * @throws LinterTimeoutException if the deadline is exceeded.
     */
    public LinterResult check(String line, Set<String> knownCommands, List<String> blacklist, List<LintCheck> order, long deadline) throws LinterTimeoutException {
        FileConfiguration config = plugin.getLinterConfig();
        Map<LintCheck, Long> timings = new EnumMap<>(LintCheck.class);

        if (!config.getBoolean("enabled", true)) {
            return new LinterResult(null, timings);
        }

        if (line.trim().isEmpty() || line.startsWith("#")) {
            return new LinterResult(null, timings);
        }

        String trimmed = line.trim();
        boolean isMeta = trimmed.startsWith("!");
        String cmdName = "";

        if (!isMeta) {
            String temp = trimmed;
            if (temp.startsWith("/")) {
                temp = temp.substring(1);
            }
            String[] parts = temp.split("\\s+");
            if (parts.length > 0) {
                cmdName = parts[0].toLowerCase();
            } else {
                return new LinterResult(null, timings);
            }
        }

        for (LintCheck check : order) {
            if (System.currentTimeMillis() > deadline) {
                throw new LinterTimeoutException();
            }

            long start = System.nanoTime();
            String warning = null;

            switch (check) {
                case META_SYNTAX:
                    if (isMeta && trimmed.toLowerCase().startsWith("!sleep")) {
                        if (config.getBoolean("rules.warn-invalid-syntax", true)) {
                            String[] parts = trimmed.split("\\s+");
                            if (parts.length < 2) {
                                warning = "Invalid syntax for !sleep. Usage: !sleep <seconds>";
                            } else {
                                try {
                                    Double.parseDouble(parts[1]);
                                } catch (NumberFormatException e) {
                                    warning = "Invalid duration for !sleep: '" + parts[1] + "'. Expected a number.";
                                }
                            }
                        }
                    }
                    break;

                case UNKNOWN_META:
                    if (isMeta && !trimmed.toLowerCase().startsWith("!sleep")) {
                        if (config.getBoolean("rules.warn-unknown-meta", true)) {
                            warning = "Unknown meta-command '" + trimmed.split("\\s+")[0] + "'. Supported: !sleep";
                        }
                    }
                    break;

                case BLACKLIST:
                    if (!isMeta && config.getBoolean("rules.warn-on-blacklisted", true)) {
                        for (String blocked : blacklist) {
                            if (cmdName.equalsIgnoreCase(blocked)) {
                                warning = "Command '" + cmdName + "' is blacklisted.";
                                break;
                            }
                        }
                    }
                    break;

                case EXISTENCE:
                    if (!isMeta && config.getBoolean("rules.check-command-existence", true)) {
                        if (!knownCommands.contains(cmdName)) {
                            if (config.getBoolean("rules.suggest-alternatives", true)) {
                                String suggestion = findClosestMatch(cmdName, knownCommands);
                                if (suggestion != null) {
                                    warning = "Unknown command '" + cmdName + "'. Did you mean '" + suggestion + "'?";
                                } else {
                                    warning = "Unknown command '" + cmdName + "'.";
                                }
                            } else {
                                warning = "Unknown command '" + cmdName + "'.";
                            }
                        }
                    }
                    break;
            }

            timings.put(check, System.nanoTime() - start);
            
            if (warning != null) {
                return new LinterResult(warning, timings);
            }
        }

        return new LinterResult(null, timings);
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
