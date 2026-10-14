package net.noxaria.core;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Raenge: Rechte ohne OP, Prefix in Tabliste, ueber dem Kopf und im Chat. */
public class RankModule implements Listener {
    private final Noxaria pl;
    private final Map<UUID, PermissionAttachment> att = new HashMap<>();

    public RankModule(Noxaria pl) { this.pl = pl; }

    public String groupOf(UUID u) {
        String g = pl.data.getString("players." + u + ".group", "spieler");
        return pl.getConfig().isConfigurationSection("groups." + g) ? g : "spieler";
    }

    private ConfigurationSection grp(String id) { return pl.getConfig().getConfigurationSection("groups." + id); }

    public String prefixOf(UUID u) {
        ConfigurationSection s = grp(groupOf(u));
        return s != null ? s.getString("prefix", "&7") : "&7";
    }

    public void setGroup(OfflinePlayer t, String id) {
        pl.data.set("players." + t.getUniqueId() + ".group", id);
        pl.dirty();
        Player on = t.getPlayer();
        if (on != null) apply(on);
    }

    public void apply(Player p) {
        String id = groupOf(p.getUniqueId());
        ConfigurationSection s = grp(id);
        if (s == null) return;

        PermissionAttachment old = att.remove(p.getUniqueId());
        if (old != null) p.removeAttachment(old);
        PermissionAttachment a = p.addAttachment(pl);
        for (String perm : s.getStringList("permissions")) a.setPermission(perm, true);
        att.put(p.getUniqueId(), a);
        p.recalculatePermissions();
        p.updateCommands();

        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team t : sb.getTeams()) {
            if (t.getName().startsWith("nx") && t.hasEntry(p.getName())) t.removeEntry(p.getName());
        }
        String tn = "nx" + String.format("%02d", Math.max(0, 99 - s.getInt("weight", 0))) + id;
        if (tn.length() > 16) tn = tn.substring(0, 16);
        Team team = sb.getTeam(tn);
        if (team == null) team = sb.registerNewTeam(tn);
        team.prefix(Util.c(s.getString("prefix", "&7")));
        team.addEntry(p.getName());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChat(AsyncChatEvent e) {
        final Component prefix = Util.c(prefixOf(e.getPlayer().getUniqueId()));
        e.renderer(ChatRenderer.viewerUnaware((source, name, message) ->
                Component.empty()
                        .append(prefix)
                        .append(Component.text(source.getName(), NamedTextColor.WHITE))
                        .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                        .append(message.colorIfAbsent(NamedTextColor.WHITE))));
    }
}
