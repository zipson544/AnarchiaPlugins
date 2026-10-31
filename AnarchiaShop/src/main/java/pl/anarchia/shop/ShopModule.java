package pl.anarchia.shop;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * /sklep - menu z kategoriami. LPM kupuje, PPM sprzedaje (Shift = stack / wszystko).
 * Edycja w grze: /sklep dodaj, usun, cena, kategoria stworz|usun, reload.
 * Dane w plugins/AnarchiaShop/shop.yml.
 */
public class ShopModule implements Listener, TabExecutor {

    private static final String ADMIN_PERM = "anarchia.shop.admin";
    private static final int PAGE_SIZE = 45;

    static class Category {
        String id;
        String name;
        Material icon = Material.CHEST;
    }

    static class ShopItem {
        String id;
        String category;
        ItemStack stack;     // uzywane, gdy heart == null
        double buy;
        double sell;
    }

    static class ShopHolder implements InventoryHolder {
        final String category;   // null = menu glowne
        final int page;
        Inventory inv;

        ShopHolder(String category, int page) {
            this.category = category;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final AnarchiaShop plugin;
    private final File file;
    private final LinkedHashMap<String, Category> categories = new LinkedHashMap<>();
    private final LinkedHashMap<String, ShopItem> items = new LinkedHashMap<>();

    public ShopModule(AnarchiaShop plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shop.yml");
        if (!file.exists()) plugin.saveResource("shop.yml", false);
        load();
    }

    /* ------------------------------------------------------------------ */
    /*  Wczytywanie / zapis                                                */
    /* ------------------------------------------------------------------ */

    public void load() {
        categories.clear();
        items.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection cs = yml.getConfigurationSection("categories");
        if (cs != null) {
            for (String id : cs.getKeys(false)) {
                Category c = new Category();
                c.id = id;
                c.name = cs.getString(id + ".name", id);
                Material m = Material.matchMaterial(cs.getString(id + ".icon", "CHEST"));
                c.icon = m == null ? Material.CHEST : m;
                categories.put(id, c);
            }
        }

        ConfigurationSection is = yml.getConfigurationSection("items");
        if (is != null) {
            for (String id : is.getKeys(false)) {
                ShopItem si = new ShopItem();
                si.id = id;
                si.category = is.getString(id + ".category", "");
                si.buy = is.getDouble(id + ".buy", 0);
                si.sell = is.getDouble(id + ".sell", 0);

                {
                    ItemStack st = is.getItemStack(id + ".item");
                    if (st == null) {
                        Material m = Material.matchMaterial(is.getString(id + ".material", ""));
                        if (m == null || m.isAir()) {
                            plugin.getLogger().warning("shop.yml: nieprawidlowy przedmiot '" + id + "' - pomijam.");
                            continue;
                        }
                        st = new ItemStack(m);
                    }
                    st.setAmount(1);
                    si.stack = st;
                }
                if (!categories.containsKey(si.category)) {
                    plugin.getLogger().warning("shop.yml: przedmiot '" + id + "' ma nieznana kategorie '" + si.category + "'.");
                    continue;
                }
                items.put(id, si);
            }
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Category c : categories.values()) {
            yml.set("categories." + c.id + ".name", c.name);
            yml.set("categories." + c.id + ".icon", c.icon.name());
        }
        for (ShopItem si : items.values()) {
            String k = "items." + si.id;
            yml.set(k + ".category", si.category);
            yml.set(k + ".buy", si.buy);
            yml.set(k + ".sell", si.sell);
            yml.set(k + ".item", si.stack);
        }
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Nie mozna zapisac shop.yml: " + ex.getMessage());
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Pomocnicze                                                         */
    /* ------------------------------------------------------------------ */

    private ItemStack stackOf(ShopItem si) {
        return si.stack.clone();
    }

    private List<ShopItem> itemsOf(String category) {
        return items.values().stream().filter(i -> i.category.equals(category)).collect(Collectors.toList());
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.format(Locale.US, "%.0f", d) : String.format(Locale.US, "%.2f", d);
    }

    private String nameOf(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        if (m != null && m.hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(m.displayName());
        }
        return it.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private ItemStack filler() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(" "));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack button(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Util.item(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Util.item(s));
        m.lore(l);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack display(ShopItem si) {
        ItemStack it = stackOf(si);
        ItemMeta m = it.getItemMeta();
        List<Component> lore = m.hasLore() && m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        if (si.buy > 0) lore.add(Util.item("&7Kup: &a" + fmt(si.buy) + "$"));
        if (si.sell > 0) lore.add(Util.item("&7Sprzedaj: &e" + fmt(si.sell) + "$"));
        lore.add(Component.empty());
        if (si.buy > 0) {
            lore.add(Util.item("&eLPM &7- kup 1"));
            lore.add(Util.item("&eShift+LPM &7- kup " + it.getMaxStackSize()));
        }
        if (si.sell > 0) {
            lore.add(Util.item("&ePPM &7- sprzedaj 1"));
            lore.add(Util.item("&eShift+PPM &7- sprzedaj wszystko"));
        }
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    private String shopTitle() {
        return plugin.getConfig().getString("messages.shop-title", "&8Sklep");
    }

    /* ------------------------------------------------------------------ */
    /*  Menu                                                               */
    /* ------------------------------------------------------------------ */

    public void openMain(Player p) {
        int n = categories.size();
        int size = Math.max(9, Math.min(54, ((Math.max(n, 1) - 1) / 9 + 1) * 9));
        ShopHolder holder = new ShopHolder(null, 0);
        Inventory inv = Bukkit.createInventory(holder, size, Util.comp(shopTitle()));
        holder.inv = inv;

        int slot = 0;
        for (Category c : categories.values()) {
            if (slot >= size) break;
            ItemStack icon = button(c.icon, c.name,
                    "&7Przedmiotow: &f" + itemsOf(c.id).size(),
                    "",
                    "&eKliknij, aby otworzyc");
            inv.setItem(slot++, icon);
        }
        p.openInventory(inv);
    }

    public void openCategory(Player p, String catId, int page) {
        Category c = categories.get(catId);
        if (c == null) {
            openMain(p);
            return;
        }
        List<ShopItem> list = itemsOf(catId);
        int pages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));

        ShopHolder holder = new ShopHolder(catId, page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Util.comp(c.name + " &8(" + (page + 1) + "/" + pages + ")"));
        holder.inv = inv;

        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = page * PAGE_SIZE + i;
            if (idx >= list.size()) break;
            inv.setItem(i, display(list.get(idx)));
        }
        for (int i = PAGE_SIZE; i < 54; i++) inv.setItem(i, filler());

        inv.setItem(45, button(Material.BARRIER, "&cWroc do kategorii"));
        if (page > 0) inv.setItem(47, button(Material.ARROW, "&7Poprzednia strona"));
        inv.setItem(49, balanceItem(p));
        if (page < pages - 1) inv.setItem(51, button(Material.ARROW, "&7Nastepna strona"));

        p.openInventory(inv);
    }

    private ItemStack balanceItem(Player p) {
        return button(Material.GOLD_INGOT, "&6Twoje saldo", "&a" + fmt(plugin.balance(p)) + "$");
    }

    /* ------------------------------------------------------------------ */
    /*  Klikniecia                                                         */
    /* ------------------------------------------------------------------ */

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof ShopHolder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != top) return;

