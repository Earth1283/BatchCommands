package io.github.Earth1283.batchCommands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileBatchCommand implements CommandExecutor {

    private final BatchCommands plugin;
    private final MiniMessage miniMessage;

    public FileBatchCommand(BatchCommands plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    private void sendMessage(CommandSender sender, String key, TagResolver... placeholders) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String message = plugin.getConfig().getString("messages." + key, "");
        if (!message.isEmpty()) {
            Component component = miniMessage.deserialize(prefix + message, placeholders);
            sender.sendMessage(component);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check for the correct permission
        if (!sender.hasPermission("batchcommands.execute")) {
            sendMessage(sender, "no-permission");
            return true;
        }

        // Check for the correct number of arguments
        if (args.length != 1) {
            sendMessage(sender, "usage");
            return false; // Returning false shows the usage message from plugin.yml
        }

        String extension = plugin.getFileExtension();
        String rawFileName = args[0];
        final String finalFileName = rawFileName.toLowerCase().endsWith(extension)
                ? rawFileName
                : rawFileName + extension;

        File batchFile = new File(plugin.getBatchesFolder(), finalFileName);

        // Check if the file exists and is not a directory
        if (!batchFile.exists() || batchFile.isDirectory()) {
            sendMessage(sender, "file-not-found", Placeholder.unparsed("filename", finalFileName));
            return true;
        }

        sendMessage(sender, "execution-started", Placeholder.unparsed("filename", finalFileName));

        // Run the file reading and command execution asynchronously to avoid lagging the server
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> commandsToExecute = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(batchFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Trim whitespace and ignore empty lines or comments (lines starting with #)
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        commandsToExecute.add(line);
                    }
                }
            } catch (IOException e) {
                // Inform the sender and log the error if file reading fails
                sendMessage(sender, "read-error");
                plugin.getLogger().severe("Could not read batch file: " + finalFileName);
                e.printStackTrace();
                return; // Stop execution
            }

            // Now, execute the commands on the main server thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (String cmd : commandsToExecute) {
                    // Dispatch command as the console sender
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
                sendMessage(sender, "success",
                        Placeholder.unparsed("count", String.valueOf(commandsToExecute.size())),
                        Placeholder.unparsed("filename", finalFileName));
            });
        });

        return true;
    }
}
