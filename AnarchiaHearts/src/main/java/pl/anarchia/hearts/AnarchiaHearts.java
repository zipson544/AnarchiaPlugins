package pl.anarchia.hearts;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dodatkowe Serca (czerwone, do 30 serc) i Anarchiczne Serce (pomaranczowe, 30-40 serc).
 * Paper 1.21.4 - nie wymaga zadnych innych pluginow.
 */
public class AnarchiaHearts extends JavaPlugin implements Listener, TabExecutor {

    private NamespacedKey keyType;
    private NamespacedKey keyHearts;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        keyType = new NamespacedKey(this, "heart_type");
        keyHearts = new NamespacedKey(this, "hearts");

        getServer().getPluginManager().registerEvents(this, this);
        for (String name : List.of("serca", "wyplacserca", "wyplacserce")) {
            PluginCommand cmd = getCommand(name);
            if (cmd == null) {
                getLogger().severe("Brak komendy w plugin.yml: " + name);
                continue;
            }
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        for (Player p : Bukkit.getOnlinePlayers()) applyHearts(p);
        getLogger().info("AnarchiaHearts wlaczony.");
    }

    private FileConfiguration cfg() {
        return getConfig();
    }

    /* ------------------------------------------------------------------ */
    /*  Serca gracza (zapisane w danych gracza)                            */
    /* ------------------------------------------------------------------ */

    public int getHearts(Player p) {
        return p.getPersistentDataContainer().getOrDefault(keyHearts, PersistentDataType.INTEGER,
                cfg().getInt("default-hearts", 10));
    }

    public void setHearts(Player p, int hearts) {
        p.getPersistentDataContainer().set(keyHearts, PersistentDataType.INTEGER, hearts);
        applyHearts(p);
    }

    private void applyHearts(Player p) {
        AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        double max = getHearts(p) * 2.0;
        attr.setBaseValue(max);
        if (p.getHealth() > max) p.setHealth(max);
    }

    /* ------------------------------------------------------------------ */
    /*  Przedmioty                                                         */
    /* ------------------------------------------------------------------ */

    public ItemStack createHeart(String type, int amount) {
        String path = type.equals("orange") ? "orange" : "red";

        Material mat = Material.matchMaterial(cfg().getString(path + ".material", "RED_DYE"));
        if (mat == null) mat = Material.RED_DYE;

        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(itemText(cfg().getString(path + ".name")));
        List<Component> lore = new ArrayList<>();
        for (String line : cfg().getStringList(path + ".lore")) lore.add(itemText(line));
        meta.lore(lore);

        int cmd = cfg().getInt(path + ".custom-model-data", 0);
        if (cmd > 0) meta.setCustomModelData(cmd);

        meta.getPersistentDataContainer().set(keyType, PersistentDataType.STRING, path);
        item.setItemMeta(meta);
        return item;
    }

