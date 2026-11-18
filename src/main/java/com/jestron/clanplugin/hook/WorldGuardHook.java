package com.jestron.clanplugin.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;

/**
 * WorldGuard'a dokunan TEK sınıf. Sadece WorldGuard yüklüyken çağrılır; böylece
 * WorldGuard olmayan sunucularda hiçbir sınıf yükleme hatası oluşmaz.
 */
public final class WorldGuardHook {

    private WorldGuardHook() {
    }

    /**
     * @param requireRegion true ise konum en az bir bölgenin içinde olmalı
     * @return WorldGuard'a göre bu konumda PvP serbest mi
     */
    public static boolean isPvPAllowed(final Location location, final boolean requireRegion) {
        final RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        final com.sk89q.worldedit.util.Location weLocation = BukkitAdapter.adapt(location);
        if (requireRegion) {
            final ApplicableRegionSet regions = query.getApplicableRegions(weLocation);
            if (regions.size() == 0) {
                return false;
            }
        }
        // Bölge flag'leri + __global__ bölgesi + varsayılan değer birlikte değerlendirilir.
        return query.testState(weLocation, (LocalPlayer) null, Flags.PVP);
    }
}
