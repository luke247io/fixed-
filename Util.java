package net.noxaria.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

final class Util {
    private Util() {}

    static Component c(String s) {
        return LegacyComponentSerializer.legacySection().deserialize(s.replace('&', '§')).decoration(TextDecoration.ITALIC, false);
    }

    static List<Component> cl(List<String> l) {
        List<Component> r = new ArrayList<>();
        for (String s : l) r.add(c(s));
        return r;
    }

    static String fmt(long n) { return String.format(Locale.GERMANY, "%,d", n); }

    static String money(double d) { return String.format(Locale.GERMANY, "%,.2f $", d); }

    static String date(long ms) {
        if (ms <= 0) return "nie";
        return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }

    static ItemStack item(Material m, String name, String... lore) { return item(m, name, Arrays.asList(lore)); }

    static ItemStack item(Material m, String name, List<String> lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta im = i.getItemMeta();
        im.displayName(c(name));
        if (!lore.isEmpty()) im.lore(cl(lore));
        im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        i.setItemMeta(im);
        return i;
    }

    static ItemStack head(OfflinePlayer op, String name, List<String> lore) {
        ItemStack i = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) i.getItemMeta();
        m.setOwningPlayer(op);
        m.displayName(c(name));
        if (!lore.isEmpty()) m.lore(cl(lore));
        i.setItemMeta(m);
        return i;
    }

    static String toB64(ItemStack i) { return Base64.getEncoder().encodeToString(i.serializeAsBytes()); }

    static ItemStack fromB64(String s) {
        try { return ItemStack.deserializeBytes(Base64.getDecoder().decode(s)); } catch (Exception e) { return null; }
    }

    static void click(Player p) { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.3f); }

    static void msg(CommandSender s, String m) { s.sendMessage(c("&5Noxaria &8» &7" + m)); }
}
