package com.showtime.deathlogger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ActionProcessor implements Listener {

    public ActionProcessor(DeathLogger plugin) {
        // No-op
    }

    @EventHandler
    public void onActionPerform(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.FISHING_ROD) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }

        Component nameComponent = meta.displayName();
        if (nameComponent == null) return;
        
        String name = PlainTextComponentSerializer.plainText().serialize(nameComponent);

        // Try to parse coordinates (X Y Z)
        String[] parts = name.trim().split("[\\s,]+");
        if (parts.length < 3) {
            return;
        }

        try {
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);

            Location target = new Location(player.getWorld(), x, y, z);
            
            // Teleport the player
            player.teleport(target);
            
        } catch (NumberFormatException e) {
            // Silence
        }
    }
}