    private String getHeartType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keyType, PersistentDataType.STRING);
    }

    private void giveHearts(Player p, String type, int count) {
        while (count > 0) {
            int stack = Math.min(64, count);
            p.getInventory().addItem(createHeart(type, stack))
                    .values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
            count -= stack;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Eventy                                                             */
    /* ------------------------------------------------------------------ */

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = e.getItem();
        String type = getHeartType(item);
        if (type == null) return;

        e.setCancelled(true);

        Player p = e.getPlayer();
        int hearts = getHearts(p);
        int max;

        if (type.equals("red")) {
            max = cfg().getInt("red.max-hearts", 30);
            if (hearts >= max) {
                send(p, "red-max", "{max}", String.valueOf(max));
                return;
            }
        } else {
            int min = cfg().getInt("orange.min-hearts", 30);
            max = cfg().getInt("orange.max-hearts", 40);
            if (hearts < min) {
                send(p, "orange-min", "{min}", String.valueOf(min));
                return;
            }
            if (hearts >= max) {
                send(p, "orange-max", "{max}", String.valueOf(max));
                return;
            }
        }

        int newHearts = Math.min(hearts + cfg().getInt("per-use", 1), max);
        item.setAmount(item.getAmount() - 1);

        setHearts(p, newHearts);
        p.setHealth(Math.min(p.getHealth() + 2.0, newHearts * 2.0));
        send(p, "used", "{hearts}", String.valueOf(newHearts));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        applyHearts(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        applyHearts(p);
        Bukkit.getScheduler().runTask(this, () -> {
            AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) p.setHealth(attr.getValue());
        });
    }

    /* ------------------------------------------------------------------ */
    /*  Komendy                                                            */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase();
        if (name.equals("wyplacserca") || name.equals("wyplacserce")) {
            return withdraw(sender, name, args);
        }
        return admin(sender, args);
    }

    /** /wyplacserca <ilosc>  |  /wyplacserce (jedno serce) */
    private boolean withdraw(CommandSender sender, String name, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Tylko dla graczy.");
            return true;
        }

        int amount = 1;
        if (name.equals("wyplacserca")) {
            try {
                amount = args.length >= 1 ? Integer.parseInt(args[0]) : 0;
            } catch (NumberFormatException ex) {
                amount = 0;
            }
            if (amount < 1) {
                p.sendMessage(prefix() + msg("withdraw-usage"));
                return true;
            }
        }

        int hearts = getHearts(p);
        int min = cfg().getInt("min-withdraw", 10);
        int available = hearts - min;
        if (available <= 0) {
            send(p, "withdraw-none", "{min}", String.valueOf(min));
            return true;
        }
        if (amount > available) {
            send(p, "withdraw-too-many", "{available}", String.valueOf(available));
            return true;
        }

        // serca do limitu czerwonego -> czerwone, powyzej -> pomaranczowe
        int redMax = cfg().getInt("red.max-hearts", 30);
        int red = 0, orange = 0, h = hearts;
        for (int i = 0; i < amount; i++) {
            if (h > redMax) orange++;
            else red++;
            h--;
        }

        setHearts(p, h);
        giveHearts(p, "red", red);
        giveHearts(p, "orange", orange);

        p.sendMessage(prefix() + msg("withdrawn")
                .replace("{amount}", String.valueOf(amount))
                .replace("{hearts}", String.valueOf(h)));
        return true;
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("anarchiahearts.admin")) {
            sender.sendMessage(prefix() + msg("no-permission"));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage(prefix() + msg("reloaded"));
            return true;
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("give")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(prefix() + msg("no-player"));
                return true;
            }
            String type = args[2].equalsIgnoreCase("orange") ? "orange" : "red";
            int amount = 1;
            if (args.length >= 4) {
                try {
                    amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                } catch (NumberFormatException ignored) {
                }
            }
            giveHearts(target, type, amount);
            sender.sendMessage(prefix() + color("&aDano " + amount + "x " + type + " graczowi " + target.getName()));
            return true;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(prefix() + msg("no-player"));
                return true;
            }
            try {
                int hearts = Math.max(1, Math.min(100, Integer.parseInt(args[2])));
                setHearts(target, hearts);
                sender.sendMessage(prefix() + color("&a" + target.getName() + " ma teraz " + hearts + " serc."));
            } catch (NumberFormatException ex) {
                sender.sendMessage(prefix() + msg("admin-usage"));
            }
            return true;
        }

        sender.sendMessage(prefix() + msg("admin-usage"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("serca") || !sender.hasPermission("anarchiahearts.admin")) {
            return List.of();
        }
        if (args.length == 1) return filter(List.of("give", "set", "reload"), args[0]);
        if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) return filter(List.of("red", "orange"), args[2]);
        return List.of();
    }

    private List<String> filter(List<String> options, String start) {
        return options.stream().filter(s -> s.toLowerCase().startsWith(start.toLowerCase())).collect(Collectors.toList());
    }

    /* ------------------------------------------------------------------ */
    /*  Pomocnicze                                                         */
    /* ------------------------------------------------------------------ */

    private String prefix() {
        return color(cfg().getString("messages.prefix", ""));
    }

    private String msg(String key) {
        return color(cfg().getString("messages." + key, key));
    }

    private void send(Player p, String key, String placeholder, String value) {
        p.sendMessage(prefix() + msg(key).replace(placeholder, value));
    }

    /** Tekst z kolorami & jako Component bez domyslnej kursywy (nazwy i opisy przedmiotow). */
    private static Component itemText(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s == null ? "" : s)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
