package net.noxaria.core;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Drachen-Spitzhacke: levelt durch abgebaute Bloecke, Flaechenabbau bis 8x8. */
public class PickModule implements Listener {
    private static final long[] NEEDED = {0L, 100L, 10_000L, 100_000L, 2_500_000L, 10_000_000L, 100_000_000L, 1_000_000_000L};
    private static final int MAX_LEVEL = NEEDED.length;
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI"};
    private static final Set<Material> NEVER = Set.of(Material.SPAWNER, Material.REINFORCED_DEEPSLATE,
            Material.BEDROCK, Material.END_PORTAL_FRAME, Material.ENDER_CHEST);

    private final Noxaria pl;
    private final NamespacedKey keyId, keyBlocks, keyMode;
    private boolean busy = false;
    // Schluessel der alten Spitzhacke (altes TitanPick-Plugin)
    private final NamespacedKey lId = new NamespacedKey("titanpick", "titan_id");
    private final NamespacedKey lBlocks = new NamespacedKey("titanpick", "titan_blocks");
    private final NamespacedKey lMode = new NamespacedKey("titanpick", "titan_mode");

    public PickModule(Noxaria pl) {
        this.pl = pl;
        keyId = new NamespacedKey(pl, "pick_id");
        keyBlocks = new NamespacedKey(pl, "pick_blocks");
        keyMode = new NamespacedKey(pl, "pick_mode");
    }

    public void registerRecipe() {
        ShapedRecipe r = new ShapedRecipe(new NamespacedKey(pl, "titan_pickaxe"), create());
        r.shape("EEE", " S ", " S ");
        r.setIngredient('E', Material.DRAGON_EGG);
        r.setIngredient('S', Material.STICK);
        Bukkit.addRecipe(r);
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta m = item.getItemMeta();
        m.getPersistentDataContainer().set(keyId, PersistentDataType.BYTE, (byte) 1);
        m.getPersistentDataContainer().set(keyBlocks, PersistentDataType.LONG, 0L);
        m.getPersistentDataContainer().set(keyMode, PersistentDataType.INTEGER, 8);
        refresh(m, 0L);
        item.setItemMeta(m);
        return item;
    }

    /** Wandelt eine alte Spitzhacke in die neue um (Fortschritt bleibt erhalten). */
    public boolean convertLegacy(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        ItemMeta m = it.getItemMeta();
        if (!m.getPersistentDataContainer().has(lId, PersistentDataType.BYTE)) return false;
        long blocks = m.getPersistentDataContainer().getOrDefault(lBlocks, PersistentDataType.LONG, 0L);
        int mode = m.getPersistentDataContainer().getOrDefault(lMode, PersistentDataType.INTEGER, 8);
        m.getPersistentDataContainer().remove(lId);
        m.getPersistentDataContainer().remove(lBlocks);
        m.getPersistentDataContainer().remove(lMode);
        m.getPersistentDataContainer().set(keyId, PersistentDataType.BYTE, (byte) 1);
        m.getPersistentDataContainer().set(keyBlocks, PersistentDataType.LONG, blocks);
        m.getPersistentDataContainer().set(keyMode, PersistentDataType.INTEGER, mode);
        refresh(m, blocks);
        it.setItemMeta(m);
        return true;
    }

