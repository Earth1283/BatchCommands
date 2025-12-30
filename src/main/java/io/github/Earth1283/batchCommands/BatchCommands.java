package io.github.Earth1283.batchCommands;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class BatchCommands extends JavaPlugin {

    private File batchesFolder;
    private String fileExtension;
    
    private File linterConfigFile;
    private FileConfiguration linterConfig;

    @Override
    public void onEnable() {
        // Plugin startup logic
        getLogger().info("BatchCommands is starting up...");

        // Save default configs if they don't exist
        saveDefaultConfig();
        saveResource("linter.yml", false);

        // Create the plugin's data folder if it doesn't exist
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Load settings from config
        String folderName = getConfig().getString("settings.batch-folder", "batches");
        this.fileExtension = getConfig().getString("settings.file-extension", ".batch");
        
        // Load linter config
        loadLinterConfig();

        // Create the batch files sub-folder
        batchesFolder = new File(getDataFolder(), folderName);
        if (!batchesFolder.exists()) {
            batchesFolder.mkdirs();
            getLogger().info("Created '" + folderName + "' directory at: " + batchesFolder.getPath());
        }

        // Register the command and its tab completer
        this.getCommand("filebatch").setExecutor(new FileBatchCommand(this));
        this.getCommand("filebatch").setTabCompleter(new FileBatchTabCompleter(this));

        getLogger().info("BatchCommands has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("BatchCommands has been disabled.");
    }
    
    private void loadLinterConfig() {
        linterConfigFile = new File(getDataFolder(), "linter.yml");
        linterConfig = YamlConfiguration.loadConfiguration(linterConfigFile);
    }

    public FileConfiguration getLinterConfig() {
        if (linterConfig == null) {
            loadLinterConfig();
        }
        return linterConfig;
    }

    /**
     * Gets the folder where batch files are stored.
     * @return The File object for the configured batch directory.
     */
    public File getBatchesFolder() {
        return batchesFolder;
    }

    /**
     * Gets the configured file extension for batch files.
     * @return The file extension (e.g., ".batch").
     */
    public String getFileExtension() {
        return fileExtension;
    }
}
