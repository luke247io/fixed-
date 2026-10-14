package net.noxaria.core;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class Noxaria extends JavaPlugin implements Listener {

    public YamlConfiguration data;
    private File dataFile;
    private boolean dirty;
    public boolean chatLocked = false;

    public PickModule pick;
    public SwordModule sword;
    public RankModule ranks;
    public WarnModule warns;
    public MarketModule market;
    public PanelModule panel;
    public StashModule stash;
    public StatsModule stats;

    public static Noxaria get() { return JavaPlugin.getPlugin(Noxaria.class); }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);

        pick = new PickModule(this);
        sword = new SwordModule(this);
        ranks = new RankModule(this);
        warns = new WarnModule(this);
        market = new MarketModule(this);
        panel = new PanelModule(this);
        stash = new StashModule(this);
        stats = new StatsModule(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(new GuiListener(this), this);
        pm.registerEvents(pick, this);
        pm.registerEvents(sword, this);
        pm.registerEvents(ranks, this);
        pm.registerEvents(market, this);
        pm.registerEvents(panel, this);
        pm.registerEvents(stash, this);
        pm.registerEvents(stats, this);
        pick.registerRecipe();
        sword.registerRecipe();

        for (String c : List.of("noxaria", "warn", "market", "sell", "money", "pay", "spawnstash")) {
            if (getCommand(c) != null) getCommand(c).setExecutor(this::handle);
        }
        Bukkit.getScheduler().runTaskTimer(this, this::flush, 600L, 600L);
        for (Player p : Bukkit.getOnlinePlayers()) ranks.apply(p);
        getLogger().info("NoxariaCore aktiviert.");
    }

    @Override
    public void onDisable() {
        dirty = true;
        flush();
    }

    public void dirty() { dirty = true; }

    public void flush() {
        if (!dirty) return;
        try { data.save(dataFile); } catch (IOException e) { getLogger().warning("data.yml konnte nicht gespeichert werden: " + e.getMessage()); }
        dirty = false;
    }

    // ---------- Rechte ----------
    public boolean isOwner(OfflinePlayer p) {
        String u = getConfig().getString("owner-uuid", "");
        return !u.isEmpty() && u.equals(p.getUniqueId().toString());
    }

    public boolean isStaff(Player p) { return isOwner(p) || p.hasPermission("noxaria.staff"); }

    public boolean canWarn(CommandSender s) {
        if (s instanceof ConsoleCommandSender) return true;
        return s instanceof Player p && (isOwner(p) || p.hasPermission("noxaria.warn"));
    }

    public void alertStaff(String msg) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isStaff(p)) {
                p.sendMessage(Util.c(msg));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
            }
        }
        getLogger().info(msg.replaceAll("[&§].", ""));
    }

    // ---------- Join / Quit ----------
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String base = "players." + p.getUniqueId();
        data.set(base + ".name", p.getName());
        data.set(base + ".last", System.currentTimeMillis());
        data.set(base + ".session", System.currentTimeMillis());
        if (p.getAddress() != null) data.set(base + ".ip", p.getAddress().getAddress().getHostAddress());

        String ownerName = getConfig().getString("owner-name", "");
        if (getConfig().getString("owner-uuid", "").isEmpty() && p.getName().equalsIgnoreCase(ownerName)) {
            getConfig().set("owner-uuid", p.getUniqueId().toString());
            saveConfig();
            data.set(base + ".group", "owner");
            Util.msg(p, "Du wurdest als &4Owner&7 festgelegt (UUID gespeichert).");
        }
        dirty = true;
        ranks.apply(p);
        int conv = pick.convertAll(p);
        if (conv > 0) Util.msg(p, "Deine alte Drachen-Spitzhacke wurde auf die neue Version umgestellt.");
        if (getConfig().getBoolean("maintenance") && !isStaff(p)) {
            p.kick(Util.c("&cDer Server ist im Wartungsmodus."));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        String b = "players." + e.getPlayer().getUniqueId();
        long now = System.currentTimeMillis();
        long start = data.getLong(b + ".session", now);
        data.set(b + ".playtime", data.getLong(b + ".playtime", 0L) + Math.max(0L, now - start));
        data.set(b + ".session", now);
        data.set(b + ".last", now);
        dirty = true;
    }

    // ---------- Befehle ----------
    private boolean handle(CommandSender s, Command cmd, String label, String[] a) {
        switch (cmd.getName().toLowerCase()) {
            case "noxaria" -> noxaria(s, a);
            case "warn" -> warnCmd(s, a);
            case "market" -> { if (s instanceof Player p) market.openMarket(p, 0, false); }
            case "sell" -> { if (s instanceof Player p) market.openSell(p); }
            case "money" -> {
                if (s instanceof Player p) Util.msg(p, "Kontostand: &a" + Util.money(market.bal(p.getUniqueId())));
            }
            case "pay" -> payCmd(s, a);
            case "spawnstash" -> {
                if (s instanceof Player p) {
                    if (isOwner(p) || p.hasPermission("noxaria.stash")) stash.spawn(p);
                    else p.sendMessage(Util.c("&cKeine Berechtigung."));
                }
            }
            default -> { return false; }
        }
        return true;
    }

    private void noxaria(CommandSender s, String[] a) {
        boolean owner = (s instanceof Player p && isOwner(p)) || s instanceof ConsoleCommandSender;
        if (!owner) { s.sendMessage(Util.c("&cUnbekannter Befehl.")); return; }
        if (a.length == 0) {
            if (s instanceof Player p) panel.open(p); else s.sendMessage("Nur im Spiel.");
            return;
        }
        switch (a[0].toLowerCase()) {
            case "give" -> {
                if (a.length < 2) { Util.msg(s, "/noxaria give pick|sword [spieler]"); return; }
                Player t = a.length >= 3 ? Bukkit.getPlayerExact(a[2]) : (s instanceof Player p ? p : null);
                if (t == null) { Util.msg(s, "Spieler nicht gefunden."); return; }
                ItemStack it = a[1].equalsIgnoreCase("sword") ? sword.create() : pick.create();
                t.getInventory().addItem(it);
                Util.msg(s, "Item gegeben.");
            }
            case "convert" -> {
                if (s instanceof Player p) Util.msg(p, pick.convertAll(p) + " Spitzhacke(n) umgewandelt.");
            }
            case "setblocks" -> {
                if (s instanceof Player p && a.length >= 2) {
                    try { pick.setBlocks(p, Long.parseLong(a[1])); } catch (NumberFormatException ex) { Util.msg(s, "Ungueltige Zahl."); }
                }
            }
            case "setkills" -> {
                if (s instanceof Player p && a.length >= 2) {
                    try { sword.setKills(p, Integer.parseInt(a[1]), a.length >= 3 ? Integer.parseInt(a[2]) : 0); }
                    catch (NumberFormatException ex) { Util.msg(s, "Ungueltige Zahl."); }
                }
            }
            case "eco" -> {
                if (a.length == 4 && a[1].equalsIgnoreCase("give")) {
                    OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(a[2]);
                    if (t == null) { Util.msg(s, "Spieler nicht gefunden."); return; }
                    try {
                        market.add(t.getUniqueId(), Double.parseDouble(a[3].replace(',', '.')));
                        Util.msg(s, "Geld gutgeschrieben.");
                    } catch (NumberFormatException ex) { Util.msg(s, "Ungueltige Zahl."); }
                } else Util.msg(s, "/noxaria eco give <spieler> <betrag>");
            }
            case "reload" -> {
                reloadConfig();
                warns.load();
                for (Player p : Bukkit.getOnlinePlayers()) ranks.apply(p);
                Util.msg(s, "Konfiguration neu geladen.");
            }
            default -> Util.msg(s, "give | convert | setblocks | setkills | eco | reload");
        }
    }

    private void warnCmd(CommandSender s, String[] a) {
        if (!canWarn(s)) { s.sendMessage(Util.c("&cKeine Berechtigung.")); return; }
        if (a.length == 0) { Util.msg(s, "/warn <spieler> [grund]"); return; }
        OfflinePlayer t = Bukkit.getPlayerExact(a[0]);
        if (t == null) t = Bukkit.getOfflinePlayerIfCached(a[0]);
        if (t == null) { Util.msg(s, "Spieler nicht gefunden."); return; }
        if (a.length == 1 && s instanceof Player p) {
            warns.openReasons(p, t, null);
        } else if (a.length >= 2) {
            warns.warn(s.getName(), t, String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)));
            Util.msg(s, "Verwarnung gesendet.");
        }
    }

    private void payCmd(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) return;
        if (a.length != 2) { Util.msg(p, "/pay <spieler> <betrag>"); return; }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null || t.equals(p)) { Util.msg(p, "Spieler nicht gefunden."); return; }
        double amt;
        try { amt = Double.parseDouble(a[1].replace(',', '.')); } catch (NumberFormatException ex) { Util.msg(p, "Ungueltiger Betrag."); return; }
        if (amt <= 0 || amt > market.bal(p.getUniqueId())) { Util.msg(p, "Ungueltiger Betrag oder zu wenig Geld."); return; }
        UUID me = p.getUniqueId();
        market.add(me, -amt);
        market.add(t.getUniqueId(), amt);
        Util.msg(p, "Du hast &a" + Util.money(amt) + "&7 an &f" + t.getName() + "&7 gesendet.");
        Util.msg(t, "Du hast &a" + Util.money(amt) + "&7 von &f" + p.getName() + "&7 erhalten.");
    }
}
