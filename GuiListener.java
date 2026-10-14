package net.noxaria.core;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

public class GuiListener implements Listener {
    private final Noxaria pl;
    public GuiListener(Noxaria pl) { this.pl = pl; }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Gui g)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null) { return; }
        int top = e.getView().getTopInventory().getSize();
        boolean inTop = e.getRawSlot() < top;

        if (!g.editable) {
            e.setCancelled(true);
            if (inTop) {
                Consumer<Player> a = g.acts.get(e.getRawSlot());
                if (a != null) { Util.click(p); a.accept(p); }
            }
            return;
        }

        // editierbar (Verkaufsmenue)
        if (inTop && (g.locked.contains(e.getRawSlot()) || g.acts.containsKey(e.getRawSlot()))) {
            e.setCancelled(true);
            Consumer<Player> a = g.acts.get(e.getRawSlot());
            if (a != null) { Util.click(p); a.accept(p); }
            return;
        }
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) { e.setCancelled(true); return; }
        if (e.isShiftClick() && !inTop) {
            e.setCancelled(true);
            ItemStack cur = e.getCurrentItem();
            if (cur != null && !cur.getType().isAir()) {
                for (int s = 0; s < top; s++) {
                    if (g.locked.contains(s)) continue;
                    if (g.getInventory().getItem(s) == null) {
                        g.getInventory().setItem(s, cur.clone());
                        e.setCurrentItem(null);
                        break;
                    }
                }
            }
        }
        if (g.onChange != null) Bukkit.getScheduler().runTask(pl, g.onChange);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Gui g)) return;
        int top = e.getView().getTopInventory().getSize();
        for (int s : e.getRawSlots()) {
            if (s < top && (!g.editable || g.locked.contains(s))) { e.setCancelled(true); return; }
        }
        if (g.onChange != null) Bukkit.getScheduler().runTask(pl, g.onChange);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Gui g && g.onClose != null && e.getPlayer() instanceof Player p) {
            g.onClose.accept(p);
        }
    }
}
