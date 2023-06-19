package com.magnaboy;

import com.google.inject.Provides;
import static com.magnaboy.Util.getRandom;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Named;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Animation;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.geometry.SimplePolygon;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(name = "Citizens", description = "Adds citizens to help bring life to the world")
public class CitizensPlugin extends Plugin {
	@Inject
	public Client client;

	@Inject
	@Getter
	private CitizensConfig config;

	@Provides
	CitizensConfig getConfig(ConfigManager configManager) {
		return configManager.getConfig(CitizensConfig.class);
	}

	@Inject
	private OverlayManager overlayManager;

	@Inject
	ChatMessageManager chatMessageManager;

	@Inject
	private CitizensOverlay citizensOverlay;

	@Inject
	public ClientThread clientThread;

	public CitizenPanel panel;
	public AnimationID[] randomIdleActionAnimationIds = {AnimationID.Flex};
	public List<Animation> animationPoses = new ArrayList<Animation>();
	public List<Citizen> citizens = new ArrayList<Citizen>();
	public List<Scenery> scenery = new ArrayList<Scenery>();
	public List<List<? extends Entity>> entityCollection = new ArrayList<>();
	public static HashMap<Integer, CitizenRegion> activeRegions;

	@Inject
	@Named("developerMode")
	public boolean IS_DEVELOPMENT;

	public Animation getAnimation(AnimationID animID) {
		Animation anim = animationPoses.stream().filter(c -> c.getId() == animID.getId()).findFirst().orElse(null);
		if (anim == null) {
			throw new IllegalStateException("Tried to get non-existant anim: " + animID);
		}
		return anim;
	}

	public boolean entitiesAreReady = false;

	public boolean isReady() {
		return entitiesAreReady && client.getLocalPlayer() != null;
	}

	@Inject
	private ClientToolbar clientToolbar;

	@Override
	protected void startUp() {
		CitizenRegion.init(this);
		activeRegions = new HashMap<>();

		// For now, the only thing in the panel is dev stuff
		if (IS_DEVELOPMENT) {
			panel = injector.getInstance(CitizenPanel.class);
			panel.init(this, citizensOverlay);
			// Add to sidebar
			final BufferedImage icon = ImageUtil.loadImageResource(CitizensPlugin.class, "/citizens_icon.png");
			NavigationButton navButton = NavigationButton.builder()
				.tooltip("Citizens")
				.icon(icon)
				.priority(7)
				.panel(panel)
				.build();
			clientToolbar.addNavigation(navButton);

			overlayManager.add(citizensOverlay);
		}

		for (AnimationID animId : randomIdleActionAnimationIds) {
			loadAnimation(animId);
		}

		for (AnimationID idList : AnimationID.values()) {
			loadAnimation(idList);
		}

		Collections.shuffle(citizens);
		citizens.forEach((citizen) -> {
			if (citizen.worldLocation == null) {
				throw new IllegalStateException(citizen.name + " has no initial loc");
			}
		});
	}

	public void loadAnimation(AnimationID animId) {
		clientThread.invoke(() -> {
			Animation anim = client.loadAnimation(animId.getId());
			if (anim == null) {
				throw new IllegalStateException("Tried to load non-existant anim: " + animId);
			}
			animationPoses.add(anim);
		});
	}

	@Override
	protected void shutDown() {
		despawnAll();
		cleanupAll();
	}

	protected void updateAll() {
		clientThread.invokeLater(() -> {
			getAllEntities().forEach(Entity::update);
		});
	}

	protected void despawnAll() {
		Util.log("Despawning all entities");
		getAllEntities().forEach(Entity::despawn);
	}

