package pl.anarchia.scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

/** Scoreboard (sidebar) z animowanym tytulem, komendy /sb, /sbpokaz i prosty /klan. */
public class ScoreboardModule implements Listener, TabExecutor {

    private static final int MAX_LINES = 15;

    private final AnarchiaScoreboard plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final NamespacedKey keyHidden;
    private final NamespacedKey keyClan;
    private int titleFrame = 0;

    public ScoreboardModule(AnarchiaScoreboard plugin) {
        this.plugin = plugin;
        this.keyHidden = new NamespacedKey(plugin, "sb_hidden");
        this.keyClan = new NamespacedKey(plugin, "clan");
    }

    /* ------------------------------------------------------------------ */
    /*  Start / stop                                                       */
    /* ------------------------------------------------------------------ */

    public void start() {
        long updateTicks = Math.max(5, plugin.getConfig().getLong("scoreboard.update-ticks", 20));
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) updateLines(p);
        }, updateTicks, updateTicks);

        long titleTicks = Math.max(2, plugin.getConfig().getLong("scoreboard.title-ticks", 10));
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<String> frames = plugin.getConfig().getStringList("scoreboard.title-frames");
            if (frames.size() < 2) return;
            titleFrame = (titleFrame + 1) % frames.size();
            Component title = Util.comp(frames.get(titleFrame));
            for (Scoreboard sb : boards.values()) {
                Objective o = sb.getObjective("anarchia");
                if (o != null) o.displayName(title);
            }
        }, titleTicks, titleTicks);

        for (Player p : Bukkit.getOnlinePlayers()) createBoard(p);
    }

    public void stop() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (boards.containsKey(p.getUniqueId())) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        boards.clear();
    }

    public void recreateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) createBoard(p);
    }

    /* ------------------------------------------------------------------ */
    /*  Scoreboard                                                         */
    /* ------------------------------------------------------------------ */

    private static String entry(int i) {
        return "\u00a7" + Integer.toHexString(i) + "\u00a7r";
    }

    private boolean isHidden(Player p) {
        Byte b = p.getPersistentDataContainer().get(keyHidden, PersistentDataType.BYTE);
        return b != null && b == 1;
    }

    private void setHidden(Player p, boolean hidden) {
        p.getPersistentDataContainer().set(keyHidden, PersistentDataType.BYTE, (byte) (hidden ? 1 : 0));
    }

    public void createBoard(Player p) {
        List<String> lines = plugin.getConfig().getStringList("scoreboard.lines");
        List<String> frames = plugin.getConfig().getStringList("scoreboard.title-frames");
        Component title = Util.comp(frames.isEmpty() ? "&f&lANARCHIA.GG" : frames.get(0));

        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = sb.registerNewObjective("anarchia", Criteria.DUMMY, title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.numberFormat(NumberFormat.blank());   // ukrywa czerwone cyfry po prawej

        int count = Math.min(lines.size(), MAX_LINES);
        for (int i = 0; i < count; i++) {
            Team t = sb.registerNewTeam("l" + i);
            t.addEntry(entry(i));
            obj.getScore(entry(i)).setScore(count - i);
        }

        boards.put(p.getUniqueId(), sb);
        p.setScoreboard(sb);
        updateLines(p);

        if (isHidden(p)) sb.clearSlot(DisplaySlot.SIDEBAR);
    }

    private void updateLines(Player p) {
        Scoreboard sb = boards.get(p.getUniqueId());
        if (sb == null) return;
        if (p.getScoreboard() != sb) p.setScoreboard(sb);

        List<String> lines = plugin.getConfig().getStringList("scoreboard.lines");
        int count = Math.min(lines.size(), MAX_LINES);
        for (int i = 0; i < count; i++) {
            Team t = sb.getTeam("l" + i);
            if (t == null) continue;
            t.prefix(Util.comp(replace(lines.get(i), p)));
        }
    }

    private String replace(String s, Player p) {
        double bal = plugin.balance(p);
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        int hearts = hp == null ? 10 : (int) Math.round(hp.getValue() / 2.0);
        int kills = p.getStatistic(Statistic.PLAYER_KILLS);
        int deaths = p.getStatistic(Statistic.DEATHS);
        double kdr = deaths == 0 ? kills : (double) kills / deaths;

        return s.replace("{nick}", p.getName())
                .replace("{klan}", getClan(p))
                .replace("{serca}", String.valueOf(hearts))
                .replace("{czas}", playTime(p))
                .replace("{kasa}", String.format(Locale.US, "%.0f", bal))
                .replace("{zabojstwa}", String.valueOf(kills))
                .replace("{smierci}", String.valueOf(deaths))
                .replace("{kdr}", String.format(Locale.US, "%.2f", kdr))
                .replace("{ping}", String.valueOf(p.getPing()))
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{sektor}", plugin.getConfig().getString("sektor", "SPAWN01"))
                .replace("{kanal}", plugin.getConfig().getString("kanal-komenda", "/ch"));
    }

    private String playTime(Player p) {
        long sec = p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
        long days = sec / 86400;
        long months = days / 30;
        long hours = (sec % 86400) / 3600;
        long mins = (sec % 3600) / 60;
        if (months > 0) return months + "msc " + (days % 30) + "d";
        if (days > 0) return days + "d " + hours + "h";
        return hours + "h " + mins + "m";
    }

    /* ------------------------------------------------------------------ */
    /*  Eventy                                                             */
    /* ------------------------------------------------------------------ */

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        createBoard(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        boards.remove(e.getPlayer().getUniqueId());
    }

    /* ------------------------------------------------------------------ */
    /*  Komendy                                                            */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase();

        if (name.equals("sb") && args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("anarchiascoreboard.admin")) {
                sender.sendMessage(plugin.prefix() + plugin.msg("no-permission"));
                return true;
            }
            plugin.reloadAll();
            sender.sendMessage(plugin.prefix() + plugin.msg("reloaded"));
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Tylko dla graczy.");
            return true;
        }

        switch (name) {
            case "sb" -> {
                boolean hide;
                if (args.length == 0) hide = !isHidden(p);               // przelacznik
                else if (args[0].equalsIgnoreCase("off")) hide = true;
                else if (args[0].equalsIgnoreCase("on")) hide = false;
                else {
                    p.sendMessage(plugin.prefix() + plugin.msg("sb-usage"));
                    return true;
                }
                if (hide) hideBoard(p);
                else showBoard(p);
            }
            case "sbpokaz" -> showBoard(p);
            case "klan" -> handleClan(p, args);
            default -> { }
        }
        return true;
    }

    private void hideBoard(Player p) {
        Scoreboard sb = boards.get(p.getUniqueId());
        if (sb != null) sb.clearSlot(DisplaySlot.SIDEBAR);
        setHidden(p, true);
        p.sendMessage(plugin.prefix() + plugin.msg("sb-hidden"));
    }

    /** Tworzy scoreboard od nowa i pokazuje go graczowi. */
    private void showBoard(Player p) {
        setHidden(p, false);
        createBoard(p);
        p.sendMessage(plugin.prefix() + plugin.msg("sb-shown"));
    }

    /* ---------- klany (proste, w danych gracza) ---------- */

    private String getClan(Player p) {
        return p.getPersistentDataContainer().getOrDefault(keyClan, PersistentDataType.STRING, "-");
    }

    private void handleClan(Player p, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("stworz")) {
            if (!getClan(p).equals("-")) {
                p.sendMessage(plugin.prefix() + plugin.msg("clan-has"));
                return;
            }
            String n = args[1];
            if (n.length() < 2 || n.length() > 8 || !n.matches("[A-Za-z0-9]+")) {
                p.sendMessage(plugin.prefix() + plugin.msg("clan-bad-name"));
                return;
            }
            p.getPersistentDataContainer().set(keyClan, PersistentDataType.STRING, n);
            p.sendMessage(plugin.prefix() + plugin.msg("clan-created").replace("{clan}", n));
            updateLines(p);
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("opusc")) {
            p.getPersistentDataContainer().set(keyClan, PersistentDataType.STRING, "-");
            p.sendMessage(plugin.prefix() + plugin.msg("clan-left"));
            updateLines(p);
        } else {
            p.sendMessage(plugin.prefix() + plugin.msg("clan-usage"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String name = cmd.getName().toLowerCase();
        List<String> options = List.of();
        if (name.equals("sb") && args.length == 1) options = List.of("on", "off", "reload");
        if (name.equals("klan") && args.length == 1) options = List.of("stworz", "opusc");
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.startsWith(args[0].toLowerCase())) out.add(o);
        }
        return out;
    }
}
