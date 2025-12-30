package io.github.Earth1283.batchCommands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FileBatchTabCompleter implements TabCompleter {

    private final BatchCommands plugin;

    public FileBatchTabCompleter(BatchCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // We only want to provide completions for the first argument
        if (args.length == 1) {
            // The list of potential completions
            List<String> completions = new ArrayList<>();

            // The list of all batch file names
            List<String> batchFileNames = new ArrayList<>();
            File[] files = plugin.getBatchesFolder().listFiles();
            String extension = plugin.getFileExtension();

            if (files != null) {
                for (File file : files) {
                    // Check if it's a file and ends with the configured extension
                    if (file.isFile() && file.getName().toLowerCase().endsWith(extension)) {
                        batchFileNames.add(file.getName());
                    }
                }
            }

            // Copy all potential completions that start with the user's input
            StringUtil.copyPartialMatches(args[0], batchFileNames, completions);

            // Sort the results alphabetically
            Collections.sort(completions);

            return completions;
        }

        // Return an empty list for any other arguments
        return Collections.emptyList();
    }
}
