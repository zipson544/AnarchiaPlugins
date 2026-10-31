package pl.anarchia.tab;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.Action;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.LocalTime;
import java.util.*;

/**
 * Tablista 4 x 20 oparta o fałszywych graczy wysylanych pakietami.
 * Prawdziwi gracze sa ukrywani na tabie (listed = false).
 */
public class TabManager extends PacketListenerAbstract implements Listener {

    private static final int COLS = 4;
    private static final int ROWS = 20;
    private static final int SLOTS = COLS * ROWS;

    private final AnarchiaTab plugin;
    private final UUID[] fakeIds = new UUID[SLOTS];
    private final String[] fakeNames = new String[SLOTS];
    private final Set<UUID> fakeSet = new HashSet<>();
    private final Set<UUID> created = new HashSet<>();

    public TabManager(AnarchiaTab plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
        for (int i = 0; i < SLOTS; i++) {
            fakeIds[i] = UUID.nameUUIDFromBytes(("AnarchiaTab-slot-" + i).getBytes());
            fakeNames[i] = String.format("tab%02d", i);   // klient sortuje po nazwie -> kolumny po 20
            fakeSet.add(fakeIds[i]);
        }
    }

    /* ---------- ukrywanie prawdziwych graczy na tabie ---------- */

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.PLAYER_INFO_UPDATE) return;

        WrapperPlayServerPlayerInfoUpdate wrapper = new WrapperPlayServerPlayerInfoUpdate(event);
        boolean changed = false;
        for (PlayerInfo info : wrapper.getEntries()) {
            if (fakeSet.contains(info.getProfileId())) continue;
            info.setListed(false);
            changed = true;
        }
        if (changed) {
            wrapper.getActions().add(Action.UPDATE_LISTED);
            event.markForReEncode(true);
        }
    }

    /* ---------- eventy Bukkita ---------- */

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // chwila opoznienia, zeby klient zdazyl przetworzyc pakiety dolaczenia
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                create(p);
                update(p);
            }
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        created.remove(e.getPlayer().getUniqueId());
    }

    /* ---------- tworzenie / odswiezanie ---------- */

    public void create(Player p) {
        List<PlayerInfo> infos = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            UserProfile profile = new UserProfile(fakeIds[i], fakeNames[i]);
            infos.add(new PlayerInfo(profile, true, 0, GameMode.SURVIVAL, Component.text(" "), null));
        }
        WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(
                EnumSet.of(Action.ADD_PLAYER, Action.UPDATE_LISTED, Action.UPDATE_LATENCY,
                        Action.UPDATE_GAME_MODE, Action.UPDATE_DISPLAY_NAME),
                infos);
        PacketEvents.getAPI().getPlayerManager().sendPacket(p, packet);
        created.add(p.getUniqueId());
    }

    public void update(Player p) {
        if (!created.contains(p.getUniqueId())) return;

        Component[] slots = build(p);

        List<PlayerInfo> infos = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            UserProfile profile = new UserProfile(fakeIds[i], fakeNames[i]);
            infos.add(new PlayerInfo(profile, true, 0, GameMode.SURVIVAL, slots[i], null));
        }
        WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(
                EnumSet.of(Action.UPDATE_DISPLAY_NAME), infos);
        PacketEvents.getAPI().getPlayerManager().sendPacket(p, packet);

        int online = Bukkit.getOnlinePlayers().size();
        p.sendPlayerListHeaderAndFooter(
                joinLines(plugin.getConfig().getStringList("tab.header"), online),
                joinLines(plugin.getConfig().getStringList("tab.footer"), online));
    }

    private Component joinLines(List<String> lines, int online) {
        Component out = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) out = out.appendNewline();
            out = out.append(Util.comp(lines.get(i).replace("{online}", String.valueOf(online))));
        }
        return out;
    }

    /* ---------- budowanie zawartosci 4 kolumn ---------- */

    private Component[] build(Player p) {
        String[][] col = new String[COLS][ROWS];
        for (String[] c : col) Arrays.fill(c, " ");

        Rankings rankings = plugin.rankings();
        int kills = p.getStatistic(Statistic.PLAYER_KILLS);
        int deaths = p.getStatistic(Statistic.DEATHS);
        double kdr = deaths == 0 ? kills : (double) kills / deaths;
        double bal = plugin.balance(p);

        // kolumna 1: informacje o graczu i sektorze
        int r = 0;
        col[0][r++] = "&e&lINFORMACJE O TOBIE";
        r++;
        col[0][r++] = "&7Nick: &f" + p.getName();
        col[0][r++] = "&7Zabójstw: &f" + kills;
        col[0][r++] = "&7Śmierci: &f" + deaths;
        col[0][r++] = "&7KDR: &f" + String.format(Locale.US, "%.2f", kdr);
        col[0][r++] = "&7Stan konta: &f" + String.format(Locale.US, "%.1f", bal) + "$";
        col[0][r++] = "&7Ping: &f" + p.getPing();
        r++;
        col[0][r++] = "&e&lINFORMACJE O SEKTORZE";
        r++;
        col[0][r++] = "&7Godzina: &f" + LocalTime.now().withNano(0);
        col[0][r++] = "&7Graczy: &f" + Bukkit.getOnlinePlayers().size();

        // kolumna 2: najlepsi gracze (zabojstwa)
        col[1][0] = "&e&lNAJLEPSI GRACZE";
        List<Rankings.Entry> tk = rankings.topKills();
        for (int i = 0; i < 15 && i < tk.size(); i++) {
            Rankings.Entry en = tk.get(i);
            col[1][2 + i] = rankLine(i + 1, en, Util.shortNum((long) en.value()) + " &7⚔");
        }
        col[1][17] = "&e→ &7#" + rankings.rankOf(tk, p.getUniqueId()) + " &f" + p.getName()
                + " &7» &e" + Util.shortNum(kills) + " &7⚔";

        // kolumna 3: najbogatsi gracze
        col[2][0] = "&e&lNAJBOGATSI GRACZE";
        List<Rankings.Entry> tm = rankings.topMoney();
        for (int i = 0; i < 15 && i < tm.size(); i++) {
            Rankings.Entry en = tm.get(i);
            col[2][2 + i] = rankLine(i + 1, en, "&a" + Util.shortNum((long) en.value()) + "$");
        }
        col[2][17] = "&e→ &7#" + rankings.rankOf(tm, p.getUniqueId()) + " &f" + p.getName()
                + " &7» &a" + Util.shortNum((long) bal) + "$";

        // kolumna 4: informacje z configu
        List<String> info = plugin.getConfig().getStringList("tab.info");
        for (int i = 0; i < ROWS && i < info.size(); i++) col[3][i] = info.get(i);

        Component[] result = new Component[SLOTS];
        for (int c = 0; c < COLS; c++) {
            for (int row = 0; row < ROWS; row++) {
                String text = col[c][row];
                result[c * ROWS + row] = Util.comp(text == null || text.isEmpty() ? " " : text);
            }
        }
        return result;
    }

    private String rankLine(int rank, Rankings.Entry en, String value) {
        String dot = Bukkit.getPlayer(en.uuid()) != null ? "&a•" : "&c•";
        return dot + " &7#" + rank + " &f" + en.name() + " &7» &e" + value;
    }
}
