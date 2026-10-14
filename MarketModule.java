package net.noxaria.core;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Auktionshaus (/market), Verkaufsmenue (/sell) und eine einfache interne Waehrung. */
public class MarketModule implements Listener {
    private final Noxaria pl;
    private final Set<UUID> pending = new HashSet<>();

    public MarketModule(Noxaria pl) { this.pl = pl; }

    // ---------- Geld ----------
    public double bal(UUID u) {
        return pl.data.getDouble("players." + u + ".balance", pl.getConfig().getDouble("economy.start-balance", 500));
    }

    public void add(UUID u, double amt) {
        pl.data.set("players." + u + ".balance", Math.max(0, bal(u) + amt));
        pl.dirty();
    }

    // ---------- Markt ----------
    private List<Long> ids() {
        ConfigurationSection s = pl.data.getConfigurationSection("market.listings");
        List<Long> r = new ArrayList<>();
        if (s != null) for (String k : s.getKeys(false)) r.add(Long.parseLong(k));
        r.sort(Comparator.reverseOrder());
        return r;
    }

    public void openMarket(Player p, int page, boolean mine) {
        List<Long> all = new ArrayList<>();
        for (long id : ids()) {
            String seller = pl.data.getString("market.listings." + id + ".seller", "");
            if (!mine || seller.equals(p.getUniqueId().toString())) all.add(id);
        }
        int pages = Math.max(1, (all.size() + 44) / 45);
        page = Math.max(0, Math.min(page, pages - 1));
        final int pg = page;

        Gui g = new Gui(mine ? "&8» &6&lMeine Angebote" : "&8» &6&lMarkt", 6);
        for (int i = 0; i < 45; i++) {
            int idx = pg * 45 + i;
            if (idx >= all.size()) break;
            final String id = String.valueOf(all.get(idx));
            ConfigurationSection s = pl.data.getConfigurationSection("market.listings." + id);
            if (s == null) continue;
            ItemStack it = Util.fromB64(s.getString("item", ""));
            if (it == null) continue;
            ItemMeta m = it.getItemMeta();
            List<Component> lore = m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
            lore.add(Util.c(""));
            lore.add(Util.c("&7Preis: &a" + Util.money(s.getDouble("price"))));
            lore.add(Util.c("&7Verkäufer: &f" + s.getString("sellerName", "?")));
            lore.add(Util.c(mine ? "&cKlick = Angebot zurückziehen" : "&eKlick = kaufen"));
            m.lore(lore);
            it.setItemMeta(m);
            g.set(i, it, x -> { if (mine) cancel(x, id); else buy(x, id, pg); });
        }
        g.set(45, Util.item(Material.ARROW, "&7« Zurück"), x -> openMarket(x, pg - 1, mine));
        g.set(47, Util.item(Material.GOLD_INGOT, "&a&lVerkaufen", "&7Items reinlegen und", "&7sofort Geld bekommen", "", "&eKlick zum Öffnen"), this::openSell);
        g.set(48, Util.item(Material.CHEST, "&e&lItem anbieten", "&7Item in die Hand nehmen,", "&7klicken und Preis in den Chat", "&7schreiben."), this::startListing);
        g.set(49, Util.item(Material.PAPER, "&fSeite " + (pg + 1) + "/" + pages));
        g.set(50, Util.item(mine ? Material.EMERALD : Material.BOOK, mine ? "&aAlle Angebote" : "&6Meine Angebote"), x -> openMarket(x, 0, !mine));
        g.set(51, Util.item(Material.GOLD_NUGGET, "&6Kontostand", "&a" + Util.money(bal(p.getUniqueId()))), null);
        g.set(53, Util.item(Material.ARROW, "&7Weiter »"), x -> openMarket(x, pg + 1, mine));
        g.fill(45);
        g.open(p);
    }

    private void buy(Player p, String id, int page) {
        String path = "market.listings." + id;
        ConfigurationSection s = pl.data.getConfigurationSection(path);
        if (s == null) { Util.msg(p, "&cDas Angebot ist nicht mehr verfügbar."); openMarket(p, page, false); return; }
        UUID seller = UUID.fromString(s.getString("seller"));
        double price = s.getDouble("price");
        if (seller.equals(p.getUniqueId())) { Util.msg(p, "&cDas ist dein eigenes Angebot."); return; }
        if (bal(p.getUniqueId()) < price) { Util.msg(p, "&cDu hast nicht genug Geld."); p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
        ItemStack it = Util.fromB64(s.getString("item", ""));
        if (it == null) { pl.data.set(path, null); return; }
        if (p.getInventory().firstEmpty() == -1) { Util.msg(p, "&cDein Inventar ist voll."); return; }
        add(p.getUniqueId(), -price);
        p.getInventory().addItem(it);
        double tax = price * pl.getConfig().getDouble("market.tax", 0.05);
        add(seller, price - tax);
        pl.data.set(path, null);
        pl.dirty();
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        Util.msg(p, "Gekauft für &a" + Util.money(price) + "&7.");
        Player sp = Bukkit.getPlayer(seller);
        if (sp != null) Util.msg(sp, "&f" + p.getName() + "&7 hat dein Angebot gekauft: &a+" + Util.money(price - tax));
        openMarket(p, page, false);
    }

    private void cancel(Player p, String id) {
        String path = "market.listings." + id;
        ConfigurationSection s = pl.data.getConfigurationSection(path);
        if (s == null) { openMarket(p, 0, true); return; }
        ItemStack it = Util.fromB64(s.getString("item", ""));
        if (it != null) {
            Map<Integer, ItemStack> left = p.getInventory().addItem(it);
            for (ItemStack r : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), r);
        }
        pl.data.set(path, null);
        pl.dirty();
        Util.msg(p, "Angebot zurückgezogen.");
        openMarket(p, 0, true);
    }

