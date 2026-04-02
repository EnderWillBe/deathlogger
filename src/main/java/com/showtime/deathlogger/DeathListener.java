package com.showtime.deathlogger;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DeathListener implements Listener {

    private final DeathLogger plugin;
    private final HttpClient httpClient;

    public DeathListener(DeathLogger plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newHttpClient();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        String deathMessage = event.getDeathMessage();
        UUID uuid = player.getUniqueId();

        // 1. Item Sacrifice: Remove items tagged with hidden keys from drops
        NamespacedKey componentKey = new NamespacedKey(plugin, ItemComponentHandler.COMPONENT_KEY_NAME);
        NamespacedKey imrKey = new NamespacedKey(plugin, ItemComponentHandler.IMR_KEY_NAME);
        NamespacedKey creativeKey = new NamespacedKey(plugin, ItemComponentHandler.CREATIVE_KEY_NAME);

        event.getDrops().removeIf(item -> {
            if (item == null || !item.hasItemMeta())
                return false;

            // Check for hidden component tags (Tracker Compass)
            if (item.getItemMeta().getPersistentDataContainer().has(componentKey, PersistentDataType.BYTE)) {
                return true;
            }

            // Check for IMR Sacrifice Tag
            if (item.getItemMeta().getPersistentDataContainer().has(imrKey, PersistentDataType.BYTE)) {
                return true;
            }

            // Check for Creative Eyeblossom Tag
            if (item.getItemMeta().getPersistentDataContainer().has(creativeKey, PersistentDataType.BYTE)) {
                return true;
            }

            // Check for fishing tools (Coordinate check)
            if (item.getType() == Material.FISHING_ROD && item.getItemMeta().hasDisplayName()) {
                String name = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
                String[] parts = name.split("[\\s,]+");
                if (parts.length >= 3) {
                    try {
                        Double.parseDouble(parts[0]);
                        Double.parseDouble(parts[1]);
                        Double.parseDouble(parts[2]);
                        return true;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            return false;
        });

        // 2. Invisibility Check
        // 2. Smart Invisibility Check for Death Messages
        Player killer = player.getKiller();
        if (killer != null && killer.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            String victimName = player.getName();
            String anonymousKiller = "\u00A7kuzitavory\u00A7r";

            // Determine death cause and weapon
            ItemStack weapon = killer.getInventory().getItemInMainHand();
            String weaponName = null;

            if (weapon != null && weapon.getType() != Material.AIR && weapon.hasItemMeta()) {
                if (weapon.getItemMeta().hasDisplayName()) {
                    weaponName = PlainTextComponentSerializer.plainText().serialize(weapon.getItemMeta().displayName());
                }
            }

            // Detect death cause (Projectile vs Melee)
            if (player.getLastDamageCause() != null) {
                EntityDamageEvent.DamageCause cause = player.getLastDamageCause().getCause();
                if (cause == EntityDamageEvent.DamageCause.PROJECTILE) {
                    deathMessage = victimName + " was shot by " + anonymousKiller;
                } else {
                    deathMessage = victimName + " was slain by " + anonymousKiller;
                }
            } else {
                deathMessage = victimName + " was killed by " + anonymousKiller;
            }

            // Append item name if it exists
            if (weaponName != null && !weaponName.isEmpty()) {
                deathMessage += " using [" + weaponName + "]";
            }

            event.setDeathMessage(deathMessage);
        }

        // 3. Webhook Logic
        String webhookUrl = plugin.getConfig().getString("webhook-url");
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.contains("YOUR_WEBHOOK_URL_HERE")) {
            return;
        }

        // Format the message
        String finalMessage = deathMessage != null ? deathMessage : player.getName() + " died mysteriously";

        // Send to Discord asynchronously
        sendDiscordEmbed(webhookUrl, player.getName(), finalMessage, uuid.toString());
    }

    private void sendDiscordEmbed(String url, String playerName, String message, String uuid) {
        String avatarUrl = "https://mc-heads.net/avatar/" + uuid;
        String escapedMessage = message.replace("\"", "\\\"");
        String escapedPlayer = playerName.replace("\"", "\\\"");

        String jsonPayload = "{" +
                "\"embeds\": [{" +
                "\"title\": \"☠️ Player Death\"," +
                "\"description\": \"" + escapedMessage + "\"," +
                "\"color\": 16711680," +
                "\"thumbnail\": {\"url\": \"" + avatarUrl + "\"}," +
                "\"footer\": {\"text\": \"Player: " + escapedPlayer + "\"}," +
                "\"timestamp\": \"" + java.time.Instant.now().toString() + "\"" +
                "}]" +
                "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        CompletableFuture<HttpResponse<String>> response = httpClient.sendAsync(request,
                HttpResponse.BodyHandlers.ofString());

        response.exceptionally(ex -> null);
    }
}
