package io.github.Earth1283.batchCommands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.help.HelpTopic;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FileBatchCommand implements CommandExecutor {

    private final BatchCommands plugin;
    private final MiniMessage miniMessage;
    private final BatchLinter linter;

    private enum ActionType { COMMAND, SLEEP }

    private static class BatchAction {
        ActionType type;
        String command;
        double seconds;

        BatchAction(String command) {
            this.type = ActionType.COMMAND;
            this.command = command;
        }

        BatchAction(double seconds) {
            this.type = ActionType.SLEEP;
            this.seconds = seconds;
        }
    }

    public FileBatchCommand(BatchCommands plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.linter = new BatchLinter(plugin);
    }

    private void sendMessage(CommandSender sender, String key, TagResolver... placeholders) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String message = plugin.getConfig().getString("messages." + key, "");
        if (!message.isEmpty()) {
            Component component = miniMessage.deserialize(prefix + message, placeholders);
            sender.sendMessage(component);
        }
    }

    private boolean isDangerous(String commandLine, List<String> blacklist) {
        String temp = commandLine;
        if (temp.startsWith("/")) {
            temp = temp.substring(1);
        }
        
        String[] parts = temp.split("\\s+");
        if (parts.length == 0) return false;
        String commandName = parts[0];

        for (String blocked : blacklist) {
            if (commandName.equalsIgnoreCase(blocked)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("batchcommands.execute")) {
            sendMessage(sender, "no-permission");
            return true;
        }

        if (args.length != 1) {
            sendMessage(sender, "usage");
            return false;
        }

        String extension = plugin.getFileExtension();
        String rawFileName = args[0];
        final String finalFileName = rawFileName.toLowerCase().endsWith(extension)
                ? rawFileName
                : rawFileName + extension;

        File batchFile = new File(plugin.getBatchesFolder(), finalFileName);

        if (!batchFile.exists() || batchFile.isDirectory()) {
            sendMessage(sender, "file-not-found", Placeholder.unparsed("filename", finalFileName));
            return true;
        }

        sendMessage(sender, "execution-started", Placeholder.unparsed("filename", finalFileName));

        // Capture known commands on the main thread for thread safety
        Set<String> knownCommands = new HashSet<>();
        for (HelpTopic topic : Bukkit.getHelpMap().getHelpTopics()) {
            String name = topic.getName();
            if (name.startsWith("/")) {
                knownCommands.add(name.substring(1).toLowerCase());
            } else {
                knownCommands.add(name.toLowerCase());
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<BatchAction> batchActions = new ArrayList<>();
            List<String> linterWarnings = new ArrayList<>();
            int skippedCount = 0;
            
            boolean removeDangerous = plugin.getConfig().getBoolean("security.remove-dangerous-commands", true);
            List<String> blacklist = plugin.getConfig().getStringList("security.command-blacklist");
            
            // Linter setup
            long maxTime = plugin.getLinterConfig().getLong("execution.max-lint-time-ms", 100);
            long deadline = System.currentTimeMillis() + maxTime;
            boolean debugMode = plugin.getLinterConfig().getBoolean("execution.debug-mode", false);
            
            List<String> orderStrings = plugin.getLinterConfig().getStringList("execution.lint-order");
            List<BatchLinter.LintCheck> order = new ArrayList<>();
            for (String s : orderStrings) {
                try {
                    order.add(BatchLinter.LintCheck.valueOf(s));
                } catch (IllegalArgumentException ignored) {}
            }
            
            // Only apply defaults if the key is missing from the config entirely
            if (order.isEmpty() && !plugin.getLinterConfig().contains("execution.lint-order")) {
                order.add(BatchLinter.LintCheck.META_SYNTAX);
                order.add(BatchLinter.LintCheck.UNKNOWN_META);
                order.add(BatchLinter.LintCheck.BLACKLIST);
                order.add(BatchLinter.LintCheck.EXISTENCE);
            }
            
            boolean linterTimedOut = false;
            Map<BatchLinter.LintCheck, Long> totalTimings = new EnumMap<>(BatchLinter.LintCheck.class);

            try (BufferedReader reader = new BufferedReader(new FileReader(batchFile))) {
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    line = line.trim();
                    // Ignore empty lines and Python-style comments (#)
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        
                        // Run linter check if not timed out
                        if (!linterTimedOut) {
                            try {
                                BatchLinter.LinterResult result = linter.check(line, knownCommands, blacklist, order, deadline);
                                if (result.warning != null) {
                                    linterWarnings.add("Line " + lineNumber + ": " + result.warning);
                                }
                                
                                if (debugMode) {
                                    for (Map.Entry<BatchLinter.LintCheck, Long> entry : result.timings.entrySet()) {
                                        totalTimings.merge(entry.getKey(), entry.getValue(), Long::sum);
                                    }
                                }
                            } catch (BatchLinter.LinterTimeoutException e) {
                                linterTimedOut = true;
                                String action = plugin.getLinterConfig().getString("execution.on-timeout", "SKIP_LINTER");
                                if ("CANCEL_EXECUTION".equalsIgnoreCase(action)) {
                                    Bukkit.getScheduler().runTask(plugin, () -> 
                                        sender.sendMessage(miniMessage.deserialize("<red>Linter timed out. Execution cancelled."))
                                    );
                                    return;
                                }
                                linterWarnings.add("<yellow>Linter timed out. Skipping checks for remaining lines.");
                            }
                        }

                        // Check for sleep command
                        if (line.toLowerCase().startsWith("!sleep")) {
                            String[] parts = line.split("\\s+");
                            if (parts.length >= 2) {
                                try {
                                    double seconds = Double.parseDouble(parts[1]);
                                    batchActions.add(new BatchAction(seconds));
                                } catch (NumberFormatException e) {
                                    sendMessage(sender, "invalid-sleep", Placeholder.unparsed("line", line));
                                }
                            } else {
                                sendMessage(sender, "invalid-sleep", Placeholder.unparsed("line", line));
                            }
                            continue;
                        }

                        // Check security for normal commands
                        if (removeDangerous && isDangerous(line, blacklist)) {
                            skippedCount++;
                            continue;
                        }
                        batchActions.add(new BatchAction(line));
                    }
                }
                
                // Check for empty file warning
                if (batchActions.isEmpty() && plugin.getLinterConfig().getBoolean("enabled", true) 
                        && plugin.getLinterConfig().getBoolean("rules.warn-empty-file", true)) {
                     linterWarnings.add("File appears to be empty or contains no valid commands.");
                }
                
            } catch (IOException e) {
                sendMessage(sender, "read-error");
                plugin.getLogger().severe("Could not read batch file: " + finalFileName);
                e.printStackTrace();
                return;
            }

            final int finalSkippedCount = skippedCount;
            final List<String> finalWarnings = linterWarnings;
            final Map<BatchLinter.LintCheck, Long> finalTimings = totalTimings;

            // Start execution on the main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Show warnings first
                if (!finalWarnings.isEmpty()) {
                    sender.sendMessage(miniMessage.deserialize("<yellow><b>Linter Warnings:</b>"));
                    for (String w : finalWarnings) {
                        sender.sendMessage(miniMessage.deserialize("<yellow> - " + w));
                    }
                }
                
                // Show debug report
                if (debugMode && !finalTimings.isEmpty()) {
                    sender.sendMessage(miniMessage.deserialize("<gray><b>Linter Debug Report:</b>"));
                    for (Map.Entry<BatchLinter.LintCheck, Long> entry : finalTimings.entrySet()) {
                        double ms = entry.getValue() / 1_000_000.0;
                        sender.sendMessage(miniMessage.deserialize("<gray> - " + entry.getKey() + ": <yellow>" + String.format("%.2f", ms) + "ms"));
                    }
                }
                
                executeBatch(sender, finalFileName, batchActions, 0, 0, finalSkippedCount);
            });
        });

        return true;
    }

    private void executeBatch(CommandSender sender, String fileName, List<BatchAction> actions, int startIndex, int previouslyExecuted, int skippedCount) {
        int executed = previouslyExecuted;

        for (int i = startIndex; i < actions.size(); i++) {
            BatchAction action = actions.get(i);

            if (action.type == ActionType.SLEEP) {
                scheduleSleep(sender, fileName, actions, i + 1, executed, skippedCount, action.seconds);
                return; // Break loop, waiting for callback
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), action.command);
                executed++;
            }
        }

        // Completion
        if (skippedCount > 0) {
            sendMessage(sender, "skipped-dangerous",
                    Placeholder.unparsed("count", String.valueOf(skippedCount)),
                    Placeholder.unparsed("filename", fileName));
        }
        sendMessage(sender, "success",
                Placeholder.unparsed("count", String.valueOf(executed)),
                Placeholder.unparsed("filename", fileName));
    }

    private void scheduleSleep(CommandSender sender, String fileName, List<BatchAction> actions, int nextIndex, int executed, int skippedCount, double seconds) {
        String mode = plugin.getConfig().getString("settings.timer-mode", "ticks");

        if ("realtime".equalsIgnoreCase(mode)) {
            long delayMillis = (long) (seconds * 1000);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException ignored) {}
                Bukkit.getScheduler().runTask(plugin, () ->
                        executeBatch(sender, fileName, actions, nextIndex, executed, skippedCount)
                );
            });
        } else {
            // Default to ticks (20 ticks per second)
            long delayTicks = (long) (seconds * 20);
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    executeBatch(sender, fileName, actions, nextIndex, executed, skippedCount),
                    delayTicks
            );
        }
    }
}
