package net.noxaria.core;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Kleines Menue-System mit Klick-Sounds und Aufbau-Animation (Reihe fuer Reihe). */
public class Gui implements InventoryHolder {
    private final Inventory inv;
    final Map<Integer, ItemStack> items = new LinkedHashMap<>();
    final Map<Integer, Consumer<Player>> acts = new HashMap<>();
    final Set<Integer> locked = new HashSet<>();
    boolean editable = false;
    Runnable onChange;
    Consumer<Player> onClose;

    static final ItemStack FILLER = Util.item(Material.BLACK_STAINED_GLASS_PANE, " ");

    public Gui(String title, int rows) {
        inv = Bukkit.createInventory(this, rows * 9, Util.c(title));
    }

    @Override
    public Inventory getInventory() { return inv; }

    public Gui set(int slot, ItemStack it, Consumer<Player> a) {
        items.put(slot, it);
        if (a != null) acts.put(slot, a);
        return this;
    }

    public Gui set(int slot, ItemStack it) { return set(slot, it, null); }

    /** Fuellt alle freien Slots ab 'from' mit Glas (und sperrt sie). */
    public Gui fill(int from) {
        for (int s = from; s < inv.getSize(); s++) {
            if (!items.containsKey(s)) items.put(s, FILLER);
            locked.add(s);
        }
        return this;
    }

    public void open(Player p, boolean animate) {
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.5f, 1.6f);
        if (!animate) {
            items.forEach(inv::setItem);
            return;
        }
        final int rows = inv.getSize() / 9;
        new BukkitRunnable() {
            int r = 0;
            @Override
            public void run() {
                if (r >= rows) { cancel(); return; }
                for (int c = 0; c < 9; c++) {
                    int s = r * 9 + c;
                    ItemStack it = items.get(s);
                    if (it != null) inv.setItem(s, it);
                }
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.25f, 0.8f + 0.12f * r);
                r++;
            }
        }.runTaskTimer(Noxaria.get(), 0L, 1L);
    }

    public void open(Player p) { open(p, true); }
}
