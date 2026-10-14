package net.noxaria.core;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Noxaria Panel: nur fuer den Owner. Oeffnen mit /noxaria. */
public class PanelModule implements Listener {
    private final Noxaria pl;
    private final Set<UUID> frozen = new HashSet<>();

    public PanelModule(Noxaria pl) { this.pl = pl; }

    private boolean ok(Player p) {
        if (pl.isOwner(p)) return true;
        p.closeInventory();
        return false;
    }

    // ---------- Hauptmenue ----------
    public void open(Player p) {
        if (!ok(p)) return;
        Gui g = new Gui("&8» &5&lNoxaria Panel", 4);
        g.set(10, Util.item(Material.PLAYER_HEAD, "&d&lSpieler", "&7Alle Spieler ansehen,", "&7verwarnen, Rang vergeben", "", "&eKlick zum Öffnen"), x -> players(x, 0));
        g.set(12, Util.item(Material.NAME_TAG, "&6&lRänge", "&7Übersicht aller Gruppen", "", "&eKlick zum Öffnen"), this::ranksOverview);
        g.set(14, Util.item(Material.WRITABLE_BOOK, "&c&lVerwarnungen", "&7Letzte Verwarnungen", "", "&eKlick zum Öffnen"), this::warnLog);
        g.set(16, Util.item(Material.COMPARATOR, "&b&lServer", "&7Wartung, Chat, Status", "", "&eKlick zum Öffnen"), this::server);
        g.set(31, Util.item(Material.BARRIER, "&7Schließen"), Player::closeInventory);
        g.fill(0);
        g.open(p);
    }

    // ---------- Spielerliste ----------
    private void players(Player p, int page) {
        if (!ok(p)) return;
        List<OfflinePlayer> all = new ArrayList<>();
        for (OfflinePlayer o : Bukkit.getOfflinePlayers()) if (o.getName() != null) all.add(o);
        all.sort(Comparator.comparing((OfflinePlayer o) -> !o.isOnline()).thenComparingLong(o -> -o.getLastPlayed()));
        int pages = Math.max(1, (all.size() + 44) / 45);
        final int pg = Math.max(0, Math.min(page, pages - 1));

        Gui g = new Gui("&8» &5Spieler &7(" + all.size() + ")", 6);
        for (int i = 0; i < 45; i++) {
            int idx = pg * 45 + i;
            if (idx >= all.size()) break;
            OfflinePlayer o = all.get(idx);
            List<String> lore = new ArrayList<>();
            lore.add("&7Rang: &f" + pl.ranks.groupOf(o.getUniqueId()));
            lore.add("&7Verwarnungen: &c" + pl.data.getStringList("players." + o.getUniqueId() + ".warns").size());
            lore.add("");
            lore.add("&eKlick für Details");
            g.set(i, Util.head(o, (o.isOnline() ? "&a● &f" : "&7○ &f") + o.getName(), lore), x -> player(x, o.getUniqueId()));
        }
        g.set(45, Util.item(Material.ARROW, "&7Zurück zum Panel"), this::open);
        g.set(48, Util.item(Material.ARROW, "&7« Vorherige Seite"), x -> players(x, pg - 1));
        g.set(49, Util.item(Material.PAPER, "&fSeite " + (pg + 1) + "/" + pages));
        g.set(50, Util.item(Material.ARROW, "&7Nächste Seite »"), x -> players(x, pg + 1));
        g.fill(45);
        g.open(p);
    }

    // ---------- Einzelner Spieler ----------
    private List<String> info(OfflinePlayer t) {
        UUID id = t.getUniqueId();
        String b = "players." + id + ".";
        Player on = t.getPlayer();
        List<String> l = new ArrayList<>();
        l.add(on != null ? "&aOnline" : "&cOffline");
        l.add("&7UUID: &f" + id);
        String ip = (on != null && on.getAddress() != null) ? on.getAddress().getAddress().getHostAddress() : pl.data.getString(b + "ip", "unbekannt");
        l.add("&7IP: &f" + ip);
        l.add("&7Rang: &f" + pl.ranks.groupOf(id));
        l.add("&7Erster Beitritt: &f" + Util.date(t.getFirstPlayed()));
        l.add("&7Zuletzt online: &f" + (on != null ? "jetzt" : Util.date(t.getLastPlayed())));
        long played = pl.data.getLong(b + "playtime", 0L) + (on != null ? Math.max(0L, System.currentTimeMillis() - pl.data.getLong(b + "session", System.currentTimeMillis())) : 0L);
        l.add("&7Spielzeit (seit Plugin): &f" + (played / 3_600_000L) + " h " + ((played / 60_000L) % 60) + " min");
        l.add("&7Geld: &a" + Util.money(pl.market.bal(id)));
        if (on != null) {
            l.add("&7Ping: &f" + on.getPing() + " ms");
            l.add("&7Welt: &f" + on.getWorld().getName() + " &7(" + on.getLocation().getBlockX() + " " + on.getLocation().getBlockY() + " " + on.getLocation().getBlockZ() + ")");
            l.add("&7Modus: &f" + on.getGameMode());
            String brand = on.getClientBrandName();
            l.add("&7Client: &f" + (brand != null ? brand : "unbekannt"));
        }
        l.add("&7Abbau: &f" + pl.stats.ratio(id));
        List<String> w = pl.data.getStringList(b + "warns");
        l.add("&7Verwarnungen: &c" + w.size());
        for (int i = Math.max(0, w.size() - 3); i < w.size(); i++) {
            String[] a = w.get(i).split("\\|");
            if (a.length >= 3) l.add("  &8" + Util.date(Long.parseLong(a[0])) + " &7" + a[2] + " &8(" + a[1] + ")");
        }
        if (pl.data.getBoolean(b + "muted")) l.add("&cStummgeschaltet");
        if (frozen.contains(id)) l.add("&bEingefroren");
        return l;
    }

