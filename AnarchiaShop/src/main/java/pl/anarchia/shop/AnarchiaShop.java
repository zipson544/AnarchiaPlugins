package pl.anarchia.shop;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Sklep /sklep. Wymaga Vaulta i pluginu ekonomii. */
public class AnarchiaShop extends JavaPlugin {

    private Economy economy;
    private ShopModule shop;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        shop = new ShopModule(this);
        getServer().getPluginManager().registerEvents(shop, this);

        PluginCommand cmd = getCommand("sklep");
        if (cmd == null) {
            getLogger().severe("Brak komendy 'sklep' w plugin.yml!");
            return;
        }
        cmd.setExecutor(shop);
        cmd.setTabCompleter(shop);

        getLogger().info("AnarchiaShop wlaczony.");
    }

    /* ---------- ekonomia (szukana dopiero, gdy jest potrzebna) ---------- */

    private Economy eco() {
        if (economy == null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
        return economy;
    }

    public double balance(OfflinePlayer p) {
        Economy e = eco();
        if (e == null) return 0;
        try {
            return e.getBalance(p);
        } catch (Exception ex) {
            return 0;
        }
    }

    /** Pobiera pieniadze; zwraca false, gdy gracz ma za malo lub brak ekonomii. */
    public boolean withdraw(OfflinePlayer p, double amount) {
        Economy e = eco();
        if (e == null || e.getBalance(p) < amount) return false;
        return e.withdrawPlayer(p, amount).transactionSuccess();
    }

    public void deposit(OfflinePlayer p, double amount) {
        Economy e = eco();
        if (e != null) e.depositPlayer(p, amount);
    }

    public String prefix() {
        return Util.color(getConfig().getString("messages.prefix", ""));
    }

    public String msg(String key) {
        return Util.color(getConfig().getString("messages." + key, key));
    }
}