        int slot = e.getSlot();

        // menu glowne
        if (holder.category == null) {
            List<Category> list = new ArrayList<>(categories.values());
            if (slot >= 0 && slot < list.size()) openCategory(p, list.get(slot).id, 0);
            return;
        }

        // dolny pasek
        if (slot >= PAGE_SIZE) {
            if (slot == 45) openMain(p);
            else if (slot == 47 && holder.page > 0) openCategory(p, holder.category, holder.page - 1);
            else if (slot == 51 && top.getItem(51) != null && top.getItem(51).getType() == Material.ARROW) {
                openCategory(p, holder.category, holder.page + 1);
            }
            return;
        }

        List<ShopItem> list = itemsOf(holder.category);
        int idx = holder.page * PAGE_SIZE + slot;
        if (idx < 0 || idx >= list.size()) return;
        ShopItem si = list.get(idx);

        ClickType ct = e.getClick();
        switch (ct) {
            case LEFT -> buy(p, si, 1);
            case SHIFT_LEFT -> buy(p, si, stackOf(si).getMaxStackSize());
            case RIGHT -> sell(p, si, 1, false);
            case SHIFT_RIGHT -> sell(p, si, 0, true);
            default -> { return; }
        }
        top.setItem(49, balanceItem(p));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof ShopHolder) e.setCancelled(true);
    }

    /* ------------------------------------------------------------------ */
    /*  Kupowanie / sprzedawanie                                           */
    /* ------------------------------------------------------------------ */

    private void buy(Player p, ShopItem si, int amount) {
        if (si.buy <= 0) {
            p.sendMessage(plugin.prefix() + plugin.msg("shop-cant-buy"));
            return;
        }
        double price = si.buy * amount;
        if (!plugin.withdraw(p, price)) {
            p.sendMessage(plugin.prefix() + plugin.msg("shop-no-money").replace("{price}", fmt(price)));
            return;
        }
        ItemStack give = stackOf(si);
        give.setAmount(amount);
        p.getInventory().addItem(give).values()
                .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));

        p.sendMessage(plugin.prefix() + plugin.msg("shop-bought")
                .replace("{amount}", String.valueOf(amount))
                .replace("{name}", nameOf(give))
                .replace("{price}", fmt(price)));
    }

    private void sell(Player p, ShopItem si, int amount, boolean all) {
        if (si.sell <= 0) {
            p.sendMessage(plugin.prefix() + plugin.msg("shop-cant-sell"));
            return;
        }
        ItemStack proto = stackOf(si);
        ItemStack[] contents = p.getInventory().getStorageContents();

        int have = 0;
        for (ItemStack it : contents) {
            if (it != null && it.isSimilar(proto)) have += it.getAmount();
        }
        int n = all ? have : Math.min(amount, have);
        if (n <= 0) {
            p.sendMessage(plugin.prefix() + plugin.msg("shop-no-items"));
            return;
        }

        int left = n;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (it == null || !it.isSimilar(proto)) continue;
            int take = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            if (it.getAmount() <= 0) contents[i] = null;
            left -= take;
        }
        p.getInventory().setStorageContents(contents);

        double price = si.sell * n;
        plugin.deposit(p, price);
        p.sendMessage(plugin.prefix() + plugin.msg("shop-sold")
                .replace("{amount}", String.valueOf(n))
                .replace("{name}", nameOf(proto))
                .replace("{price}", fmt(price)));
    }

    /* ------------------------------------------------------------------ */
    /*  Komendy                                                            */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Tylko dla graczy.");
            return true;
        }

        if (args.length == 0) {
            openMain(p);
            return true;
        }

        String sub = args[0].toLowerCase();

        // /sklep <kategoria>
        if (categories.containsKey(sub)) {
            openCategory(p, sub, 0);
            return true;
        }

        List<String> adminSubs = List.of("dodaj", "usun", "cena", "kategoria", "reload");
        if (!adminSubs.contains(sub)) {
            p.sendMessage(plugin.prefix() + plugin.msg("shop-no-category"));
            return true;
        }
        if (!p.hasPermission(ADMIN_PERM)) {
            p.sendMessage(plugin.prefix() + plugin.msg("no-permission"));
            return true;
        }

        switch (sub) {
            case "reload" -> {
                load();
                p.sendMessage(plugin.prefix() + plugin.msg("reloaded"));
            }
            case "dodaj" -> addItem(p, args);
            case "usun" -> removeItem(p);
            case "cena" -> setPrice(p, args);
            case "kategoria" -> categoryCmd(p, args);
            default -> { }
        }
        return true;
    }

    private ItemStack hand(Player p) {
        ItemStack h = p.getInventory().getItemInMainHand();
        if (h.getType().isAir()) {
            p.sendMessage(plugin.prefix() + plugin.msg("shop-hold-item"));
            return null;
        }
        ItemStack one = h.clone();
        one.setAmount(1);
        return one;
    }

    private Double parsePrice(String s) {
        try {
            double d = Double.parseDouble(s.replace(',', '.'));
            return d < 0 ? null : d;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void adminUsage(Player p) {
        p.sendMessage(plugin.prefix() + plugin.msg("shop-admin-usage"));
    }

    /** /sklep dodaj <kategoria> <kupno> <sprzedaz> - przedmiot z reki */
    private void addItem(Player p, String[] args) {
        if (args.length < 4) {
            adminUsage(p);
            return;
        }
        String cat = args[1].toLowerCase();
        Double buy = parsePrice(args[2]);
        Double sell = parsePrice(args[3]);
        if (!categories.containsKey(cat)) {
            p.sendMessage(plugin.prefix() + plugin.msg("shop-no-category"));
            return;
        }
        if (buy == null || sell == null) {
            adminUsage(p);
            return;
        }
        ItemStack one = hand(p);
        if (one == null) return;

        // jesli juz jest w tej kategorii - tylko zmien ceny
        for (ShopItem si : itemsOf(cat)) {
            if (stackOf(si).isSimilar(one)) {
                si.buy = buy;
                si.sell = sell;
                save();
                p.sendMessage(plugin.prefix() + plugin.msg("shop-price-set"));
                return;
            }
        }

        ShopItem si = new ShopItem();
        int n = items.size() + 1;
        while (items.containsKey("i" + n)) n++;
        si.id = "i" + n;
        si.category = cat;
        si.stack = one;
        si.buy = buy;
        si.sell = sell;
        items.put(si.id, si);
        save();
        p.sendMessage(plugin.prefix() + plugin.msg("shop-added"));
    }

    /** /sklep usun - usuwa przedmiot z reki ze sklepu */
    private void removeItem(Player p) {
        ItemStack one = hand(p);
        if (one == null) return;
        int before = items.size();
        items.values().removeIf(si -> stackOf(si).isSimilar(one));
        if (items.size() == before) {
            p.sendMessage(plugin.prefix() + plugin.msg("shop-not-in-shop"));
            return;
        }
        save();
        p.sendMessage(plugin.prefix() + plugin.msg("shop-removed"));
    }

    /** /sklep cena <kupno> <sprzedaz> - zmienia ceny przedmiotu z reki */
    private void setPrice(Player p, String[] args) {
        if (args.length < 3) {
            adminUsage(p);
            return;
        }
        Double buy = parsePrice(args[1]);
        Double sell = parsePrice(args[2]);
        if (buy == null || sell == null) {
            adminUsage(p);
            return;
        }
        ItemStack one = hand(p);
        if (one == null) return;
        boolean found = false;
        for (ShopItem si : items.values()) {
            if (stackOf(si).isSimilar(one)) {
                si.buy = buy;
                si.sell = sell;
                found = true;
            }
        }
        if (!found) {
            p.sendMessage(plugin.prefix() + plugin.msg("shop-not-in-shop"));
            return;
        }
        save();
        p.sendMessage(plugin.prefix() + plugin.msg("shop-price-set"));
    }

    /** /sklep kategoria stworz <id> <nazwa...>  |  /sklep kategoria usun <id> */
    private void categoryCmd(Player p, String[] args) {
        if (args.length < 3) {
            adminUsage(p);
            return;
        }
        String action = args[1].toLowerCase();
        String id = args[2].toLowerCase();

        if (action.equals("stworz")) {
            if (!id.matches("[a-z0-9_]+") || categories.containsKey(id)) {
                adminUsage(p);
                return;
            }
            Category c = new Category();
            c.id = id;
            c.name = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "&f" + id;
            ItemStack h = p.getInventory().getItemInMainHand();
            c.icon = h.getType().isAir() ? Material.CHEST : h.getType();
            categories.put(id, c);
            save();
            p.sendMessage(plugin.prefix() + plugin.msg("shop-cat-created"));
        } else if (action.equals("usun")) {
            if (categories.remove(id) == null) {
                p.sendMessage(plugin.prefix() + plugin.msg("shop-no-category"));
                return;
            }
            items.values().removeIf(si -> si.category.equals(id));
            save();
            p.sendMessage(plugin.prefix() + plugin.msg("shop-cat-removed"));
        } else {
            adminUsage(p);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(categories.keySet());
            if (sender.hasPermission(ADMIN_PERM)) out.addAll(List.of("dodaj", "usun", "cena", "kategoria", "reload"));
        } else if (sender.hasPermission(ADMIN_PERM)) {
            if (args.length == 2 && args[0].equalsIgnoreCase("dodaj")) out.addAll(categories.keySet());
            if (args.length == 2 && args[0].equalsIgnoreCase("kategoria")) out.addAll(List.of("stworz", "usun"));
            if (args.length == 3 && args[0].equalsIgnoreCase("kategoria") && args[1].equalsIgnoreCase("usun")) {
                out.addAll(categories.keySet());
            }
        }
        String start = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        return out.stream().filter(s -> s.startsWith(start)).collect(Collectors.toList());
    }
}
