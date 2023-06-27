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
import java.util.function.Consumer;

public class CitizenRegion {

	private static final float VALID_REGION_VERSION = 0.8f;
	private static final HashMap<Integer, CitizenRegion> dirtyRegions = new HashMap<>();
	public static final HashMap<Integer, CitizenRegion> regionCache = new HashMap<>();
	private static final String REGIONDATA_DIRECTORY = new File("src/main/resources/RegionData/").getAbsolutePath();
	private static CitizensPlugin plugin;
	private final transient HashMap<UUID, Entity> entities = new HashMap<>();

	public float version;
	public int regionId;
	public List<CitizenInfo> citizenRoster = new ArrayList<>();
	public List<SceneryInfo> sceneryRoster = new ArrayList<>();

	public static void init(CitizensPlugin p) {
		plugin = p;
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
				region.entities.put(citizen.uuid, citizen);
			}
			for (SceneryInfo sInfo : region.sceneryRoster) {
				Scenery scenery = loadScenery(plugin, sInfo);
				region.entities.put(scenery.uuid, scenery);
			}
			if (plugin.IS_DEVELOPMENT) {
				region.entities.values().forEach(Entity::validate);
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
			.setObjectToRemove(info.removedObject)
			.setModelRecolors(info.modelRecolorFind, info.modelRecolorReplace)
			.setIdleAnimation(info.idleAnimation)
			.setScale(info.scale)
			.setTranslate(info.translate)
			.setBaseOrientation(info.baseOrientation)
			.setUUID(info.uuid)
			.setWorldLocation(info.worldLocation)
			.setRegion(info.regionId);

		if (info.mergedObjects != null) {
			info.mergedObjects.forEach(citizen::addMergedObject);
		}

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
			.setIdleAnimation(info.idleAnimation)
			.setScale(info.scale)
			.setTranslate(info.translate)
			.setBaseOrientation(info.baseOrientation)
			.setUUID(info.uuid)
			.setWorldLocation(info.worldLocation)
			.setRegion(info.regionId);
	}

	public static void forEachEntity(Consumer<Entity> function) {
		regionCache.forEach((regionId, r) -> {
			r.entities.forEach((entityId, e) -> {
				function.accept(e);
			});
		});
	}

	public static void cleanUp() {
		forEachEntity(Entity::despawn);

		for (CitizenRegion r : regionCache.values()) {
			r.citizenRoster.clear();
			r.sceneryRoster.clear();
			r.entities.clear();
		}
		regionCache.clear();
		dirtyRegions.clear();
	}

	// DEVELOPMENT SECTION
	public static Citizen spawnCitizenFromPanel(CitizenInfo info) {
		Citizen citizen = loadCitizen(plugin, info);
		CitizenRegion region = loadRegion(info.regionId, true);
		region.entities.put(info.uuid, citizen);
		region.citizenRoster.add(info);
		dirtyRegion(region);
		plugin.updateAll();
		return citizen;
	}

	public static Scenery spawnSceneryFromPanel(SceneryInfo info) {
		Scenery scenery = loadScenery(plugin, info);
		CitizenRegion region = regionCache.get(info.regionId);
		region.entities.put(info.uuid, scenery);
		region.sceneryRoster.add(info);
		dirtyRegion(region);
		plugin.updateAll();
		return scenery;
	}

	public static void updateEntity(EntityInfo info) {
		if (info.entityType == EntityType.Scenery) {
			// TODO
		} else {
			CitizenRegion region = regionCache.get(info.regionId);
			Entity e = region.entities.get(info.uuid);
			Citizen updated = loadCitizen(plugin, (CitizenInfo) info);

			addEntityToRegion(updated, info);
			removeEntityFromRegion(e);
		}
	}

	public static void dirtyRegion(CitizenRegion region) {
		dirtyRegions.put(region.regionId, region);
	}

	public static void clearDirtyRegions() {
		dirtyRegions.clear();
	}

	public static void addEntityToRegion(Entity e, EntityInfo info) {
		CitizenRegion region = regionCache.get(e.regionId);
		region.entities.put(e.uuid, e);
		if (info instanceof CitizenInfo) {
			region.citizenRoster.add((CitizenInfo) info);
		}
		if (info instanceof SceneryInfo) {
			region.sceneryRoster.add((SceneryInfo) info);
		}
	}

	private static void removeEntityFromRegion(Citizen citizen, CitizenRegion region) {
		CitizenInfo info = region.citizenRoster.stream()
			.filter(c -> c.uuid == citizen.uuid)
			.findFirst()
			.orElse(null);
		region.citizenRoster.remove(info);
	}

	private static void removeEntityFromRegion(Scenery scenery, CitizenRegion region) {
		SceneryInfo info = region.sceneryRoster.stream()
			.filter(c -> c.uuid == scenery.uuid)
			.findFirst()
			.orElse(null);
		region.sceneryRoster.remove(info);
	}

	public static void removeEntityFromRegion(Entity e) {
		CitizenRegion region = regionCache.get(e.regionId);
		if (e instanceof Citizen) {
			removeEntityFromRegion((Citizen) e, region);
		}
		if (e instanceof Scenery) {
			removeEntityFromRegion((Scenery) e, region);
		}

		region.entities.remove(e.uuid);
		e.despawn();
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

	public void despawnRegion() {
		entities.values().forEach(Entity::despawn);
	}

	public void updateEntities() {
		entities.values().forEach(Entity::update);
	}
}
