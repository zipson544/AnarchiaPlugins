package pl.anarchia.tab;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Tablista 4-kolumnowa. Wymaga pluginow Vault i PacketEvents. */
public class AnarchiaTab extends JavaPlugin {

    private Economy economy;
    private Rankings rankings;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        rankings = new Rankings(this);
        rankings.start();

        try {
            TabModule.enable(this);
        } catch (Throwable t) {
            getLogger().severe("Nie udalo sie wlaczyc tablisty: " + t);
            t.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("AnarchiaTab wlaczony.");
    }

    @Override
    public void onDisable() {
        if (rankings != null) rankings.save();
    }

    public Rankings rankings() {
        return rankings;
    }

    /* ---------- ekonomia (szukana dopiero, gdy jest potrzebna) ---------- */

    public double balance(OfflinePlayer p) {
        if (economy == null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
        if (economy == null) return 0;
        try {
            return economy.getBalance(p);
        } catch (Exception e) {
            return 0;
        }
    }
}
