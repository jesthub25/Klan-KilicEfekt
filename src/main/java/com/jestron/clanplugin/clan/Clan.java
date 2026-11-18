package com.jestron.clanplugin.clan;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Klan verisi. Üye kümeleri thread-safe; asenkron sohbet ve kayıt işlemleri güvenle okuyabilir.
 */
public final class Clan {

    private final String name;
    private final UUID owner;
    private final Set<UUID> members = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> mods = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile int points;
    private volatile int kills;
    private volatile int deaths;
    private volatile int memberLimit;

    public Clan(final String name, final UUID owner, final int memberLimit) {
        this.name = name;
        this.owner = owner;
        this.memberLimit = memberLimit;
        this.members.add(owner);
    }

    public String getName()            { return name; }
    public String getKey()             { return name.toLowerCase(java.util.Locale.ROOT); }
    public UUID getOwner()             { return owner; }
    public int getPoints()             { return points; }
    public void setPoints(final int v) { points = v; }
    public int getKills()              { return kills; }
    public void setKills(final int v)  { kills = v; }
    public int getDeaths()             { return deaths; }
    public void setDeaths(final int v) { deaths = v; }
    public int getMemberLimit()        { return memberLimit; }
    public void setMemberLimit(final int v) { memberLimit = Math.max(1, v); }

    public synchronized void addKill()  { kills++; }
    public synchronized void addDeath() { deaths++; }

    public double getKDR() {
        final int d = deaths;
        if (d == 0) {
            return kills;
        }
        return Math.round(kills / (double) d * 100.0) / 100.0;
    }

    /** Anlık kopya döner. */
    public Set<UUID> getMembers() { return new HashSet<>(members); }
    public Set<UUID> getMods()    { return new HashSet<>(mods); }

    public void addMember(final UUID uuid)    { members.add(uuid); }
    public void removeMember(final UUID uuid) { members.remove(uuid); mods.remove(uuid); }
    public boolean isMember(final UUID uuid)  { return members.contains(uuid); }
    public boolean isOwner(final UUID uuid)   { return owner.equals(uuid); }
    public boolean isMod(final UUID uuid)     { return owner.equals(uuid) || mods.contains(uuid); }
    public void addMod(final UUID uuid)       { if (members.contains(uuid)) { mods.add(uuid); } }
    public void removeMod(final UUID uuid)    { mods.remove(uuid); }
    public int getSize()                      { return members.size(); }
    public boolean isFull()                   { return members.size() >= memberLimit; }

    public String serializeMembers() { return join(members); }
    public String serializeMods()    { return join(mods); }

    private static String join(final Set<UUID> set) {
        final StringBuilder sb = new StringBuilder();
        for (final UUID uuid : set) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(uuid);
        }
        return sb.toString();
    }

    public void deserializeMembers(final String data) {
        members.clear();
        parseInto(data, members);
        members.add(owner);
    }

    public void deserializeMods(final String data) {
        mods.clear();
        parseInto(data, mods);
        mods.retainAll(members);
    }

    private static void parseInto(final String data, final Set<UUID> target) {
        if (data == null || data.isEmpty()) {
            return;
        }
        for (final String part : data.split(",")) {
            final String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                target.add(UUID.fromString(trimmed));
            } catch (final IllegalArgumentException ignored) {
                // bozuk kayıt atlanır
            }
        }
    }
}
