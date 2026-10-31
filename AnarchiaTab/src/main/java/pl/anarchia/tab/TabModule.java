package pl.anarchia.tab;

import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Osobna klasa, zeby klasy PacketEvents ladowaly sie dopiero wtedy,
 * gdy plugin PacketEvents jest zainstalowany.
 */
public final class TabModule {

    private TabModule() {}

    public static void enable(AnarchiaTab plugin) {
        TabManager tab = new TabManager(plugin);

        PacketEvents.getAPI().getEventManager().registerListener(tab);
        plugin.getServer().getPluginManager().registerEvents(tab, plugin);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) tab.update(p);
        }, 20L, 20L);

        // gracze, ktorzy sa juz online (np. po /reload)
        for (Player p : Bukkit.getOnlinePlayers()) tab.create(p);
    }
}