    private void player(Player p, UUID id) {
        if (!ok(p)) return;
        OfflinePlayer t = Bukkit.getOfflinePlayer(id);
        Player on = t.getPlayer();
        Gui g = new Gui("&8» &5" + t.getName(), 6);
        g.set(4, Util.head(t, "&f&l" + t.getName(), info(t)), null);

        g.set(20, Util.item(Material.BELL, "&c&lVerwarnen", "&7Grund auswählen"), x -> { if (ok(x)) pl.warns.openReasons(x, t, () -> player(x, id)); });
        g.set(22, Util.item(Material.NAME_TAG, "&6&lRang vergeben", "&7Aktuell: &f" + pl.ranks.groupOf(id)), x -> groups(x, id));
        boolean muted = pl.data.getBoolean("players." + id + ".muted");
        g.set(24, Util.item(muted ? Material.LIME_DYE : Material.GRAY_DYE, muted ? "&aEntstummen" : "&7Stummschalten"), x -> {
            if (!ok(x)) return;
            pl.data.set("players." + id + ".muted", !muted);
            pl.dirty();
            player(x, id);
        });
        g.set(29, Util.item(Material.IRON_DOOR, "&6Kick", "&7Wirft den Spieler vom Server"), x -> {
            if (!ok(x)) return;
            if (on != null) on.kick(Util.c("&cDu wurdest vom Server geworfen."));
            players(x, 0);
        });
        g.set(31, Util.item(Material.BARRIER, "&4&lBannen", "&7Bannt den Spieler (Konsole: ban)"), x -> {
            if (!ok(x)) return;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + t.getName() + " Gebannt durch das Noxaria Panel");
            Util.msg(x, "&f" + t.getName() + "&7 wurde gebannt.");
            players(x, 0);
        });
        boolean fr = frozen.contains(id);
        g.set(33, Util.item(fr ? Material.ICE : Material.PACKED_ICE, fr ? "&bAuftauen" : "&bEinfrieren", "&7Spieler kann sich nicht bewegen"), x -> {
            if (!ok(x)) return;
            if (fr) frozen.remove(id); else frozen.add(id);
            player(x, id);
        });
        if (on != null) {
            g.set(38, Util.item(Material.ENDER_PEARL, "&5Zum Spieler teleportieren"), x -> { if (ok(x)) { x.closeInventory(); x.teleport(on); } });
            g.set(40, Util.item(Material.CHEST, "&eInventar ansehen"), x -> { if (ok(x)) x.openInventory(on.getInventory()); });
            g.set(42, Util.item(Material.ENDER_CHEST, "&dEnderchest ansehen"), x -> { if (ok(x)) x.openInventory(on.getEnderChest()); });
        }
        g.set(45, Util.item(Material.ARROW, "&7Zurück"), x -> players(x, 0));
        g.fill(0);
        g.open(p);
    }

    private void groups(Player p, UUID id) {
        if (!ok(p)) return;
        OfflinePlayer t = Bukkit.getOfflinePlayer(id);
        ConfigurationSection s = pl.getConfig().getConfigurationSection("groups");
        List<String> ids = new ArrayList<>();
        if (s != null) ids.addAll(s.getKeys(false));
        ids.sort(Comparator.comparingInt((String k) -> -s.getInt(k + ".weight")));
        Gui g = new Gui("&8» &6Rang für &f" + t.getName(), 4);
        int slot = 10;
        for (String gid : ids) {
            if (slot > 25) break;
            if (slot == 17) slot = 19;
            boolean cur = gid.equals(pl.ranks.groupOf(id));
            g.set(slot, Util.item(cur ? Material.ENCHANTED_BOOK : Material.NAME_TAG, s.getString(gid + ".prefix", "&7") + gid,
                    "&7Vorschau: " + s.getString(gid + ".prefix", "&7") + t.getName(), "", cur ? "&a✔ aktuell" : "&eKlick = zuweisen"), x -> {
                if (!ok(x)) return;
                pl.ranks.setGroup(t, gid);
                Util.msg(x, "&f" + t.getName() + "&7 ist jetzt &f" + gid + "&7.");
                player(x, id);
            });
            slot++;
        }
        g.set(31, Util.item(Material.ARROW, "&7Zurück"), x -> player(x, id));
        g.fill(0);
        g.open(p);
    }

    private void ranksOverview(Player p) {
        if (!ok(p)) return;
        ConfigurationSection s = pl.getConfig().getConfigurationSection("groups");
        Gui g = new Gui("&8» &6Ränge", 4);
        int slot = 10;
        if (s != null) {
            for (String gid : s.getKeys(false)) {
                if (slot > 25) break;
                if (slot == 17) slot = 19;
                int count = 0;
                ConfigurationSection pls = pl.data.getConfigurationSection("players");
                if (pls != null) for (String u : pls.getKeys(false)) {
                    if (gid.equals(pls.getString(u + ".group", "spieler"))) count++;
                }
                g.set(slot++, Util.item(Material.NAME_TAG, s.getString(gid + ".prefix", "&7") + gid,
                        "&7Mitglieder: &f" + count, "&7Rechte: &f" + s.getStringList(gid + ".permissions").size(),
                        "", "&8Bearbeiten in config.yml"), null);
            }
        }
        g.set(31, Util.item(Material.ARROW, "&7Zurück"), this::open);
        g.fill(0);
        g.open(p);
    }

    private void warnLog(Player p) {
        if (!ok(p)) return;
        List<String> log = pl.data.getStringList("warnlog");
        List<String> lore = new ArrayList<>();
        for (int i = log.size() - 1; i >= 0 && lore.size() < 12; i--) {
            String[] a = log.get(i).split("\\|");
            if (a.length >= 4) lore.add("&8" + Util.date(Long.parseLong(a[0])) + " &f" + a[2] + " &7- &c" + a[3] + " &8(" + a[1] + ")");
        }
        if (lore.isEmpty()) lore.add("&7Noch keine Verwarnungen.");
        Gui g = new Gui("&8» &cVerwarnungen", 3);
        g.set(13, Util.item(Material.WRITABLE_BOOK, "&c&lLetzte Verwarnungen", lore), null);
        g.set(22, Util.item(Material.ARROW, "&7Zurück"), this::open);
        g.fill(0);
        g.open(p);
    }

    private void server(Player p) {
        if (!ok(p)) return;
        boolean maint = pl.getConfig().getBoolean("maintenance");
        Gui g = new Gui("&8» &bServer", 3);
        g.set(11, Util.item(maint ? Material.REDSTONE_TORCH : Material.LEVER, maint ? "&cWartungsmodus: AN" : "&aWartungsmodus: AUS",
                "&7Bei AN kommen nur Staff-Mitglieder rein.", "", "&eKlick zum Umschalten"), x -> {
            if (!ok(x)) return;
            pl.getConfig().set("maintenance", !maint);
            pl.saveConfig();
            server(x);
        });
        g.set(13, Util.item(pl.chatLocked ? Material.RED_DYE : Material.LIME_DYE, pl.chatLocked ? "&cChat: GESPERRT" : "&aChat: offen",
                "&7Gesperrt: nur Staff darf schreiben.", "", "&eKlick zum Umschalten"), x -> {
            if (!ok(x)) return;
            pl.chatLocked = !pl.chatLocked;
            server(x);
        });
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
        g.set(15, Util.item(Material.CLOCK, "&bStatus", "&7TPS: &f" + String.format("%.1f", Math.min(20.0, Bukkit.getTPS()[0])),
                "&7RAM: &f" + used + " / " + (rt.maxMemory() / 1048576L) + " MB",
                "&7Spieler: &f" + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers()), null);
        g.set(22, Util.item(Material.ARROW, "&7Zurück"), this::open);
        g.fill(0);
        g.open(p);
    }

    // ---------- Wirkung von Mute / Chat-Sperre / Freeze ----------
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (pl.data.getBoolean("players." + p.getUniqueId() + ".muted")) {
            e.setCancelled(true);
            Util.msg(p, "&cDu bist stummgeschaltet.");
        } else if (pl.chatLocked && !pl.isStaff(p)) {
            e.setCancelled(true);
            Util.msg(p, "&cDer Chat ist gerade gesperrt.");
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!frozen.contains(e.getPlayer().getUniqueId())) return;
        if (e.getTo() != null && (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getZ() != e.getTo().getZ() || e.getFrom().getY() != e.getTo().getY())) {
            e.setTo(e.getFrom());
        }
    }
}
