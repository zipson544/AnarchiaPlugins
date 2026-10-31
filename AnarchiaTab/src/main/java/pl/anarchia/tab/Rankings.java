package pl.anarchia.tab;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Cache rankingow (zabojstwa i kasa) dla tablisty.
 * Dane online sa brane na zywo ze statystyk Bukkita i Vaulta,
 * dane offline z ostatniego zapisu (rankings.yml).
 * Wszystko dziala na glownym watku.
 */
public class Rankings {

    public record Entry(UUID uuid, String name, double value) {}

    private static class Rec {
        String name = "?";
        int kills = 0;
        double money = 0;
    }

    private final AnarchiaTab plugin;
    private final File file;
    private final Map<UUID, Rec> data = new HashMap<>();

    private List<Entry> topKills = new ArrayList<>();
    private List<Entry> topMoney = new ArrayList<>();

    public Rankings(AnarchiaTab plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rankings.yml");
        load();
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 40L, 100L);   // co 5 s
        Bukkit.getScheduler().runTaskTimer(plugin, this::save, 6000L, 6000L);  // co 5 min
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yml.getConfigurationSection("players");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                Rec r = new Rec();
                r.name = sec.getString(key + ".name", "?");
                r.kills = sec.getInt(key + ".kills");
                r.money = sec.getDouble(key + ".money");
                data.put(UUID.fromString(key), r);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        // zaktualizuj graczy online przed zapisem
        updateOnline();
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Rec> e : data.entrySet()) {
            String k = "players." + e.getKey();
            yml.set(k + ".name", e.getValue().name);
            yml.set(k + ".kills", e.getValue().kills);
            yml.set(k + ".money", e.getValue().money);
        }
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Nie mozna zapisac rankings.yml: " + ex.getMessage());
        }
    }

    private void updateOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Rec r = data.computeIfAbsent(p.getUniqueId(), k -> new Rec());
            r.name = p.getName();
            r.kills = p.getStatistic(Statistic.PLAYER_KILLS);
            r.money = plugin.balance(p);
        }
    }

    public void refresh() {
        updateOnline();
        List<Entry> kills = new ArrayList<>();
        List<Entry> money = new ArrayList<>();
        for (Map.Entry<UUID, Rec> e : data.entrySet()) {
            Rec r = e.getValue();
            kills.add(new Entry(e.getKey(), r.name, r.kills));
            money.add(new Entry(e.getKey(), r.name, r.money));
        }
        Comparator<Entry> cmp = Comparator.comparingDouble(Entry::value).reversed();
        kills.sort(cmp);
        money.sort(cmp);
        topKills = kills;
        topMoney = money;
    }

    public List<Entry> topKills() { return topKills; }
    public List<Entry> topMoney() { return topMoney; }

    /** Miejsce gracza (od 1); jesli go nie ma na liscie, to rozmiar listy + 1. */
    public int rankOf(List<Entry> list, UUID id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).uuid().equals(id)) return i + 1;
        }
        return list.size() + 1;
    }
}