    private void startListing(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) { Util.msg(p, "&cNimm das Item, das du anbieten willst, in die Hand."); return; }
        int own = 0;
        for (long id : ids()) if (pl.data.getString("market.listings." + id + ".seller", "").equals(p.getUniqueId().toString())) own++;
        if (own >= pl.getConfig().getInt("market.max-listings", 10)) { Util.msg(p, "&cDu hast zu viele Angebote."); return; }
        pending.add(p.getUniqueId());
        p.closeInventory();
        Util.msg(p, "Schreibe jetzt den &aPreis &7für den ganzen Stapel in den Chat (oder &cabbrechen&7).");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!pending.contains(p.getUniqueId())) return;
        e.setCancelled(true);
        String txt = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(pl, () -> handlePrice(p, txt));
    }

    private void handlePrice(Player p, String txt) {
        pending.remove(p.getUniqueId());
        if (txt.equalsIgnoreCase("abbrechen")) { Util.msg(p, "Abgebrochen."); return; }
        double price;
        try { price = Double.parseDouble(txt.replace(',', '.')); } catch (NumberFormatException ex) { Util.msg(p, "&cUngültiger Preis."); return; }
        if (price <= 0 || price > 1_000_000_000d) { Util.msg(p, "&cUngültiger Preis."); return; }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) { Util.msg(p, "&cDu hältst kein Item mehr in der Hand."); return; }
        long id = pl.data.getLong("market.next", 1L);
        pl.data.set("market.next", id + 1);
        String b = "market.listings." + id;
        pl.data.set(b + ".seller", p.getUniqueId().toString());
        pl.data.set(b + ".sellerName", p.getName());
        pl.data.set(b + ".price", price);
        pl.data.set(b + ".item", Util.toB64(hand));
        pl.data.set(b + ".time", System.currentTimeMillis());
        pl.dirty();
        p.getInventory().setItemInMainHand(null);
        Util.msg(p, "Angebot erstellt für &a" + Util.money(price) + "&7.");
        openMarket(p, 0, true);
    }

    // ---------- Verkaufsmenue ----------
    private double priceOf(ItemStack it) {
        if (it == null || it.getType().isAir() || it.hasItemMeta()) return -1;
        return pl.getConfig().getDouble("sell-prices." + it.getType().name(), -1);
    }

    private double total(Gui g) {
        double sum = 0;
        for (int s = 0; s < 45; s++) {
            ItemStack it = g.getInventory().getItem(s);
            double pr = priceOf(it);
            if (pr >= 0) sum += pr * it.getAmount();
        }
        return sum;
    }

    private ItemStack sellButton(double tot) {
        return Util.item(Material.EMERALD, "&a&lAlles verkaufen", "&7Wert: &a" + Util.money(tot), "", "&eKlick zum Verkaufen");
    }

    public void openSell(Player p) {
        Gui g = new Gui("&8» &a&lVerkaufen", 6);
        g.editable = true;
        g.set(49, sellButton(0), x -> {
            double sum = 0;
            for (int s = 0; s < 45; s++) {
                ItemStack it = g.getInventory().getItem(s);
                double pr = priceOf(it);
                if (pr >= 0) { sum += pr * it.getAmount(); g.getInventory().setItem(s, null); }
            }
            if (sum <= 0) { Util.msg(x, "&cNichts Verkaufbares im Menü."); return; }
            add(x.getUniqueId(), sum);
            x.playSound(x.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            Util.msg(x, "Verkauft für &a" + Util.money(sum) + "&7. Kontostand: &a" + Util.money(bal(x.getUniqueId())));
            g.getInventory().setItem(49, sellButton(0));
        });
        g.fill(45);
        g.onChange = () -> g.getInventory().setItem(49, sellButton(total(g)));
        g.onClose = x -> {
            for (int s = 0; s < 45; s++) {
                ItemStack it = g.getInventory().getItem(s);
                if (it == null || it.getType().isAir()) continue;
                g.getInventory().setItem(s, null);
                Map<Integer, ItemStack> left = x.getInventory().addItem(it);
                for (ItemStack r : left.values()) x.getWorld().dropItemNaturally(x.getLocation(), r);
            }
        };
        g.open(p, false);
    }
}
