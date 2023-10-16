package com.magnaboy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import static com.magnaboy.Util.getRandomItem;
import com.magnaboy.serialization.CitizenInfo;
import com.magnaboy.serialization.EntityInfo;
import com.magnaboy.serialization.SceneryInfo;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class CitizenRegion {

	public static final HashMap<Integer, CitizenRegion> regionCache = new HashMap<>();
	private static final float VALID_REGION_VERSION = 0.8f;
	private static final HashMap<Integer, CitizenRegion> dirtyRegions = new HashMap<>();
	private static final String REGIONDATA_DIRECTORY = new File("src/main/resources/RegionData/").getAbsolutePath();
	private static CitizensPlugin plugin;
	public transient HashMap<UUID, Entity> entities = new HashMap<>();

	public float version;
	public int regionId;
	public List<CitizenInfo> citizenRoster = new ArrayList<>();
	public List<SceneryInfo> sceneryRoster = new ArrayList<>();
	public transient ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

	public static void init(CitizensPlugin p) {
		plugin = p;
	}

	public static CitizenRegion loadRegion(int regionId) {
		return loadRegion(regionId, false);
	}

	public synchronized static CitizenRegion loadRegion(int regionId, Boolean createIfNotExists) {
		if (regionCache.containsKey(regionId)) {
			return regionCache.get(regionId);
		}

		InputStream inputStream = plugin.getClass().getClassLoader().getResourceAsStream("RegionData/" + regionId + ".json");
		if (inputStream == null) {
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
				CitizenRegion newRegion = loadRegion(regionId, false);
				return newRegion;
			}
		} else {
			try (Reader reader = new BufferedReader(new InputStreamReader(inputStream))) {
				CitizenRegion region = plugin.gson.fromJson(reader, CitizenRegion.class);
				if (region == null) {
					return null;
				}
				if (region.version != VALID_REGION_VERSION) {
					return null;
				}
				for (CitizenInfo cInfo : region.citizenRoster) {
					Citizen citizen = loadCitizen(plugin, cInfo);
					if (citizen != null) {
						region.entities.put(citizen.uuid, citizen);
					}
				}
				for (SceneryInfo sInfo : region.sceneryRoster) {
					Scenery scenery = loadScenery(plugin, sInfo);
					region.entities.put(scenery.uuid, scenery);
				}
				if (plugin.IS_DEVELOPMENT) {
					region.entities.values().forEach(Entity::validate);
				}
				regionCache.put(regionId, region);
				return region;
			} catch (IOException e) {
				return null;
			}
		}
		return null;
	}

	public static void initCitizenInfo(Citizen citizen, CitizenInfo info) {
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
	}

	public static Citizen loadCitizen(CitizensPlugin plugin, CitizenInfo info) {
		Citizen citizen;

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

		if (info.mergedObjects != null) {
			info.mergedObjects.forEach(citizen::addMergedObject);
		}

		if (info.moveAnimation != null) {
			citizen.movingAnimationId = info.moveAnimation;
		}

		return citizen;
	}

	private static StationaryCitizen loadStationaryCitizen(CitizensPlugin plugin, CitizenInfo info) {
		info.entityType = EntityType.StationaryCitizen;
		StationaryCitizen citizen = new StationaryCitizen(plugin);
		initCitizenInfo(citizen, info);
		citizen.setWorldLocation(info.worldLocation);
		return citizen;
	}

	private static WanderingCitizen loadWanderingCitizen(CitizensPlugin plugin, CitizenInfo info) {
		info.entityType = EntityType.WanderingCitizen;
		WanderingCitizen citizen = new WanderingCitizen(plugin);
		initCitizenInfo(citizen, info);
		citizen.setWanderRegionBL(info.wanderBoxBL)
			.setWanderRegionTR(info.wanderBoxTR)
			.setWorldLocation(info.worldLocation)
			.setBoundingBox(info.wanderBoxBL, info.wanderBoxTR)
			.setBaseOrientation(getRandomItem(new CardinalDirection[]{CardinalDirection.North, CardinalDirection.South, CardinalDirection.East, CardinalDirection.West}));
		return citizen;
	}

	private static ScriptedCitizen loadScriptedCitizen(CitizensPlugin plugin, CitizenInfo info) {
		info.entityType = EntityType.ScriptedCitizen;
		ScriptedCitizen citizen = new ScriptedCitizen(plugin);
		initCitizenInfo(citizen, info);
		citizen.setWorldLocation(info.worldLocation)
			.setScript(info.startScript);

		citizen.baseLocation = info.worldLocation;
		return citizen;
	}

	public static Scenery loadScenery(CitizensPlugin plugin, SceneryInfo info) {
		Scenery scenery = new Scenery(plugin).setModelIDs(info.modelIds)
			.setModelRecolors(info.modelRecolorFind, info.modelRecolorReplace)
			.setIdleAnimation(info.idleAnimation)
			.setScale(info.scale)
			.setTranslate(info.translate)
			.setBaseOrientation(info.baseOrientation)
			.setUUID(info.uuid)
			.setWorldLocation(info.worldLocation)
			.setRegion(info.regionId);

		if (info.mergedObjects != null) {
			info.mergedObjects.forEach(scenery::addMergedObject);
		}

		return scenery;
	}

	public static void forEachActiveEntity(Consumer<Entity> function) {
		for (CitizenRegion r : regionCache.values()) {
			if (r != null) {
				for (Entity e : r.entities.values()) {
					if (e != null && e.distanceToPlayer() <= Util.MAX_ENTITY_RENDER_DISTANCE) {
						function.accept(e);
					}
				}
			}
		}
	}

	public static void forEachEntity(Consumer<Entity> function) {
		regionCache.forEach((regionId, r) -> {
			if (r != null) {
				r.entities.forEach((id, e) -> {
					if (e != null) {
						function.accept(e);
					}
				});
			}
		});
	}

	public static void cleanUp() {
		forEachEntity(Entity::despawn);
		regionCache.clear();
		dirtyRegions.clear();

		for (CitizenRegion r : regionCache.values()) {
			r.citizenRoster.clear();
			r.sceneryRoster.clear();
			r.entities.clear();
			r.executorService.shutdownNow();
		}
	}

	// DEVELOPMENT SECTION
	public static Citizen spawnCitizenFromPanel(CitizenInfo info) {
		Citizen citizen = loadCitizen(plugin, info);
		loadRegion(info.regionId, true);
		CitizenRegion region = loadRegion(info.regionId, true);
		if (region == null) {
			throw new RuntimeException("Null region for ID: " + info.regionId);
		}
		region.entities.put(info.uuid, citizen);
		region.citizenRoster.add(info);
		dirtyRegion(region);
		updateAllEntities();
		return citizen;
	}

	public static Scenery spawnSceneryFromPanel(SceneryInfo info) {
		Scenery scenery = loadScenery(plugin, info);
		CitizenRegion region = loadRegion(info.regionId, true);
		region.entities.put(info.uuid, scenery);
		region.sceneryRoster.add(info);
		dirtyRegion(region);
		updateAllEntities();
		return scenery;
	}

	public static void updateEntity(EntityInfo info) {
		if (info.entityType == EntityType.Scenery) {
			CitizenRegion region = regionCache.get(info.regionId);
			Entity e = region.entities.get(info.uuid);
			Scenery updated = loadScenery(plugin, (SceneryInfo) info);

			addEntityToRegion(updated, info);
			removeEntityFromRegion(e);
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
		clearDirtyRegions();
	}

	public static void updateAllEntities() {
		for (CitizenRegion region : regionCache.values()) {
			region.updateEntities();
		}
		if (plugin.IS_DEVELOPMENT) {
			plugin.panel.update();
		}
	}

	public void saveRegion() throws IOException {
		try {
			Path path = Paths.get(REGIONDATA_DIRECTORY, regionId + ".json");
			Writer wr = new BufferedWriter(new FileWriter(path.toString()));
			GsonBuilder gb = plugin.gson.newBuilder();
			gb.setPrettyPrinting();
			Gson gson = gb.create();
			gson.toJson(this, wr);
			wr.flush();
			wr.close();
		} catch (IOException e) {
			throw new IOException(e);
		}
	}

	public void updateEntities() {
		plugin.clientThread.invokeLater(() -> {
			entities.values().forEach(Entity::update);
		});
	}

	public void runOncePerTimePeriod(int timePeriodSeconds, int callIntervalSeconds, Consumer<Entity> callback) {
		double chance = (double) callIntervalSeconds / timePeriodSeconds;

		List<Entity> entityList = new ArrayList<>(entities.values());

		for (Entity entity : entityList) {
			if (!entity.isActive()) {
				continue;
			}
			if (Math.random() < chance) {
				int delayMs = (Util.getRandom(0, (callIntervalSeconds / 2) * 1000));
				executorService.schedule(() -> callback.accept(entity), delayMs, TimeUnit.MILLISECONDS);
			}
		}
	}
}
