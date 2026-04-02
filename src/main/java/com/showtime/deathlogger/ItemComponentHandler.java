package com.showtime.deathlogger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class ItemComponentHandler implements Listener {

    private final DeathLogger plugin;
    public static final String COMPONENT_KEY_NAME = "is_tracker_compass";
    public static final String IMR_KEY_NAME = "is_imr_item";
    public static final String CREATIVE_KEY_NAME = "is_creative_item";

    public ItemComponentHandler(DeathLogger plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onComponentProcess(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result == null || !result.hasItemMeta()) {
            return;
        }

        ItemMeta meta = result.getItemMeta();
        if (!meta.hasDisplayName()) {
            return;
        }

        String name = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

        // 1. IMR check
        if (name.equalsIgnoreCase("IMR")) {
            meta.displayName(null);
            NamespacedKey imrKey = new NamespacedKey(plugin, IMR_KEY_NAME);
            meta.getPersistentDataContainer().set(imrKey, PersistentDataType.BYTE, (byte) 1);
            result.setItemMeta(meta);
            event.setResult(result);
            return;
        }

        // 2. BOTEN-PCB check
        if (name.equalsIgnoreCase("BOTEN-PCB")) {
            Material eyeMaterial = Material.getMaterial("OPEN_EYEBLOSSOM");
            if (result.getType() == eyeMaterial || result.getType() == Material.POPPY) {
                NamespacedKey creativeKey = new NamespacedKey(plugin, CREATIVE_KEY_NAME);
                meta.getPersistentDataContainer().set(creativeKey, PersistentDataType.BYTE, (byte) 1);
                result.setItemMeta(meta);
                event.setResult(result);
                return;
            }
        }

        // 3. Compass Tracking (LOC-)
        if (name.startsWith("LOC-") && meta instanceof CompassMeta compassMeta) {
            String targetName = name.substring(4);
            Player target = Bukkit.getPlayer(targetName);

            if (target != null && target.isOnline()) {
                Location targetLoc = target.getLocation();
                compassMeta.setLodestone(targetLoc);
                compassMeta.setLodestoneTracked(false);
                compassMeta.displayName(Component.text(target.getName()));
                
                NamespacedKey trackerKey = new NamespacedKey(plugin, COMPONENT_KEY_NAME);
                compassMeta.getPersistentDataContainer().set(trackerKey, PersistentDataType.BYTE, (byte) 1);
                
                result.setItemMeta(compassMeta);
                event.setResult(result);
            }
        }
    }

    @EventHandler
    public void onResultPickup(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.ANVIL) return;
        if (event.getRawSlot() != 2) return; // Result slot
        
        ItemStack result = event.getCurrentItem();
        if (result == null || !result.hasItemMeta()) return;
        
        Player player = (Player) event.getWhoClicked();
        NamespacedKey trackerKey = new NamespacedKey(plugin, COMPONENT_KEY_NAME);
        NamespacedKey creativeKey = new NamespacedKey(plugin, CREATIVE_KEY_NAME);
        NamespacedKey imrKey = new NamespacedKey(plugin, IMR_KEY_NAME);

        // Check if pick-up is one of our special items
        if (result.getItemMeta().getPersistentDataContainer().has(trackerKey, PersistentDataType.BYTE) ||
            result.getItemMeta().getPersistentDataContainer().has(creativeKey, PersistentDataType.BYTE) ||
            result.getItemMeta().getPersistentDataContainer().has(imrKey, PersistentDataType.BYTE)) {
            
            // 🎬 Activation Animation
            Location loc = player.getLocation();
            loc.getWorld().spawnParticle(Particle.WITCH, loc.add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
            loc.getWorld().spawnParticle(Particle.GLOW, loc, 15, 0.5, 0.5, 0.5, 0.02);
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
            loc.getWorld().playSound(loc, Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 0.5f, 1.2f);
        }
    }
}
