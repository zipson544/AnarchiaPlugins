package pl.anarchia.scoreboard;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Scoreboard + /sb + /sbpokaz + prosty /klan. Wymaga Vaulta (stan konta). */
public class AnarchiaScoreboard extends JavaPlugin {

    private Economy economy;
    private ScoreboardModule module;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        module = new ScoreboardModule(this);
        getServer().getPluginManager().registerEvents(module, this);

        bind("sb", module, module);
        bind("sbpokaz", module, module);
        bind("klan", module, module);

        module.start();
        getLogger().info("AnarchiaScoreboard wlaczony.");
    }

    @Override
    public void onDisable() {
        if (module != null) module.stop();
    }

    private void bind(String name, CommandExecutor exec, TabCompleter tab) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().severe("Brak komendy w plugin.yml: " + name);
            return;
        }
        cmd.setExecutor(exec);
        cmd.setTabCompleter(tab);
    }

    public void reloadAll() {
        reloadConfig();
        module.recreateAll();
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

    public String prefix() {
        return Util.color(getConfig().getString("messages.prefix", ""));
    }

    public String msg(String key) {
        return Util.color(getConfig().getString("messages." + key, key));
    }
}
