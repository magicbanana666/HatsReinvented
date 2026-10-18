package com.magicbanana666.hats.common.hat;

import com.magicbanana666.bananautil.tabula.TabulaFile;
import com.magicbanana666.bananautil.tabula.TabulaProject;
import com.magicbanana666.hats.HatsReinvented;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Loads the hats: .tbl (Tabula) files in the "hats" folder of the game directory.
 * The hats that come with the mod are extracted there on first start, so you can add / edit your own.
 */
public final class HatResources
{
    /** Read by the render / server threads while a reload can be running: a reload builds a new map and swaps it in. */
    public static volatile Map<String, HatInfo> HATS = Map.of();
    private static Path hatsDir;

    private HatResources() {}

    public static Path getHatsDir()
    {
        if(hatsDir == null)
        {
            hatsDir = FabricLoader.getInstance().getGameDir().resolve("hats");
        }
        return hatsDir;
    }

    /** Extracts the bundled hats once (a marker file stops it from extracting again, so deleted hats stay deleted). */
    public static void extractDefaults(boolean force)
    {
        Path dir = getHatsDir();
        try
        {
            Files.createDirectories(dir);
            Path marker = dir.resolve("files.extracted");
            if(Files.exists(marker) && !force)
            {
                return;
            }
            int count = 0;
            try(InputStream in = HatsReinvented.class.getResourceAsStream("/hats.zip"))
            {
                if(in == null)
                {
                    HatsReinvented.LOGGER.error("hats.zip is missing from the mod jar!");
                    return;
                }
                try(ZipInputStream zip = new ZipInputStream(in))
                {
                    ZipEntry entry;
                    while((entry = zip.getNextEntry()) != null)
                    {
                        Path target = dir.resolve(entry.getName()).normalize();
                        if(!target.startsWith(dir))
                        {
                            continue;
                        }
                        if(entry.isDirectory())
                        {
                            Files.createDirectories(target);
                        }
                        else
                        {
                            Files.createDirectories(target.getParent());
                            Files.write(target, zip.readAllBytes());
                            count++;
                        }
                    }
                }
            }
            Files.writeString(marker, "");
            HatsReinvented.LOGGER.info("Extracted {} hat files to {}", count, dir);
        }
        catch(IOException e)
        {
            HatsReinvented.LOGGER.error("Could not extract the hats", e);
        }
    }

    public static synchronized void loadAllHats()
    {
        Map<String, HatInfo> hats = new HashMap<>();
        List<HatInfo> accessories = new ArrayList<>();
        int files = 0;
        try(Stream<Path> stream = Files.walk(getHatsDir()))
        {
            for(Path path : (Iterable<Path>)stream::iterator)
            {
                if(!Files.isRegularFile(path) || !path.getFileName().toString().endsWith(".tbl"))
                {
                    continue;
                }
                HatInfo info = readHat(path);
                if(info != null)
                {
                    files++;
                    if(info.accessoryFor == null)
                    {
                        hats.put(info.name, info);
                    }
                    else
                    {
                        accessories.add(info);
                    }
                }
            }
        }
        catch(IOException e)
        {
            HatsReinvented.LOGGER.error("Could not load the hats", e);
        }

        int accessoryCount = accessories.size();
        Map<String, List<HatInfo>> byHat = new HashMap<>();
        for(HatInfo accessory : accessories)
        {
            byHat.computeIfAbsent(accessory.accessoryFor, k -> new ArrayList<>()).add(accessory);
        }
        byHat.forEach((hatName, infos) -> {
            HatInfo base = hats.get(hatName);
            if(base != null)
            {
                base.accessorise(infos);
            }
            for(HatInfo orphan : infos)
            {
                HatsReinvented.LOGGER.warn("Could not find the hat {} for the accessory {}", hatName, orphan.name);
            }
        });
        HatsReinvented.LOGGER.info("Loaded {} hat files: {} hats, {} accessories", files, hats.size(), accessoryCount);
        Map<String, HatInfo> loaded = Collections.unmodifiableMap(hats);
        //rarities and pools are set up before the new hats become visible
        HatHandler.allocateHatPools(loaded);
        HATS = loaded;
    }

    @Nullable
    private static HatInfo readHat(Path file)
    {
        TabulaProject project = TabulaFile.read(file);
        if(project == null)
        {
            HatsReinvented.LOGGER.warn("Could not read hat file {}", file);
            return null;
        }
        if(project.textureBytes == null)
        {
            HatsReinvented.LOGGER.warn("Hat file has no texture, skipping: {}", file);
            return null;
        }
        String fileName = file.getFileName().toString();
        return new HatInfo(fileName.substring(0, fileName.length() - 4), project);
    }

    @Nullable
    public static HatInfo get(String name)
    {
        return HATS.get(name);
    }

    @Nullable
    public static HatInfo get(HatPart part)
    {
        return HATS.get(part.name);
    }

    public static List<HatPart> getAllHatsAsParts(int count)
    {
        List<HatPart> parts = new ArrayList<>();
        HATS.values().stream().sorted().forEach(info -> parts.add(info.getAsHatPart(count)));
        return parts;
    }
}