    public int convertAll(Player p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (convertLegacy(it)) { p.getInventory().setItem(i, it); n++; }
        }
        return n;
    }

    private boolean isPick(ItemStack i) {
        return i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(keyId, PersistentDataType.BYTE);
    }

    private static int levelOf(long blocks) {
        int lvl = 1;
        for (int i = 0; i < NEEDED.length; i++) if (blocks >= NEEDED[i]) lvl = i + 1;
        return lvl;
    }

    private static int sizeOf(int lvl, int mode) {
        if (lvl < 3) return 1;
        if (lvl < MAX_LEVEL) return lvl;
        return mode;
    }

    private void refresh(ItemMeta m, long blocks) {
        int lvl = levelOf(blocks);
        int mode = m.getPersistentDataContainer().getOrDefault(keyMode, PersistentDataType.INTEGER, 8);
        for (Enchantment e : new ArrayList<>(m.getEnchants().keySet())) m.removeEnchant(e);
        if (lvl >= 2) m.addEnchant(Enchantment.EFFICIENCY, lvl == 2 ? 1 : 8, true);
        if (lvl >= 3) {
            m.addEnchant(Enchantment.UNBREAKING, 10, true);
            m.addEnchant(Enchantment.MENDING, 1, true);
        }
        m.setItemModel(new NamespacedKey("noxaria", "titan_pick"));
        m.displayName(Util.c("&5&lDrachen-Spitzhacke &7[Lv. " + lvl + "]"));
        List<String> lore = new ArrayList<>();
        lore.add("&7Level: &e" + lvl + "&7/" + MAX_LEVEL);
        lore.add("&7Abgebaut: &a" + Util.fmt(blocks));
        lore.add(lvl < MAX_LEVEL ? "&7Naechstes Level bei: &e" + Util.fmt(NEEDED[lvl]) : "&6&lMAX LEVEL");
        if (lvl >= 3) {
            int size = sizeOf(lvl, mode);
            lore.add("");
            lore.add("&5Titanenbruch " + ROMAN[lvl - 3]);
            lore.add("&7Abbaugebiet: &f" + (size == 1 ? "normal" : size + "x" + size));
            if (lvl == MAX_LEVEL) lore.add("&8Hand tauschen (F) = Groesse wechseln");
        }
        m.lore(Util.cl(lore));
    }

    public void setBlocks(Player p, long n) {
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (!isPick(tool)) { Util.msg(p, "Halte die Drachen-Spitzhacke in der Hand."); return; }
        ItemMeta m = tool.getItemMeta();
        m.getPersistentDataContainer().set(keyBlocks, PersistentDataType.LONG, n);
        refresh(m, n);
        tool.setItemMeta(m);
        Util.msg(p, "Abgebaute Bloecke: " + Util.fmt(n));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (busy) return;
        Player p = e.getPlayer();
        ItemStack tool = p.getInventory().getItemInMainHand();
        convertLegacy(tool);
        if (!isPick(tool)) return;
        ItemMeta m = tool.getItemMeta();
        long blocks = m.getPersistentDataContainer().getOrDefault(keyBlocks, PersistentDataType.LONG, 0L);
        int mode = m.getPersistentDataContainer().getOrDefault(keyMode, PersistentDataType.INTEGER, 8);
        int lvlBefore = levelOf(blocks);
        int size = sizeOf(lvlBefore, mode);

        long gained = 1;
        if (size > 1) {
            busy = true;
            try { gained += breakArea(p, e.getBlock(), size); } finally { busy = false; }
        }
        ItemStack cur = p.getInventory().getItemInMainHand();
        if (!isPick(cur)) return;
        ItemMeta cm = cur.getItemMeta();
        long total = blocks + gained;
        cm.getPersistentDataContainer().set(keyBlocks, PersistentDataType.LONG, total);
        int lvlAfter = levelOf(total);
        if (lvlAfter > lvlBefore) {
            if (lvlAfter == MAX_LEVEL) cm.getPersistentDataContainer().set(keyMode, PersistentDataType.INTEGER, 8);
            p.showTitle(Title.title(Util.c("&6&lLEVEL UP!"), Util.c("&7Drachen-Spitzhacke &eLv. " + lvlAfter)));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        refresh(cm, total);
        cur.setItemMeta(cm);
    }

    private int breakArea(Player p, Block origin, int size) {
        RayTraceResult ray = p.rayTraceBlocks(7.0);
        BlockFace face = (ray != null && ray.getHitBlockFace() != null) ? ray.getHitBlockFace() : BlockFace.UP;
        int lo = -((size - 1) / 2), hi = size / 2;
        int broken = 0;
        for (int a = lo; a <= hi; a++) {
            for (int b = lo; b <= hi; b++) {
                if (a == 0 && b == 0) continue;
                Block t = switch (face) {
                    case UP, DOWN -> origin.getRelative(a, 0, b);
                    case NORTH, SOUTH -> origin.getRelative(a, b, 0);
                    default -> origin.getRelative(0, b, a);
                };
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (!isPick(hand)) return broken;
                Material type = t.getType();
                if (type.isAir() || t.isLiquid() || NEVER.contains(type) || type.getHardness() < 0) continue;
                if (!t.isPreferredTool(hand)) continue;
                if (p.breakBlock(t)) broken++;
            }
        }
        return broken;
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (!isPick(e.getOffHandItem())) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (!isPick(tool)) return;
        ItemMeta m = tool.getItemMeta();
        long blocks = m.getPersistentDataContainer().getOrDefault(keyBlocks, PersistentDataType.LONG, 0L);
        if (levelOf(blocks) < MAX_LEVEL) {
            p.sendActionBar(Util.c("&cDie Groesse kannst du erst auf Level " + MAX_LEVEL + " wechseln."));
            return;
        }
        int mode = m.getPersistentDataContainer().getOrDefault(keyMode, PersistentDataType.INTEGER, 8);
        mode = mode % 8 + 1;
        m.getPersistentDataContainer().set(keyMode, PersistentDataType.INTEGER, mode);
        refresh(m, blocks);
        tool.setItemMeta(m);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        p.sendActionBar(Util.c("&5Titanenbruch: &f" + (mode == 1 ? "normal" : mode + "x" + mode)));
    }
}
