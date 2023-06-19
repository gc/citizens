package com.magnaboy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

	public static void init(CitizensPlugin p) {
		plugin = p;
	}

	public void saveRegion() throws IOException {
		try {
			Path path = Paths.get(REGIONDATA_DIRECTORY, regionId + ".json");
			Writer wr = new BufferedWriter(new FileWriter(path.toString()));
			GsonBuilder gb = new GsonBuilder();
			gb.setPrettyPrinting();
			Gson gson = gb.create();
			gson.toJson(this, wr);
			wr.flush();
			wr.close();
		} catch (IOException e) {
			throw new IOException(e);
		}
	}

	public static CitizenRegion loadRegion(int regionId) {
		return loadRegion(regionId, false);
	}

	public static CitizenRegion loadRegion(int regionId, Boolean createIfNotExists) {
		if (regionCache.containsKey(regionId)) {
			Util.log("Loaded Region: " + regionId + " from cache");
			return regionCache.get(regionId);
		}

		InputStream inputStream;
		try {
			inputStream = new FileInputStream(REGIONDATA_DIRECTORY + File.separator + regionId + ".json");
		} catch (FileNotFoundException e) {
			// No region file was found.
			// If in development, create one, save it and then try to load it.
			if (plugin.IS_DEVELOPMENT && createIfNotExists) {
				CitizenRegion region = new CitizenRegion();
				region.regionId = regionId;
				region.version = VALID_REGION_VERSION;
				try {
					region.saveRegion();
				} catch (IOException ex) {
					throw new RuntimeException(ex);
				}
				return loadRegion(regionId, false);
			}
			return null;
		}
		try (Reader reader = new BufferedReader(new InputStreamReader(inputStream))) {
			Gson gson = new Gson();
			CitizenRegion region = gson.fromJson(reader, CitizenRegion.class);
			if (region == null) {
				Util.log("Region file found but didn't deserialize");
				return null;
			}
			if (region.version != VALID_REGION_VERSION) {
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

	public static Citizen loadCitizen(CitizensPlugin plugin, CitizenInfo info) {
		Citizen citizen;
		if (info.entityType == null) {
			citizen = loadStationaryCitizen(plugin, info);
		} else {
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
		// Citizen
		citizen.setName(info.name)
			.setExamine(info.examineText)
			.setRemarks(info.remarks);

		// Entity
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
		return new StationaryCitizen(plugin)
			.setWorldLocation(info.worldLocation);
	}

	private static WanderingCitizen loadWanderingCitizen(CitizensPlugin plugin, CitizenInfo info) {
		info.entityType = EntityType.WanderingCitizen;
		return new WanderingCitizen(plugin)
			.setWanderRegionBL(info.wanderBoxBL)
			.setWanderRegionTR(info.wanderBoxTR)
			.setWorldLocation(info.worldLocation)
			.setBoundingBox(info.wanderBoxBL, info.wanderBoxTR);
	}

	private static ScriptedCitizen loadScriptedCitizen(CitizensPlugin plugin, CitizenInfo info) {
		info.entityType = EntityType.StationaryCitizen;
		return new ScriptedCitizen(plugin).setWorldLocation(info.worldLocation);
	}

	public static Scenery loadScenery(CitizensPlugin plugin, SceneryInfo info) {
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

	public static void cleanUp() {
		for (CitizenRegion r : regionCache.values()) {
			r.citizenRoster.clear();
			r.sceneryRoster.clear();

			r.citizens.clear();
			r.scenery.clear();
		}
		regionCache.clear();
		dirtyRegions.clear();
	}

	public static Citizen spawnCitizenFromPanel(CitizenInfo info) {
		Citizen citizen = loadCitizen(plugin, info);
		CitizenRegion region = loadRegion(info.regionId, true);
		region.citizens.put(info.uuid, citizen);
		region.citizenRoster.add(info);
		plugin.citizens.add(citizen);
		dirtyRegion(region);
		plugin.refreshEntityCollection();
		plugin.updateAll();
		return citizen;
	}

	public static Scenery spawnSceneryFromPanel(SceneryInfo info) {
		Scenery scenery = loadScenery(plugin, info);
		CitizenRegion region = regionCache.get(info.regionId);
		region.scenery.put(info.uuid, scenery);
		region.sceneryRoster.add(info);
		plugin.scenery.add(scenery);
		dirtyRegion(region);
		plugin.refreshEntityCollection();
		plugin.updateAll();
		return scenery;
	}

	public static void saveEntity(EntityInfo info) {
		if (info.entityType == EntityType.Scenery) {
			// TODO
		} else {
			Citizen citizen = loadCitizen(plugin, (CitizenInfo) info);
			CitizenRegion region = loadRegion(info.regionId);
			region.citizens.get(info.uuid).despawn();
			region.citizens.put(info.uuid, citizen);
			CitizenInfo oldInfo = region.citizenRoster.stream().filter(i -> i.uuid == info.uuid).findFirst().orElse(null);
			if (oldInfo != null) {
				region.citizenRoster.remove(oldInfo);
				region.citizenRoster.add((CitizenInfo) info);
			}
			CitizensPlugin.reloadCitizens(plugin);
			dirtyRegion(region);
		}
	}

	// DEV ONLY
	public static void dirtyRegion(CitizenRegion region) {
		dirtyRegions.put(region.regionId, region);
	}

	public static void clearDirtyRegions() {
		dirtyRegions.clear();
	}

	public static void clearCache() {
		regionCache.clear();
	}

	public static void deleteEntity(Citizen citizen) {
		CitizenRegion region = loadRegion(citizen.regionId);
		CitizenInfo info = region.citizenRoster.stream()
			.filter(c -> c.uuid == citizen.uuid)
			.findFirst()
			.orElse(null);
		region.citizenRoster.remove(info);
		dirtyRegion(region);
	}

	public static void deleteEntity(Scenery scenery) {
		CitizenRegion region = loadRegion(scenery.regionId);
		SceneryInfo info = region.sceneryRoster.stream()
			.filter(c -> c.uuid == scenery.uuid)
			.findFirst()
			.orElse(null);
		region.sceneryRoster.remove(info);
		dirtyRegion(region);
	}

	public static int dirtyRegionCount() {
		return dirtyRegions.size();
	}

	public static void saveDirtyRegions() {
		for (Map.Entry<Integer, CitizenRegion> region : dirtyRegions.entrySet()) {
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
