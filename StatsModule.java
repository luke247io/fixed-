package net.noxaria.core;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Einfache X-Ray-Hinweise: Verhaeltnis Stein zu Erzen. Kein Beweis, nur ein Hinweis fuer Admins. */
public class StatsModule implements Listener {
    private static final Set<Material> STONE = Set.of(Material.STONE, Material.DEEPSLATE, Material.ANDESITE,
            Material.DIORITE, Material.GRANITE, Material.TUFF);
    private static final Set<Material> ORES = Set.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.ANCIENT_DEBRIS);

    private final Noxaria pl;
    private final Map<UUID, Long> lastAlert = new HashMap<>();

    public StatsModule(Noxaria pl) { this.pl = pl; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL) return;
        Material t = e.getBlock().getType();
        String base = "players." + p.getUniqueId() + ".mined.";
        if (STONE.contains(t)) {
            pl.data.set(base + "stone", pl.data.getInt(base + "stone") + 1);
            pl.dirty();
        } else if (ORES.contains(t)) {
            int ores = pl.data.getInt(base + "ores") + 1;
            pl.data.set(base + "ores", ores);
            pl.dirty();
            int stone = pl.data.getInt(base + "stone");
            long now = System.currentTimeMillis();
            if (ores >= 8 && stone / (double) ores < 120 && now - lastAlert.getOrDefault(p.getUniqueId(), 0L) > 300_000L && !pl.isStaff(p)) {
                lastAlert.put(p.getUniqueId(), now);
                pl.alertStaff("&6[X-Ray?] &f" + p.getName() + "&7: &b" + ores + " &7Diamant/Debris vs. &f" + stone
                        + " &7Stein (1:" + (int) (stone / (double) ores) + ") &8- nur ein Hinweis, kein Beweis");
            }
        }
    }

    public String ratio(UUID id) {
        int ores = pl.data.getInt("players." + id + ".mined.ores");
        int stone = pl.data.getInt("players." + id + ".mined.stone");
        return ores + " Erze / " + stone + " Stein" + (ores > 0 ? " (1:" + (int) (stone / (double) ores) + ")" : "");
    }
}
