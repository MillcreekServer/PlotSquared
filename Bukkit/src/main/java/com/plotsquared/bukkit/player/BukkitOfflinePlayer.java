/*
 *       _____  _       _    _____                                _
 *      |  __ \| |     | |  / ____|                              | |
 *      | |__) | | ___ | |_| (___   __ _ _   _  __ _ _ __ ___  __| |
 *      |  ___/| |/ _ \| __|\___ \ / _` | | | |/ _` | '__/ _ \/ _` |
 *      | |    | | (_) | |_ ____) | (_| | |_| | (_| | | |  __/ (_| |
 *      |_|    |_|\___/ \__|_____/ \__, |\__,_|\__,_|_|  \___|\__,_|
 *                                    | |
 *                                    |_|
 *            PlotSquared plot management system for Minecraft
 *                  Copyright (C) 2021 IntellectualSites
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.plotsquared.bukkit.player;

import com.plotsquared.core.permissions.NullPermissionProfile;
import com.plotsquared.core.permissions.PermissionHandler;
import com.plotsquared.core.permissions.PermissionProfile;
import com.plotsquared.core.player.OfflinePlotPlayer;
import org.bukkit.OfflinePlayer;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.UUID;

public class BukkitOfflinePlayer implements OfflinePlotPlayer {

    public final OfflinePlayer player;
    private final PermissionProfile permissionProfile;

    /**
     * Please do not use this method. Instead use BukkitUtil.getPlayer(Player),
     * as it caches player objects.
     *
     * @param player Bukkit OfflinePlayer player to convert
     * @param permissionHandler Permission Profile to be used
     */
    public BukkitOfflinePlayer(@Nonnull final OfflinePlayer player, @Nonnull final
        PermissionHandler permissionHandler) {
        this.player = player;
        this.permissionProfile = permissionHandler.getPermissionProfile(this)
            .orElse(NullPermissionProfile.INSTANCE);
    }

    @Nonnull @Override public UUID getUUID() {
        return this.player.getUniqueId();
    }

    @Override @Nonnegative public long getLastPlayed() {
        return this.player.getLastSeen();
    }

    @Override public String getName() {
        return this.player.getName();
    }

    @Override public boolean hasPermission(@Nullable final String world,
                                           @Nonnull final String permission) {
        return this.permissionProfile.hasPermission(world, permission);
    }

}
