package com.showtime.deathlogger;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VanishProcessor implements Listener {

    private final DeathLogger plugin;
    private final Set<UUID> vanishedPlayers = new HashSet<>();

    public VanishProcessor(DeathLogger plugin) {
        this.plugin = plugin;
    }

    public boolean toggleVanish(Player player) {
        UUID uuid = player.getUniqueId();
        if (vanishedPlayers.contains(uuid)) {
            // Unvanish
            vanishedPlayers.remove(uuid);
            showPlayer(player);
            // Broadcast fake join message (standard Bukkit/Essentials style)
            Bukkit.broadcastMessage("§e" + player.getName() + " joined the game");
            player.sendMessage("§b§lVanish §8| §7State: §cOFF");
            return false;
        } else {
            // Vanish
            vanishedPlayers.add(uuid);
            hidePlayer(player);
            // Broadcast fake quit message
            Bukkit.broadcastMessage("§e" + player.getName() + " left the game");
            player.sendMessage("§b§lVanish §8| §7State: §aON");
            return true;
        }
    }

    private void hidePlayer(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.hidePlayer(plugin, player);
            }
        }
    }

    private void showPlayer(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.showPlayer(plugin, player);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        
        // Hide vanished players from the joiner
        for (UUID uuid : vanishedPlayers) {
            Player vanished = Bukkit.getPlayer(uuid);
            if (vanished != null) {
                joining.hidePlayer(plugin, vanished);
            }
        }
        
        // If a vanished player joins, hide them from everyone
        if (vanishedPlayers.contains(joining.getUniqueId())) {
            event.joinMessage(null); // Silent join
            hidePlayer(joining);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (vanishedPlayers.contains(event.getPlayer().getUniqueId())) {
            event.quitMessage(null); // Hide real quit message
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (vanishedPlayers.contains(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Player target) {
            if (vanishedPlayers.contains(target.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent event) {
        // Remove vanished players from the sample/count using the modern Paper API
        event.getListedPlayers().removeIf(info -> vanishedPlayers.contains(info.id()));
        
        // Update count for privacy
        int vanishedCount = 0;
        for (UUID uuid : vanishedPlayers) {
            if (Bukkit.getPlayer(uuid) != null) vanishedCount++;
        }
        event.setNumPlayers(Math.max(0, event.getNumPlayers() - vanishedCount));
    }

    public boolean isVanished(Player player) {
        return vanishedPlayers.contains(player.getUniqueId());
    }
}
