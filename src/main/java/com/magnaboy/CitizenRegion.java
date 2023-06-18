package com.magnaboy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.runelite.api.coords.WorldPoint;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

///The main list of all the citizens
public class CitizenRegion {

    public float version;
    public int regionId;
    public List<CitizenInfo> citizenRoster = new ArrayList<>();
    public List<SceneryInfo> sceneryRoster = new ArrayList<>();


    //TODO Make hashmaps private and make getters/setters
    public transient HashMap<UUID, Scenery> scenery = new HashMap<>();
    public transient HashMap<UUID, Citizen> citizens = new HashMap<>();
    private static CitizensPlugin plugin;
    private static final float VALID_REGION_VERSION = 0.8f;        //This is just in case we want to make any major changes to the files
    private static final HashMap<Integer, CitizenRegion> dirtyRegions = new HashMap<>();
    private static final HashMap<Integer, CitizenRegion> regionCache = new HashMap<>();
    private static final String REGIONDATA_DIRECTORY = new File("src/main/resources/RegionData/").getAbsolutePath();

    public static void init(CitizensPlugin p)
    {
        plugin = p;
    }

    public void saveRegion() throws IOException {
        saveRegion(false);
    }

    public void saveRegion(boolean prettyPrint) throws IOException {
        try {
            Path path = Paths.get(REGIONDATA_DIRECTORY, regionId + ".json");
            Writer wr = new BufferedWriter(new FileWriter(path.toString()));
            GsonBuilder gb = new GsonBuilder();
            if (prettyPrint)
                gb.setPrettyPrinting();
            Gson gson = gb.create();
            gson.toJson(this, wr);
            wr.flush();
            wr.close();
        } catch (IOException e) {
            throw new IOException(e);
        }
    }

    public static CitizenRegion loadRegion(int regionId, CitizensPlugin plugin) {

        if(regionCache.containsKey(regionId)) {
            Util.log("Loaded Region: " + regionId + " from cache");
            return regionCache.get(regionId);
        }

        InputStream inputStream;
        try {
            inputStream = new FileInputStream(REGIONDATA_DIRECTORY + File.separator + regionId + ".json");
        } catch (FileNotFoundException e) {
            //No need to do anything special here. Probably just a region without citizens
            return null;
        }
        try (Reader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            Gson gson = new Gson();
            CitizenRegion region = gson.fromJson(reader, CitizenRegion.class);
            if(region == null) {
                Util.log(String.format("Region file found but didn't deserialize"));
                return null;
            }
            if(region.version != VALID_REGION_VERSION) {
                Util.log(String.format("Incompatible region version. Expected: %f Received: %f", VALID_REGION_VERSION, region.version));
                return null;
            }
            for (CitizenInfo cInfo : region.citizenRoster) {
                Citizen citizen = loadCitizen(plugin, cInfo);
               region.citizens.put(citizen.uuid, citizen);
            }
            for (SceneryInfo sInfo : region.sceneryRoster) {
                Scenery scenery = loadScenery(plugin, sInfo);
                region.scenery.put(scenery.uuid, scenery);
            }
            regionCache.put(regionId, region);
            Util.log("Loaded Region: " + regionId + " from file");
            return region;
        } catch (IOException e) {
            return null;
        }
    }

    public static Citizen loadCitizen(CitizensPlugin plugin, CitizenInfo info)
    {
        Citizen citizen;
        if (info.entityType == null) {
            citizen = loadStationaryCitizen(plugin, info);
        }
        else {                //Specific
            switch (info.entityType) {
                case WanderingCitizen:
                    citizen = loadWanderingCitizen(plugin, info);
                    break;
                case ScriptedCitizen:
                    citizen = loadScriptedCitizen(plugin, info);
                    break;
                default:
                    citizen = loadStationaryCitizen(plugin, info);
                    break;
            }
        }
        //Citizen
        citizen.setName(info.name)
                .setExamine(info.examineText)
                .setRemarks(info.remarks);

        //Entity
        citizen.setModelIDs(info.modelIds)
                .setModelRecolors(info.modelRecolorFind, info.modelRecolorReplace)
                .setIdleAnimation(info.idleAnimation)
                .setScale(info.scale)
                .setTranslate(info.translate)
                .setBaseOrientation(info.baseOrientation)
                .setUUID(info.uuid)
                .setWorldLocation(info.worldLocation)
                .setRegion(info.regionId);

        return citizen;
    }

    private static StationaryCitizen loadStationaryCitizen(CitizensPlugin plugin, CitizenInfo info) {
        info.entityType = EntityType.StationaryCitizen;
        return new StationaryCitizen(plugin);
    }

    private static WanderingCitizen loadWanderingCitizen(CitizensPlugin plugin, CitizenInfo info) {
        info.entityType = EntityType.WanderingCitizen;
        return new WanderingCitizen(plugin)
                .setBoundingBox(info.wanderBoxBL, info.wanderBoxTR);
    }

    private static ScriptedCitizen loadScriptedCitizen(CitizensPlugin plugin, CitizenInfo info) {
        info.entityType = EntityType.StationaryCitizen;
        return new ScriptedCitizen(plugin);
    }

    public static Scenery loadScenery(CitizensPlugin plugin, SceneryInfo info)
    {
        return new Scenery(plugin).setModelIDs(info.modelIds)
                   .setModelRecolors(info.modelRecolorFind, info.modelRecolorReplace)
                   //.setIdleAnimation(info.idleAnimation)     //Will Scenery objects have this?
                   .setScale(info.scale)
                   .setTranslate(info.translate)
                   .setBaseOrientation(info.baseOrientation)
                   .setUUID(info.uuid)
                   .setWorldLocation(info.worldLocation)
                   .setRegion(info.regionId);
    }

    public static void cleanUp()
    {
        regionCache.clear();
        dirtyRegions.clear();
    }

    //DEV ONLY
    //Spawns citizens from the editor panel
    public static void spawnCitizen(CitizenInfo info)
    {
        Citizen citizen = loadCitizen(plugin, info);
        CitizenRegion region = regionCache.get(info.regionId);
        region.citizens.put(info.uuid, citizen);
        region.citizenRoster.add(info);
        plugin.citizens.add(citizen);
        dirtyRegion(region);
        plugin.refreshEntityCollection();
        plugin.updateAll();
    }

    public static void spawnScenery(SceneryInfo info)
    {
        Scenery scenery = loadScenery(plugin, info);
        CitizenRegion region = regionCache.get(info.regionId);
        region.scenery.put(info.uuid, scenery);
        region.sceneryRoster.add(info);
        plugin.scenery.add(scenery);
        dirtyRegion(region);
        plugin.refreshEntityCollection();
        plugin.updateAll();
    }

    //DEV ONLY
    public static void dirtyRegion(CitizenRegion region)
    {
        dirtyRegions.put(region.regionId, region);
    }

    public static void clearDirtyRegions()
    {
        dirtyRegions.clear();
    }

    public static int dirtyRegionCount()
    {
        return dirtyRegions.size();
    }

    public static void saveDirtyRegions()
    {
        for(Map.Entry<Integer, CitizenRegion> region : dirtyRegions.entrySet())
        {
            try {
                region.getValue().saveRegion();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        Util.log("Saved " + dirtyRegions.size() + " dirty regions");
        clearDirtyRegions();
    }
}
