package net.noxaria.core;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** /spawnstash: Koeder-Versteck (Spawner, Fass, brennende Kerze). Admins werden gewarnt, wenn jemand es oeffnet oder abbaut. */
public class StashModule implements Listener {
    private final Noxaria pl;

    public StashModule(Noxaria pl) { this.pl = pl; }

    private static String key(Location l) { return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ(); }

    private boolean isStash(Location l) { return pl.data.getStringList("stash.locs").contains(key(l)); }

    public void spawn(Player p) {
        BlockFace f = p.getFacing();
        Block base = p.getLocation().getBlock();
        Block spawner = base.getRelative(f, 2);
        Block barrel = base.getRelative(f, 3);
        Block candle = barrel.getRelative(BlockFace.UP);
        if (!spawner.getType().isAir() || !barrel.getType().isAir() || !candle.getType().isAir()) {
            Util.msg(p, "&cDavor ist nicht genug freier Platz. Such dir eine freie Stelle.");
            return;
        }
        spawner.setType(Material.SPAWNER);
        if (spawner.getState() instanceof CreatureSpawner cs) {
            cs.setSpawnedType(EntityType.ZOMBIE);
            cs.setRequiredPlayerRange(0);
            cs.update();
        }
        barrel.setType(Material.BARREL);
        if (barrel.getState() instanceof Barrel b) {
            for (String s : pl.getConfig().getStringList("stash.bait")) {
                String[] a = s.split(":");
                Material m = Material.matchMaterial(a[0]);
                if (m == null) continue;
                int amt = 1;
                try { if (a.length > 1) amt = Integer.parseInt(a[1]); } catch (NumberFormatException ignored) { }
                b.getInventory().addItem(new ItemStack(m, amt));
            }
        }
        candle.setType(Material.CANDLE);
        if (candle.getBlockData() instanceof org.bukkit.block.data.type.Candle cd) {
            cd.setLit(true);
            candle.setBlockData(cd);
        }
        List<String> locs = new ArrayList<>(pl.data.getStringList("stash.locs"));
        locs.add(key(spawner.getLocation()));
        locs.add(key(barrel.getLocation()));
        locs.add(key(candle.getLocation()));
        pl.data.set("stash.locs", locs);
        pl.dirty();
        Util.msg(p, "Versteck erstellt. Du bekommst eine Meldung, wenn jemand es oeffnet oder abbaut.");
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Location l = e.getBlock().getLocation();
        if (!isStash(l) || pl.isStaff(e.getPlayer())) return;
        pl.alertStaff("&4[Koeder] &f" + e.getPlayer().getName() + " &7hat einen Koeder-Block abgebaut: &f" + e.getBlock().getType()
                + " &7(" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + ")");
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getInventory().getHolder() instanceof Barrel b)) return;
        if (!(e.getPlayer() instanceof Player p) || pl.isStaff(p)) return;
        if (!isStash(b.getLocation())) return;
        pl.alertStaff("&4[Koeder] &f" + p.getName() + " &7hat das Koeder-Fass geoeffnet (" + b.getX() + " " + b.getY() + " " + b.getZ() + ")");
    }
}
