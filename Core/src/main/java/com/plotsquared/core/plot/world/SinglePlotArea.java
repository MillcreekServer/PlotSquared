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
package com.plotsquared.core.plot.world;

import com.plotsquared.core.PlotSquared;
import com.plotsquared.core.configuration.ConfigurationNode;
import com.plotsquared.core.configuration.ConfigurationSection;
import com.plotsquared.core.configuration.ConfigurationUtil;
import com.plotsquared.core.configuration.file.YamlConfiguration;
import com.plotsquared.core.generator.GridPlotWorld;
import com.plotsquared.core.generator.SingleWorldGenerator;
import com.plotsquared.core.inject.annotations.WorldConfig;
import com.plotsquared.core.listener.PlotListener;
import com.plotsquared.core.location.Location;
import com.plotsquared.core.location.PlotLoc;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.PlotAreaType;
import com.plotsquared.core.plot.PlotId;
import com.plotsquared.core.plot.PlotManager;
import com.plotsquared.core.plot.PlotSettings;
import com.plotsquared.core.plot.flag.FlagContainer;
import com.plotsquared.core.queue.GlobalBlockQueue;
import com.plotsquared.core.setup.PlotAreaBuilder;
import com.plotsquared.core.setup.SettingsNodesWrapper;
import com.plotsquared.core.util.EventDispatcher;
import com.plotsquared.core.util.task.TaskManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class SinglePlotArea extends GridPlotWorld {

    public boolean VOID = false;

    private final EventDispatcher eventDispatcher;
    private final PlotListener plotListener;

    public SinglePlotArea(@Nonnull final PlotAreaManager plotAreaManager,
                          @Nonnull final EventDispatcher eventDispatcher,
                          @Nonnull final PlotListener plotListener,
                          @WorldConfig @Nonnull final YamlConfiguration worldConfiguration,
                          @Nonnull final GlobalBlockQueue globalBlockQueue) {
        super("*", null, new SingleWorldGenerator(plotAreaManager), null, null,
            worldConfiguration, globalBlockQueue);
        this.eventDispatcher = eventDispatcher;
        this.plotListener = plotListener;
        this.setAllowSigns(false);
        this.setDefaultHome(new PlotLoc(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Nonnull @Override protected PlotManager createManager() {
        return new SinglePlotManager(this);
    }

    @Override public void loadConfiguration(ConfigurationSection config) {
        VOID = config.getBoolean("void", false);
    }

    @Override public void saveConfiguration(ConfigurationSection config) {
        super.saveConfiguration(config);
    }

    public void loadWorld(final PlotId id) {
        String worldName = id.getX() + "." + id.getY();
        if (PlotSquared.platform().worldUtil().isWorld(worldName)) {
            return;
        }
        PlotAreaBuilder builder = PlotAreaBuilder.newBuilder()
                .plotManager("PlotSquared:single")
                .generatorName("PlotSquared:single")
                .plotAreaType(getType())
                .terrainType(getTerrain())
                .settingsNodesWrapper(new SettingsNodesWrapper(new ConfigurationNode[0], null))
                .worldName(worldName);

        File container = PlotSquared.platform().worldContainer();
        File destination = new File(container, worldName);

        {// convert old
            File oldFile = new File(container, id.toCommaSeparatedString());
            if (oldFile.exists()) {
                oldFile.renameTo(destination);
            }
        }
        // Duplicate 0;0
        if (builder.plotAreaType() != PlotAreaType.NORMAL) {
            if (!destination.exists()) {
                File src = new File(container, "0.0");
                if (src.exists()) {
                    if (!destination.exists()) {
                        destination.mkdirs();
                    }
                    File levelDat = new File(src, "level.dat");
                    if (levelDat.exists()) {
                        try {
                            Files.copy(levelDat.toPath(),
                                new File(destination, levelDat.getName()).toPath());
                            File data = new File(src, "data");
                            if (data.exists()) {
                                File dataDest = new File(destination, "data");
                                dataDest.mkdirs();
                                for (File file : data.listFiles()) {
                                    Files.copy(file.toPath(),
                                        new File(dataDest, file.getName()).toPath());
                                }
                            }
                        } catch (IOException exception) {
                            exception.printStackTrace();
                        }
                    }
                }
            }
        }

        try {
            TaskManager.getPlatformImplementation().sync(() -> {
                final String name = id.getX() + "." + id.getY();
                if (!PlotSquared.platform().worldUtil().isWorld(name)) {
                    PlotSquared.platform().setupUtils().setupWorld(builder);
                }
                return null;
            });
        } catch (final Exception e) {
            e.printStackTrace();
        }

        //        String worldName = plot.getWorldName();
        //        World world = Bukkit.getWorld(worldName);
        //        if (world != null) {
        //            return world;
        //        }
        //        WorldCreator wc = new WorldCreator(worldName);
        //        wc.generator("PlotSquared:single");
        //        wc.environment(World.Environment.NORMAL);
        //        wc.type(WorldType.FLAT);
        //        return AsyncWorld.create(wc);
    }



    @Override public ConfigurationNode[] getSettingNodes() {
        return new ConfigurationNode[] {
            new ConfigurationNode("void", this.VOID, "Void world", ConfigurationUtil.BOOLEAN)};
    }

    @Nullable @Override public Plot getOwnedPlot(@Nonnull final Location location) {
        PlotId pid = PlotId.fromStringOrNull(location.getWorldName());
        Plot plot = pid == null ? null : this.plots.get(pid);
        return plot == null ? null : plot.getBasePlot(false);
    }

    @Nullable @Override public Plot getOwnedPlotAbs(@Nonnull Location location) {
        PlotId pid = PlotId.fromStringOrNull(location.getWorldName());
        return pid == null ? null : plots.get(pid);
    }

    @Nullable @Override public Plot getPlot(@Nonnull final Location location) {
        PlotId pid = PlotId.fromStringOrNull(location.getWorldName());
        return pid == null ? null : getPlot(pid);
    }

    @Nullable @Override public Plot getPlotAbs(@Nonnull final Location location) {
        final PlotId pid = PlotId.fromStringOrNull(location.getWorldName());
        return pid == null ? null : getPlotAbs(pid);
    }

    public boolean addPlot(@Nonnull Plot plot) {
        plot = adapt(plot);
        return super.addPlot(plot);
    }

    @Override public boolean addPlotAbs(@Nonnull Plot plot) {
        plot = adapt(plot);
        return super.addPlotAbs(plot);
    }

    @Override public boolean addPlotIfAbsent(@Nonnull Plot plot) {
        plot = adapt(plot);
        return super.addPlotIfAbsent(plot);
    }

    @Override
    public boolean allowSigns() {
        return false; // do not create signs for single plots
    }

    protected Plot adapt(Plot p) {
        if (p instanceof SinglePlot) {
            return p;
        }
        PlotSettings s = p.getSettings();

        final FlagContainer oldContainer = p.getFlagContainer();
        p = new SinglePlot(p.getId(), p.getOwnerAbs(), p.getTrusted(), p.getMembers(),
            p.getDenied(), s.getAlias(), s.getPosition(), null, this, s.getMerged(),
            p.getTimestamp(), p.temp);
        p.getFlagContainer().addAll(oldContainer);

        return p;
    }

    @Nullable public Plot getPlotAbs(@Nonnull final PlotId id) {
        Plot plot = getOwnedPlotAbs(id);
        if (plot == null) {
            return new SinglePlot(this, id);
        }
        return plot;
    }

    @Nullable public Plot getPlot(@Nonnull PlotId id) {
        // TODO
        Plot plot = getOwnedPlotAbs(id);
        if (plot == null) {
            return new SinglePlot(this, id);
        }
        return plot.getBasePlot(false);
    }
}
