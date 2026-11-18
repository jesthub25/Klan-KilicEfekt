package com.jestron.clanplugin.clan;

import com.jestron.clanplugin.ClanPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Oyuncu → klan davetleri. Sadece ana thread'de kullanılır; süre kontrolü zaman damgasıyla yapılır.
 */
public final class InviteManager {

    public static final class Invite {
        private final String clanKey;
        private final UUID inviter;
        private final long createdAt;

        Invite(final String clanKey, final UUID inviter, final long createdAt) {
            this.clanKey = clanKey;
            this.inviter = inviter;
            this.createdAt = createdAt;
        }

        public String clanKey() { return clanKey; }
        public UUID inviter()   { return inviter; }
        public long createdAt() { return createdAt; }
    }

    private final ClanPlugin plugin;
    private final Map<UUID, List<Invite>> invites = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public InviteManager(final ClanPlugin plugin) {
        this.plugin = plugin;
    }

    public long getCooldownRemaining(final UUID inviter) {
        final Long last = cooldowns.get(inviter);
        if (last == null) {
            return 0L;
        }
        final long cdMs = plugin.getConfig().getInt("clan.invite-cooldown", 5) * 1000L;
        final long remaining = cdMs - (System.currentTimeMillis() - last);
        return remaining <= 0 ? 0L : (remaining + 999L) / 1000L;
    }

    public void send(final Clan clan, final UUID inviter, final UUID target) {
        final List<Invite> list = invites.computeIfAbsent(target, k -> new ArrayList<>());
        list.removeIf(inv -> inv.clanKey().equals(clan.getKey()));
        list.add(new Invite(clan.getKey(), inviter, System.currentTimeMillis()));
        cooldowns.put(inviter, System.currentTimeMillis());
    }

    public List<Invite> getInvites(final UUID target) {
        final List<Invite> list = invites.get(target);
        if (list == null) {
            return Collections.emptyList();
        }
        final long timeoutMs = plugin.getConfig().getInt("clan.invite-timeout", 60) * 1000L;
        final long now = System.currentTimeMillis();
        list.removeIf(inv -> now - inv.createdAt() > timeoutMs
                || plugin.getClanManager().getClan(inv.clanKey()) == null);
        if (list.isEmpty()) {
            invites.remove(target);
            return Collections.emptyList();
        }
        return new ArrayList<>(list);
    }

    /** İsim verilirse o klanın davetini, verilmezse en yenisini döner (listeden çıkarmaz). */
    public Invite find(final UUID target, final String clanName) {
        final List<Invite> valid = getInvites(target);
        if (valid.isEmpty()) {
            return null;
        }
        if (clanName == null) {
            return valid.get(valid.size() - 1);
        }
        final String k = clanName.toLowerCase(Locale.ROOT);
        for (final Invite inv : valid) {
            if (inv.clanKey().equals(k)) {
                return inv;
            }
        }
        return null;
    }

    public void clear(final UUID target) {
        invites.remove(target);
    }

    public void forgetPlayer(final UUID uuid) {
        cooldowns.remove(uuid);
    }
}
