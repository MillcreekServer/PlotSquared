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
package com.plotsquared.core.util;

import com.google.inject.Inject;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParseException;
import com.plotsquared.core.PlotSquared;
import com.plotsquared.core.configuration.Settings;
import com.plotsquared.core.generator.ClassicPlotWorld;
import com.plotsquared.core.inject.factory.ProgressSubscriberFactory;
import com.plotsquared.core.location.Location;
import com.plotsquared.core.player.PlotPlayer;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.PlotArea;
import com.plotsquared.core.plot.schematic.Schematic;
import com.plotsquared.core.queue.QueueCoordinator;
import com.plotsquared.core.util.net.AbstractDelegateOutputStream;
import com.plotsquared.core.util.task.RunnableVal;
import com.plotsquared.core.util.task.TaskManager;
import com.plotsquared.core.util.task.YieldRunnable;
import com.sk89q.jnbt.ByteArrayTag;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.IntArrayTag;
import com.sk89q.jnbt.IntTag;
import com.sk89q.jnbt.ListTag;
import com.sk89q.jnbt.NBTInputStream;
import com.sk89q.jnbt.NBTOutputStream;
import com.sk89q.jnbt.ShortTag;
import com.sk89q.jnbt.StringTag;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.MCEditSchematicReader;
import com.sk89q.worldedit.extent.clipboard.io.SpongeSchematicReader;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BaseBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public abstract class SchematicHandler {

    private static final Logger logger = LoggerFactory.getLogger("P2/" + SchematicHandler.class.getSimpleName());
    private static final Gson GSON = new Gson();
    private static final Path TEMP_DIR = Paths.get("TODO-PATH");
    public static SchematicHandler manager;
    private final WorldUtil worldUtil;
    private boolean exportAll = false;
    private final ProgressSubscriberFactory subscriberFactory;

    @Inject public SchematicHandler(@Nonnull final WorldUtil worldUtil, @Nonnull ProgressSubscriberFactory subscriberFactory) {
        this.worldUtil = worldUtil;
        this.subscriberFactory = subscriberFactory;
    }

    @Deprecated
    public static void upload(@Nullable UUID uuid,
                              @Nullable final String file,
                              @Nonnull final String extension,
                              @Nullable final RunnableVal<OutputStream> writeTask,
                              @Nonnull final RunnableVal<URL> whenDone) {
        if (writeTask == null) {
            TaskManager.runTask(whenDone);
            return;
        }
        final String filename;
        final String website;
        if (uuid == null) {
            uuid = UUID.randomUUID();
            website = Settings.Web.URL + "upload.php?" + uuid;
            filename = "plot." + extension;
        } else {
            website = Settings.Web.URL + "save.php?" + uuid;
            filename = file + '.' + extension;
        }
        final URL url;
        try {
            url = new URL(Settings.Web.URL + "?key=" + uuid + "&type=" + extension);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            whenDone.run();
            return;
        }
        TaskManager.runTaskAsync(() -> {
            try {
                String boundary = Long.toHexString(System.currentTimeMillis());
                URLConnection con = new URL(website).openConnection();
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                try (OutputStream output = con.getOutputStream();
                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true)) {
                    String CRLF = "\r\n";
                    writer.append("--").append(boundary).append(CRLF);
                    writer.append("Content-Disposition: form-data; name=\"param\"").append(CRLF);
                    writer.append("Content-Type: text/plain; charset=").append(StandardCharsets.UTF_8.displayName()).append(CRLF);
                    String param = "value";
                    writer.append(CRLF).append(param).append(CRLF).flush();
                    writer.append("--").append(boundary).append(CRLF);
                    writer.append("Content-Disposition: form-data; name=\"schematicFile\"; filename=\"").append(filename)
                        .append(String.valueOf('"')).append(CRLF);
                    writer.append("Content-Type: ").append(URLConnection.guessContentTypeFromName(filename)).append(CRLF);
                    writer.append("Content-Transfer-Encoding: binary").append(CRLF);
                    writer.append(CRLF).flush();
                    writeTask.value = new AbstractDelegateOutputStream(output) {
                        @Override public void close() {
                        } // Don't close
                    };
                    writeTask.run();
                    output.flush();
                    writer.append(CRLF).flush();
                    writer.append("--").append(boundary).append("--").append(CRLF).flush();
                }
                String content;
                try (Scanner scanner = new Scanner(con.getInputStream()).useDelimiter("\\A")) {
                    content = scanner.next().trim();
                }
                if (!content.startsWith("<")) {
                }
                int responseCode = ((HttpURLConnection) con).getResponseCode();
                if (responseCode == 200) {
                    whenDone.value = url;
                }
                TaskManager.runTask(whenDone);
            } catch (IOException e) {
                e.printStackTrace();
                TaskManager.runTask(whenDone);
            }
        });
    }

    public boolean exportAll(Collection<Plot> collection, final File outputDir, final String namingScheme, final Runnable ifSuccess) {
        if (this.exportAll) {
            return false;
        }
        if (collection.isEmpty()) {
            return false;
        }
        this.exportAll = true;
        final ArrayList<Plot> plots = new ArrayList<>(collection);
        TaskManager.runTask(new Runnable() {
            @Override public void run() {
                if (plots.isEmpty()) {
                    SchematicHandler.this.exportAll = false;
                    TaskManager.runTask(ifSuccess);
                    return;
                }
                Iterator<Plot> i = plots.iterator();
                final Plot plot = i.next();
                i.remove();

                final String owner;
                if (plot.hasOwner()) {
                    owner = plot.getOwnerAbs().toString();
                } else {
                    owner = "unknown";
                }

                final String name;
                if (namingScheme == null) {
                    name = plot.getId().getX() + ";" + plot.getId().getY() + ',' + plot.getArea() + ',' + owner;
                } else {
                    name = namingScheme.replaceAll("%id%", plot.getId().toString()).replaceAll("%idx%", plot.getId().getX() + "")
                        .replaceAll("%idy%", plot.getId().getY() + "").replaceAll("%world%", plot.getArea().toString());
                }

                final String directory;
                if (outputDir == null) {
                    directory = Settings.Paths.SCHEMATICS;
                } else {
                    directory = outputDir.getAbsolutePath();
                }

                final Runnable THIS = this;
                getCompoundTag(plot)
                        .whenComplete((compoundTag, throwable) -> {
                            if (compoundTag != null) {
                                TaskManager.runTaskAsync(() -> {
                                    boolean result = save(compoundTag, directory + File.separator + name + ".schem");
                                    if (!result) {
                                        logger.error("Failed to save {}", plot.getId());
                                    }
                                    TaskManager.runTask(THIS);
                                });
                            }
                        });
            }
        });
        return true;
    }

    /**
     * Paste a schematic.
     *
     * @param schematic  the schematic object to paste
     * @param plot       plot to paste in
     * @param xOffset    offset x to paste it from plot origin
     * @param yOffset    offset y to paste it from plot origin
     * @param zOffset    offset z to paste it from plot origin
     * @param autoHeight if to automatically choose height to paste from
     * @param actor      the actor pasting the schematic
     * @param whenDone   task to run when schematic is pasted
     */
    public void paste(final Schematic schematic,
                      final Plot plot,
                      final int xOffset,
                      final int yOffset,
                      final int zOffset,
                      final boolean autoHeight,
                      final PlotPlayer<?> actor,
                      final RunnableVal<Boolean> whenDone) {

        TaskManager.runTask(() -> {
            if (whenDone != null) {
                whenDone.value = false;
            }
            if (schematic == null) {
                TaskManager.runTask(whenDone);
                return;
            }
            try {
                BlockVector3 dimension = schematic.getClipboard().getDimensions();
                final int WIDTH = dimension.getX();
                final int LENGTH = dimension.getZ();
                final int HEIGHT = dimension.getY();
                // Validate dimensions
                CuboidRegion region = plot.getLargestRegion();
                if (((region.getMaximumPoint().getX() - region.getMinimumPoint().getX() + xOffset + 1) < WIDTH) || (
                    (region.getMaximumPoint().getZ() - region.getMinimumPoint().getZ() + zOffset + 1) < LENGTH) || (HEIGHT > 256)) {
                    TaskManager.runTask(whenDone);
                    return;
                }
                // block type and data arrays
                final Clipboard blockArrayClipboard = schematic.getClipboard();
                // Calculate the optimal height to paste the schematic at
                final int y_offset_actual;
                if (autoHeight) {
                    if (HEIGHT >= 256) {
                        y_offset_actual = yOffset;
                    } else {
                        PlotArea pw = plot.getArea();
                        if (pw instanceof ClassicPlotWorld) {
                            y_offset_actual = yOffset + ((ClassicPlotWorld) pw).PLOT_HEIGHT;
                        } else {
                            y_offset_actual = yOffset + 1 + this.worldUtil
                                .getHighestBlockSynchronous(plot.getWorldName(), region.getMinimumPoint().getX() + 1,
                                    region.getMinimumPoint().getZ() + 1);
                        }
                    }
                } else {
                    y_offset_actual = yOffset;
                }

                final Location pos1 = Location
                    .at(plot.getWorldName(), region.getMinimumPoint().getX() + xOffset, y_offset_actual, region.getMinimumPoint().getZ() + zOffset);

                final int p1x = pos1.getX();
                final int p1z = pos1.getZ();
                // Paste schematic here
                final QueueCoordinator queue = plot.getArea().getQueue();

                for (int ry = 0; ry < Math.min(256, HEIGHT); ry++) {
                    int yy = y_offset_actual + ry;
                    if (yy > 255) {
                        continue;
                    }
                    for (int rz = 0; rz <= blockArrayClipboard.getDimensions().getZ(); rz++) {
                        for (int rx = 0; rx < blockArrayClipboard.getDimensions().getX(); rx++) {
                            int xx = p1x + rx;
                            int zz = p1z + rz;
                            BaseBlock id = blockArrayClipboard.getFullBlock(BlockVector3.at(rx, ry, rz));
                            queue.setBlock(xx, yy, zz, id);
                            if (ry == 0) {
                                BiomeType biome = blockArrayClipboard.getBiome(BlockVector3.at(rx, ry, rz));
                                queue.setBiome(xx, yy, zz, biome);
                            }
                        }
                    }
                }
                if (actor != null && Settings.QUEUE.NOTIFY_PROGRESS) {
                    queue.addProgressSubscriber(subscriberFactory.createWithActor(actor));
                }
                queue.setCompleteTask(whenDone);
                queue.enqueue();
            } catch (Exception e) {
                e.printStackTrace();
                TaskManager.runTask(whenDone);
            }
        });
    }

    public abstract boolean restoreTile(QueueCoordinator queue, CompoundTag tag, int x, int y, int z);

    /**
     * Get a schematic
     *
     * @param name to check
     * @return schematic if found, else null
     * @throws UnsupportedFormatException thrown if schematic format is unsupported
     */
    public Schematic getSchematic(String name) throws UnsupportedFormatException {
        File parent = FileUtils.getFile(PlotSquared.platform().getDirectory(), Settings.Paths.SCHEMATICS);
        if (!parent.exists()) {
            if (!parent.mkdir()) {
                throw new RuntimeException("Could not create schematic parent directory");
            }
        }
        if (!name.endsWith(".schem") && !name.endsWith(".schematic")) {
            name = name + ".schem";
        }
        File file = FileUtils.getFile(PlotSquared.platform().getDirectory(), Settings.Paths.SCHEMATICS + File.separator + name);
        if (!file.exists()) {
            file = FileUtils.getFile(PlotSquared.platform().getDirectory(), Settings.Paths.SCHEMATICS + File.separator + name);
        }
        return getSchematic(file);
    }

    /**
     * Get an immutable collection containing all schematic names
     *
     * @return Immutable collection with schematic names
     */
    public Collection<String> getSchematicNames() {
        final File parent = FileUtils.getFile(PlotSquared.platform().getDirectory(), Settings.Paths.SCHEMATICS);
        final List<String> names = new ArrayList<>();
        if (parent.exists()) {
            final String[] rawNames = parent.list((dir, name) -> name.endsWith(".schematic") || name.endsWith(".schem"));
            if (rawNames != null) {
                final List<String> transformed = Arrays.stream(rawNames)
                    //.map(rawName -> rawName.substring(0, rawName.length() - 10))
                    .collect(Collectors.toList());
                names.addAll(transformed);
            }
        }
        return Collections.unmodifiableList(names);
    }

    /**
     * Get a schematic
     *
     * @param file to check
     * @return schematic if found, else null
     * @throws UnsupportedFormatException thrown if schematic format is unsupported
     */
    public Schematic getSchematic(File file) throws UnsupportedFormatException {
        if (!file.exists()) {
            return null;
        }
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format != null) {
            try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
                Clipboard clip = reader.read();
                return new Schematic(clip);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            throw new UnsupportedFormatException("This schematic format is not recognised or supported.");
        }
        return null;
    }

    public Schematic getSchematic(@Nonnull URL url) {
        try {
            ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
            InputStream inputStream = Channels.newInputStream(readableByteChannel);
            return getSchematic(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Schematic getSchematic(@Nonnull InputStream is) {
        try {
            SpongeSchematicReader schematicReader = new SpongeSchematicReader(new NBTInputStream(new GZIPInputStream(is)));
            Clipboard clip = schematicReader.read();
            return new Schematic(clip);
        } catch (IOException ignored) {
            try {
                MCEditSchematicReader schematicReader = new MCEditSchematicReader(new NBTInputStream(new GZIPInputStream(is)));
                Clipboard clip = schematicReader.read();
                return new Schematic(clip);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public List<String> getSaves(UUID uuid) {
        String rawJSON;
        try {
            String website = Settings.Web.URL + "list.php?" + uuid.toString();
            URL url = new URL(website);
            URLConnection connection = new URL(url.toString()).openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                rawJSON = reader.lines().collect(Collectors.joining());
            }
            JsonArray array = GSON.fromJson(rawJSON, JsonArray.class);
            List<String> schematics = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                String schematic = array.get(i).getAsString();
                schematics.add(schematic);
            }
            return schematics;
        } catch (JsonParseException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Deprecated
    public void upload(final CompoundTag tag, UUID uuid, String file, RunnableVal<URL> whenDone) {
        if (tag == null) {
            TaskManager.runTask(whenDone);
            return;
        }
        upload(uuid, file, "schem", new RunnableVal<OutputStream>() {
            @Override public void run(OutputStream output) {
                try (NBTOutputStream nos = new NBTOutputStream(new GZIPOutputStream(output, true))) {
                    nos.writeNamedTag("Schematic", tag);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }, whenDone);
    }

    /**
     * Saves a schematic to a file path.
     *
     * @param tag  to save
     * @param path to save in
     * @return true if succeeded
     */
    public boolean save(CompoundTag tag, String path) {
        if (tag == null) {
            return false;
        }
        try {
            File tmp = FileUtils.getFile(PlotSquared.platform().getDirectory(), path);
            tmp.getParentFile().mkdirs();
            try (NBTOutputStream nbtStream = new NBTOutputStream(new GZIPOutputStream(new FileOutputStream(tmp)))) {
                nbtStream.writeNamedTag("Schematic", tag);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void writeSchematicData(@Nonnull final Map<String, Tag> schematic,
                                    @Nonnull final Map<String, Integer> palette,
                                    @Nonnull final Map<String, Integer> biomePalette,
                                    @Nonnull final List<CompoundTag> tileEntities,
                                    @Nonnull final ByteArrayOutputStream buffer,
                                    @Nonnull final ByteArrayOutputStream biomeBuffer) {
        schematic.put("PaletteMax", new IntTag(palette.size()));

        Map<String, Tag> paletteTag = new HashMap<>();
        palette.forEach((key, value) -> paletteTag.put(key, new IntTag(value)));

        schematic.put("Palette", new CompoundTag(paletteTag));
        schematic.put("BlockData", new ByteArrayTag(buffer.toByteArray()));
        schematic.put("BlockEntities", new ListTag(CompoundTag.class, tileEntities));

        schematic.put("BiomePaletteMax", new IntTag(biomePalette.size()));

        Map<String, Tag> biomePaletteTag = new HashMap<>();
        biomePalette.forEach((key, value) -> biomePaletteTag.put(key, new IntTag(value)));

        schematic.put("BiomePalette", new CompoundTag(biomePaletteTag));
        schematic.put("BiomeData", new ByteArrayTag(biomeBuffer.toByteArray()));
    }

    @Nonnull
    private Map<String, Tag> initSchematic(short width, short height, short length) {
        Map<String, Tag> schematic = new HashMap<>();
        schematic.put("Version", new IntTag(2));
        schematic.put("DataVersion",
            new IntTag(WorldEdit.getInstance().getPlatformManager().queryCapability(Capability.WORLD_EDITING).getDataVersion()));

        Map<String, Tag> metadata = new HashMap<>();
        metadata.put("WEOffsetX", new IntTag(0));
        metadata.put("WEOffsetY", new IntTag(0));
        metadata.put("WEOffsetZ", new IntTag(0));

        schematic.put("Metadata", new CompoundTag(metadata));

        schematic.put("Width", new ShortTag(width));
        schematic.put("Height", new ShortTag(height));
        schematic.put("Length", new ShortTag(length));

        // The Sponge format Offset refers to the 'min' points location in the world. That's our 'Origin'
        schematic.put("Offset", new IntArrayTag(new int[] {0, 0, 0,}));
        return schematic;
    }

    /**
     * Get the given plot as {@link CompoundTag} matching the Sponge schematic format.
     *
     * @param plot The plot to get the contents from.
     * @return a {@link CompletableFuture} that provides the created {@link CompoundTag}.
     */
    public CompletableFuture<CompoundTag> getCompoundTag(@Nonnull final Plot plot) {
        return getCompoundTag(Objects.requireNonNull(plot.getWorldName()), plot.getRegions());
    }

    /**
     * Get the contents of the given regions in the given world as {@link CompoundTag}
     * matching the Sponge schematic format.
     *
     * @param worldName The world to get the contents from.
     * @param regions The regions to get the contents from.
     * @return a {@link CompletableFuture} that provides the created {@link CompoundTag}.
     */
    @Nonnull
    public CompletableFuture<CompoundTag> getCompoundTag(@Nonnull final String worldName,
                                                         @Nonnull final Set<CuboidRegion> regions) {
        CompletableFuture<CompoundTag> completableFuture = new CompletableFuture<>();
        TaskManager.runTaskAsync(() -> {
            // Main positions
            CuboidRegion aabb = RegionUtil.getAxisAlignedBoundingBox(regions);
            aabb.setWorld(this.worldUtil.getWeWorld(worldName));

            final int width = aabb.getWidth();
            int height = aabb.getHeight();
            final int length = aabb.getLength();

            Map<String, Tag> schematic = initSchematic((short) width, (short) height, (short) length);

            Map<String, Integer> palette = new HashMap<>();
            Map<String, Integer> biomePalette = new HashMap<>();

            List<CompoundTag> tileEntities = new ArrayList<>();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(width * height * length);
            ByteArrayOutputStream biomeBuffer = new ByteArrayOutputStream(width * length);
            // Queue
            final ArrayDeque<CuboidRegion> queue = new ArrayDeque<>(regions);
            TaskManager.runTask(new Runnable() {
                @Override public void run() {
                    if (queue.isEmpty()) {
                        TaskManager.runTaskAsync(() -> {
                            writeSchematicData(schematic, palette, biomePalette, tileEntities, buffer, biomeBuffer);
                            completableFuture.complete(new CompoundTag(schematic));
                        });
                        return;
                    }
                    final Runnable regionTask = this;
                    CuboidRegion region = queue.poll();

                    final BlockVector3 minimum = region.getMinimumPoint();
                    final BlockVector3 maximum = region.getMaximumPoint();

                    final int minX = minimum.getX();
                    final int minZ = minimum.getZ();
                    final int minY = minimum.getY();

                    final int maxX = maximum.getX();
                    final int maxZ = maximum.getZ();
                    final int maxY = maximum.getY();

                    final Runnable yTask = new YieldRunnable() {
                        int currentY = minY;
                        int currentX = minX;
                        int currentZ = minZ;
                        @Override public void run() {
                            long start = System.currentTimeMillis();
                            for (; currentY <= maxY; currentY++) {
                                int relativeY = currentY - minY;
                                for (; currentZ <= maxZ; currentZ++) {
                                    int relativeZ = currentZ - minZ;
                                    for (; currentX <= maxX; currentX++) {
                                        // if too much time was spent here, we yield this task
                                        // note that current(X/Y/Z) aren't incremented, so the same position
                                        // as *right now* will be visited again
                                        if (System.currentTimeMillis() - start > 40) {
                                            this.yield();
                                            return;
                                        }
                                        int relativeX = currentX - minX;
                                        BlockVector3 point = BlockVector3.at(currentX, currentY, currentZ);
                                        BaseBlock block = aabb.getWorld().getFullBlock(point);
                                        if (block.getNbtData() != null) {
                                            Map<String, Tag> values = new HashMap<>();
                                            for (Map.Entry<String, Tag> entry : block.getNbtData().getValue().entrySet()) {
                                                values.put(entry.getKey(), entry.getValue());
                                            }

                                            // Positions are kept in NBT, we don't want that.
                                            values.remove("x");
                                            values.remove("y");
                                            values.remove("z");

                                            values.put("Id", new StringTag(block.getNbtId()));

                                            // Remove 'id' if it exists. We want 'Id'.
                                            // Do this after we get "getNbtId" cos otherwise "getNbtId" doesn't work.
                                            // Dum.
                                            values.remove("id");
                                            values.put("Pos", new IntArrayTag(new int[] {relativeX, relativeY, relativeZ}));

                                            tileEntities.add(new CompoundTag(values));
                                        }
                                        String blockKey = block.toImmutableState().getAsString();
                                        int blockId;
                                        if (palette.containsKey(blockKey)) {
                                            blockId = palette.get(blockKey);
                                        } else {
                                            blockId = palette.size();
                                            palette.put(blockKey, palette.size());
                                        }

                                        while ((blockId & -128) != 0) {
                                            buffer.write(blockId & 127 | 128);
                                            blockId >>>= 7;
                                        }
                                        buffer.write(blockId);

                                        if (relativeY > 0) {
                                            continue;
                                        }
                                        BlockVector2 pt = BlockVector2.at(currentX, currentZ);
                                        BiomeType biome = aabb.getWorld().getBiome(pt);
                                        String biomeStr = biome.getId();
                                        int biomeId;
                                        if (biomePalette.containsKey(biomeStr)) {
                                            biomeId = biomePalette.get(biomeStr);
                                        } else {
                                            biomeId = biomePalette.size();
                                            biomePalette.put(biomeStr, biomeId);
                                        }
                                        while ((biomeId & -128) != 0) {
                                            biomeBuffer.write(biomeId & 127 | 128);
                                            biomeId >>>= 7;
                                        }
                                        biomeBuffer.write(biomeId);
                                    }
                                    currentX = minX; // reset manually as not using local variable
                                }
                                currentZ = minZ; // reset manually as not using local variable
                            }
                            regionTask.run();
                        }
                    };
                    yTask.run();
                }
            });
        });
        return completableFuture;
    }


    public static class UnsupportedFormatException extends Exception {
        /**
         * Throw with a message.
         *
         * @param message the message
         */
        public UnsupportedFormatException(String message) {
            super(message);
        }

        /**
         * Throw with a message and a cause.
         *
         * @param message the message
         * @param cause   the cause
         */
        public UnsupportedFormatException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
