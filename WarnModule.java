package net.noxaria.core;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Verwarnungen mit Bildschirm-Titel, Bossbar und Sirene. */
public class WarnModule {
    private final Noxaria pl;
    final List<String[]> reasons = new ArrayList<>();

    public WarnModule(Noxaria pl) { this.pl = pl; load(); }

    public void load() {
        reasons.clear();
        for (String s : pl.getConfig().getStringList("warn-reasons")) {
            String[] a = s.split("\\|");
            reasons.add(new String[]{a[0], a.length > 1 ? a[1] : "PAPER"});
        }
    }

    public void openReasons(Player staff, OfflinePlayer target, Runnable back) {
        Gui g = new Gui("&8» &c&lVerwarnen: &f" + target.getName(), 3);
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < reasons.size() && i < slots.length; i++) {
            final String[] r = reasons.get(i);
            Material m = Material.matchMaterial(r[1]);
            if (m == null) m = Material.PAPER;
            g.set(slots[i], Util.item(m, "&c" + r[0], "&7Klick = Verwarnung senden"), x -> {
                if (!pl.canWarn(x)) { x.closeInventory(); return; }
                warn(x.getName(), target, r[0]);
                x.closeInventory();
                Util.msg(x, "&f" + target.getName() + "&7 wurde wegen &c" + r[0] + "&7 verwarnt.");
            });
        }
        g.set(22, Util.item(Material.ARROW, "&7Zurück"), x -> { if (back != null) back.run(); else x.closeInventory(); });
        g.fill(0);
        g.open(staff);
    }

    public void warn(String by, OfflinePlayer t, String reason) {
        String path = "players." + t.getUniqueId() + ".warns";
        String entry = System.currentTimeMillis() + "|" + by + "|" + reason;
        List<String> l = new ArrayList<>(pl.data.getStringList(path));
        l.add(entry);
        pl.data.set(path, l);
        List<String> log = new ArrayList<>(pl.data.getStringList("warnlog"));
        log.add(System.currentTimeMillis() + "|" + by + "|" + t.getName() + "|" + reason);
        while (log.size() > 50) log.remove(0);
        pl.data.set("warnlog", log);
        pl.dirty();

        Player on = t.getPlayer();
        if (on != null) effect(on, reason, l.size());
        pl.alertStaff("&c[Warn] &f" + by + " &7→ &f" + t.getName() + "&7: &c" + reason + " &8(Nr. " + l.size() + ")");
    }

    private void effect(Player p, String reason, int n) {
        p.showTitle(Title.title(Util.c("&4&l⚠ VERWARNUNG ⚠"), Util.c("&c" + reason),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(6), Duration.ofMillis(800))));
        BossBar bar = BossBar.bossBar(Util.c("&c&lVERWARNUNG &7| &f" + reason + " &7(Nr. " + n + ")"),
                1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        p.showBossBar(bar);
        Bukkit.getScheduler().runTaskLater(pl, () -> p.hideBossBar(bar), 160L);
        p.sendMessage(Util.c("&4&l⚠ &cDu wurdest verwarnt: &f" + reason + " &8(Nr. " + n + ")"));
        p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1f);
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 24 || !p.isOnline()) { cancel(); return; }
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.5f, (t % 2 == 0) ? 0.7f : 1.3f);
                t++;
            }
        }.runTaskTimer(pl, 10L, 6L);
    }
}