	protected Stream<? extends Entity> getAllEntities() {
		return entityCollection.stream().flatMap(List::stream);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN) {
			despawnAll();
			return;
		}
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
			try {
				checkRegions();
				entitiesAreReady = true;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Schedule(
		period = 5,
		unit = ChronoUnit.SECONDS,
		asynchronous = true
	)
	public void citizenTick() {
		if (!isReady()) {
			return;
		}
		clientThread.invokeLater(() -> {
			for (Citizen citizen : citizens) {
				citizen.update();
				if (!citizen.shouldRender() || !citizen.isActive()) {
					continue;
				}
				int random = getRandom(1, 10);
				if (random < 4) {
					if (citizen instanceof WanderingCitizen) {
						((WanderingCitizen) citizen).wander();
					}
				}

				if (random == 7 || random == 8 || random == 9) {
					citizen.triggerIdleAnimation();
				}

				if (random == 10) {
					citizen.sayRandomRemark();
				}
			}
		});
	}

	@Subscribe
	public void onGameTick(GameTick tick) {
		updateAll();
	}

	@Subscribe
	public void onClientTick(ClientTick ignored) {
		for (Citizen citizen : citizens) {
			if (citizen.isActive()) {
				citizen.onClientTick();
			}
		}
		try {
			//TODO: Try to find a better way of checking for regions. Could not find some sort region loaded event or similiar
			checkRegions();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		if (IS_DEVELOPMENT) {
			panel.update();
		}
	}

	@Subscribe
	public void onMenuOpened(MenuOpened ignored) {
		int firstMenuIndex = 1;

		Point mousePos = client.getMouseCanvasPosition();
		boolean clickedCitizen = false;
		for (Citizen citizen : citizens) {
			if (citizen.isActive()) {
				SimplePolygon clickbox = citizen.getClickbox();
				if (clickbox == null) {
					continue;
				}
				boolean doesClickBoxContainMousePos = clickbox.contains(mousePos.getX(), mousePos.getY());
				if (doesClickBoxContainMousePos) {

					client.createMenuEntry(firstMenuIndex)
						.setOption("Examine")
						.setTarget("<col=fffe00>" + citizen.name + "</col>")
						.setType(MenuAction.RUNELITE)
						.setParam0(0)
						.setParam1(0)
						.setDeprioritized(true);

					if (IS_DEVELOPMENT) {
						String action = "Select";
						if (CitizenPanel.selectedEntity == citizen) {
							action = "Deselect";
							clickedCitizen = true;
						}

						client.createMenuEntry(firstMenuIndex++)
							.setOption(ColorUtil.wrapWithColorTag("Citizen Editor", Color.cyan))
							.setTarget(action + " <col=fffe00>" + citizen.name + "</col>")
							.setType(MenuAction.RUNELITE)
							.setDeprioritized(true)
							.onClick(e -> {
								panel.setSelectedEntity(citizen);
								panel.update();
							});
					}
					break;
				}
			}
		}
		if (IS_DEVELOPMENT) {
			//Tile Selection
			final Tile selectedSceneTile = client.getSelectedSceneTile();
			final boolean same = CitizenPanel.selectedPosition != null && Util.samePosition(CitizenPanel.selectedPosition, selectedSceneTile.getWorldLocation());
			final String action = same ? "Deselect" : "Select";
			client.createMenuEntry(firstMenuIndex++)
				.setOption(ColorUtil.wrapWithColorTag("Citizen Editor", Color.cyan))
				.setTarget(action + " <col=fffe00>Tile</col>")
				.setType(MenuAction.RUNELITE)
				.setDeprioritized(true)
				.onClick(e -> {
					if (same) {
						CitizenPanel.selectedPosition = null;
					} else {
						CitizenPanel.selectedPosition = selectedSceneTile.getWorldLocation();
					}
					panel.update();
				});
			//Entity Deselect (from anywhere)
			if (CitizenPanel.selectedEntity != null && !clickedCitizen) {
				String name = "Scenery Object";
				if (CitizenPanel.selectedEntity instanceof Citizen) {
					name = ((Citizen) CitizenPanel.selectedEntity).name;
				}
				client.createMenuEntry(firstMenuIndex - 1)
					.setOption(ColorUtil.wrapWithColorTag("Citizen Editor", Color.cyan))
					.setTarget("Deselect <col=fffe00>" + name + "</col>")
					.setType(MenuAction.RUNELITE)
					.setDeprioritized(true)
					.onClick(e -> {
						panel.setSelectedEntity(CitizenPanel.selectedEntity);
						panel.update();
					});
			}
		}
	}

	@Subscribe
	private void onMenuOptionClicked(MenuOptionClicked event) {
		if (!event.getMenuOption().equals("Examine")) {
			return;
		}
		for (Citizen citizen : citizens) {
			if (event.getMenuTarget().equals("<col=fffe00>" + citizen.name + "</col>")) {
				event.consume();
				String chatMessage = new ChatMessageBuilder()
					.append(ChatColorType.NORMAL)
					.append(citizen.examine)
					.build();

				chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.NPC_EXAMINE)
					.runeLiteFormattedMessage(chatMessage)
					.timestamp((int) (System.currentTimeMillis() / 1000)).build());

				break;
			}
		}
	}

	public int countActiveEntities() {
		return getAllEntities().filter(ent -> ent.isActive() && ent != null).toArray().length;
	}

	public int countInactiveEntities() {
		return getAllEntities().filter(ent -> !ent.isActive() && ent != null).toArray().length;
	}

	public void refreshEntityCollection() {
		entitiesAreReady = false;
		entityCollection.clear();
		entityCollection.add(citizens);
		entityCollection.add(scenery);
		entitiesAreReady = true;
	}

	private void checkRegions() throws IOException {
		List<Integer> loaded = Arrays.stream(client.getMapRegions()).boxed().collect(Collectors.toList());
		// Check for newly loaded regions
		for (int i : loaded) {
			if (!activeRegions.containsKey(i)) {
				CitizenRegion region = CitizenRegion.loadRegion(i);
				if (region != null) {
					activeRegions.put(i, region);
					citizens.addAll(region.citizens.values());

					scenery.addAll(region.scenery.values());
					refreshEntityCollection();
					Util.log("Loaded Region: " + i + " | Contains: " + region.citizens.size() + " citizens / "
						+ region.scenery.size() + " Scenery Objects");
				}
			}
		}
	}

	public static void reloadCitizens(CitizensPlugin plugin) {
		Util.log("Reloading Citizens");
		plugin.despawnAll();
		plugin.cleanup();
		CitizenRegion.clearDirtyRegions();
		try {
			plugin.checkRegions();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		Util.log("Reloaded Citizens");
	}

	public void despawnEntity(Entity e)
	{
		if(e instanceof Citizen) {
			citizens.remove((Citizen) e);
		}
		if(e instanceof Scenery) {
			scenery.remove((Scenery) e);
		}
		e.despawn();
	}

	private void cleanup()
	{
		entityCollection.clear();
		citizens.clear();
		scenery.clear();
		activeRegions.clear();
	}

	private void cleanupAll()
	{
		overlayManager.remove(citizensOverlay);
		entityCollection.clear();
		citizens.clear();
		scenery.clear();
		activeRegions.clear();
		CitizenRegion.cleanUp();
		if (IS_DEVELOPMENT) {
			panel.cleanup();
		}
	}
}




