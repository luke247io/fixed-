package net.noxaria.core;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Titan-Schwert: 10 Kills = 1 Level, Level 10 braucht 30 verschiedene getoetete Spieler. */
public class SwordModule implements Listener {
    private static final int MAX = 10;
    private static final int NEED_VICTIMS = 30;

    private final Noxaria pl;
    private final NamespacedKey kId, kKills, kVictims;

    public SwordModule(Noxaria pl) {
        this.pl = pl;
        kId = new NamespacedKey(pl, "sword_id");
        kKills = new NamespacedKey(pl, "sword_kills");
        kVictims = new NamespacedKey(pl, "sword_victims");
    }

    public void registerRecipe() {
        ShapedRecipe r = new ShapedRecipe(new NamespacedKey(pl, "titan_sword"), create());
        r.shape("N", "N", "S");
        r.setIngredient('N', Material.NETHERITE_INGOT);
        r.setIngredient('S', Material.STICK);
        Bukkit.addRecipe(r);
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta m = item.getItemMeta();
        m.getPersistentDataContainer().set(kId, PersistentDataType.BYTE, (byte) 1);
        m.getPersistentDataContainer().set(kKills, PersistentDataType.INTEGER, 0);
        m.getPersistentDataContainer().set(kVictims, PersistentDataType.STRING, "");
        refresh(m, 0, 0);
        item.setItemMeta(m);
        return item;
    }

    private boolean isSword(ItemStack i) {
        return i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(kId, PersistentDataType.BYTE);
    }

    static int levelOf(int kills, int victims) {
        int l = Math.min(MAX, 1 + kills / 10);
        if (l >= MAX && victims < NEED_VICTIMS) l = MAX - 1;
        return l;
    }

    private static int blut(int l) { return l >= 3 ? 1 + (l - 3) / 3 : 0; }
    private static int frost(int l) { return l >= 5 ? 1 + (l - 5) / 3 : 0; }
    private static int donner(int l) { return l >= 7 ? l - 6 : 0; }

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V"};
        return n < r.length ? r[n] : String.valueOf(n);
    }

    private void refresh(ItemMeta m, int kills, int victims) {
        int l = levelOf(kills, victims);
        for (Enchantment e : new ArrayList<>(m.getEnchants().keySet())) m.removeEnchant(e);
        m.addEnchant(Enchantment.SHARPNESS, 1 + l, true);
        if (l >= 2) m.addEnchant(Enchantment.UNBREAKING, l, true);
        if (l >= 3) m.addEnchant(Enchantment.LOOTING, 3, true);
        if (l >= 4) m.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
        if (l >= 5) m.addEnchant(Enchantment.MENDING, 1, true);
        if (l >= 6) m.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);
        m.setItemModel(new NamespacedKey("noxaria", "titan_sword"));
        m.displayName(Util.c("&b&lTitan-Schwert &7[Lv. " + l + "]"));

        List<String> lore = new ArrayList<>();
        lore.add("&7Level: &e" + l + "&7/" + MAX);
        lore.add("&7Kills: &a" + kills + (l < MAX ? " &8(naechstes Level bei " + (l * 10) + ")" : ""));
        if (l >= MAX - 1) lore.add("&7Verschiedene Spieler: &f" + victims + "&7/" + NEED_VICTIMS);
        lore.add("");
        if (blut(l) > 0) lore.add("&cBlutdurst " + roman(blut(l)) + " &8- heilt dich bei Treffern");
        if (frost(l) > 0) lore.add("&bFrostbiss " + roman(frost(l)) + " &8- verlangsamt Gegner");
        if (donner(l) > 0) lore.add("&eDonnerhieb " + roman(donner(l)) + " &8- Blitz und Extra-Schaden");
        if (l >= MAX) lore.add("&5Seelenernte &8- Kill gibt Regeneration und Schild");
        if (l == MAX - 1 && victims < NEED_VICTIMS) lore.add("&8Level 10: " + NEED_VICTIMS + " verschiedene Spieler toeten");
        m.lore(Util.cl(lore));
    }

    private static Set<String> victimSet(String s) {
        Set<String> r = new LinkedHashSet<>();
        if (s != null && !s.isEmpty()) for (String x : s.split(",")) r.add(x);
        return r;
    }

    public void setKills(Player p, int kills, int fakeVictims) {
        ItemStack it = p.getInventory().getItemInMainHand();
        if (!isSword(it)) { Util.msg(p, "Halte das Titan-Schwert in der Hand."); return; }
        ItemMeta m = it.getItemMeta();
        Set<String> v = new LinkedHashSet<>();
        for (int i = 0; i < fakeVictims; i++) v.add(UUID.randomUUID().toString());
        m.getPersistentDataContainer().set(kKills, PersistentDataType.INTEGER, kills);
        m.getPersistentDataContainer().set(kVictims, PersistentDataType.STRING, String.join(",", v));
        refresh(m, kills, v.size());
        it.setItemMeta(m);
        Util.msg(p, "Kills gesetzt.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity t)) return;
        ItemStack it = p.getInventory().getItemInMainHand();
        if (!isSword(it)) return;
        ItemMeta m = it.getItemMeta();
        int kills = m.getPersistentDataContainer().getOrDefault(kKills, PersistentDataType.INTEGER, 0);
        int vic = victimSet(m.getPersistentDataContainer().get(kVictims, PersistentDataType.STRING)).size();
        int l = levelOf(kills, vic);

        int b = blut(l);
        if (b > 0) {
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            double mh = max != null ? max.getValue() : 20.0;
            p.setHealth(Math.min(mh, p.getHealth() + 0.5 * b));
        }
        int f = frost(l);
        if (f > 0) t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40 + 20 * f, f));
        int d = donner(l);
        if (d > 0 && Math.random() < 0.15 * d) {
            t.getWorld().strikeLightningEffect(t.getLocation());
            e.setDamage(e.getDamage() + 2.0 * d);
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null) return;
        ItemStack it = k.getInventory().getItemInMainHand();
        if (!isSword(it)) return;
        ItemMeta m = it.getItemMeta();
        int kills = m.getPersistentDataContainer().getOrDefault(kKills, PersistentDataType.INTEGER, 0) + 1;
        Set<String> v = victimSet(m.getPersistentDataContainer().get(kVictims, PersistentDataType.STRING));
        if (e.getEntity() instanceof Player victim && !victim.equals(k) && v.size() < NEED_VICTIMS) {
            v.add(victim.getUniqueId().toString());
        }
        int before = levelOf(kills - 1, victimSet(m.getPersistentDataContainer().get(kVictims, PersistentDataType.STRING)).size());
        int after = levelOf(kills, v.size());
        m.getPersistentDataContainer().set(kKills, PersistentDataType.INTEGER, kills);
        m.getPersistentDataContainer().set(kVictims, PersistentDataType.STRING, String.join(",", v));
        refresh(m, kills, v.size());
        it.setItemMeta(m);

        if (after > before) {
            k.showTitle(Title.title(Util.c("&b&lLEVEL UP!"), Util.c("&7Titan-Schwert &eLv. " + after)));
            k.playSound(k.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        if (after >= MAX) {
            k.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 1));
            k.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
        }
    }
}
