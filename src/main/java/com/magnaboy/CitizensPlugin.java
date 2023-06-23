package com.magnaboy;

import com.google.inject.Provides;
import static com.magnaboy.Util.getRandom;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
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
	public static HashMap<Integer, CitizenRegion> activeRegions;
	public static boolean shuttingDown;
	@Inject
	public Client client;
	@Inject
	public ClientThread clientThread;
	public CitizenPanel panel;
	public AnimationID[] randomIdleActionAnimationIds = {AnimationID.Flex};
	public List<Animation> animationPoses = new ArrayList<Animation>();
	@Inject
	@Named("developerMode")
	public boolean IS_DEVELOPMENT;
	public boolean entitiesAreReady = false;
	@Inject
	ChatMessageManager chatMessageManager;
	@Inject
	@Getter
	private CitizensConfig config;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private CitizensOverlay citizensOverlay;
	@Inject
	private ClientToolbar clientToolbar;

	public static void reloadCitizens(CitizensPlugin plugin) {
		CitizenRegion.cleanUp();
		plugin.cleanup();
	}

	@Provides
	CitizensConfig getConfig(ConfigManager configManager) {
		return configManager.getConfig(CitizensConfig.class);
	}

	public Animation getAnimation(AnimationID animID) {
		Animation anim = animationPoses.stream().filter(c -> c.getId() == animID.getId()).findFirst().orElse(null);
		if (anim == null) {
			throw new IllegalStateException("Tried to get non-existant anim: " + animID);
		}
		return anim;
	}

	public boolean isReady() {
		return entitiesAreReady && client.getLocalPlayer() != null;
	}

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
	}

	@Override
	protected void shutDown() {
		cleanupAll();
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

	protected void updateAll() {
		clientThread.invokeLater(() -> {
			for (CitizenRegion r : activeRegions.values()) {
				r.updateEntities();
			}
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
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
			CitizenRegion.forEachEntity(entity -> {
				entity.update();
				if (!entity.shouldRender() || !entity.isActive()) {
					return;
				}
				int random = getRandom(1, 10);
				if (random < 4) {
					if (entity instanceof WanderingCitizen) {
						((WanderingCitizen) entity).wander();
					}
				}

				if (random == 7 || random == 8 || random == 9) {
					if (entity instanceof Citizen) {
						((Citizen) entity).triggerIdleAnimation();
					}
				}

				if (random == 10) {
					if (entity instanceof Citizen) {
						((Citizen) entity).sayRandomRemark();
					}
				}
			});
		});
		panel.update();
	}

	@Subscribe
	public void onGameTick(GameTick tick) {
		updateAll();
	}

	@Subscribe
	public void onClientTick(ClientTick ignored) {
		CitizenRegion.forEachEntity((entity) -> {
			if (entity.isActive() && entity.isCitizen()) {
				((Citizen) entity).onClientTick();
			}
		});
		try {
			//TODO: Try to find a better way of checking for regions. Could not find some sort region loaded event or similiar
			checkRegions();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Subscribe
	public void onMenuOpened(MenuOpened ignored) {
		final int[] firstMenuIndex = {1};

		Point mousePos = client.getMouseCanvasPosition();
		final AtomicBoolean[] clickedCitizen = {new AtomicBoolean(false)};
		CitizenRegion.forEachEntity(entity -> {
			if (entity.name == null || entity.examine == null) {
				return;
			}
			if (entity.isActive()) {
				SimplePolygon clickbox = entity.getClickbox();
				if (clickbox == null) {
					return;
				}
				boolean doesClickBoxContainMousePos = clickbox.contains(mousePos.getX(), mousePos.getY());
				if (doesClickBoxContainMousePos) {

					client.createMenuEntry(firstMenuIndex[0])
						.setOption("Examine")
						.setTarget("<col=fffe00>" + entity.name + "</col>")
						.setType(MenuAction.RUNELITE)
						.setParam0(0)
						.setParam1(0)
						.setDeprioritized(true);

					if (IS_DEVELOPMENT) {
						String action = "Select";
						if (CitizenPanel.selectedEntity == entity) {
							action = "Deselect";
							clickedCitizen[0].set(true);
						}

						client.createMenuEntry(firstMenuIndex[0]++)
							.setOption(ColorUtil.wrapWithColorTag("Citizen Editor", Color.cyan))
							.setTarget(action + " <col=fffe00>" + entity.name + "</col>")
							.setType(MenuAction.RUNELITE)
							.setDeprioritized(true)
							.onClick(e -> {
								panel.setSelectedEntity(entity);
								panel.update();
							});
					}
				}
			}
		});

		if (IS_DEVELOPMENT) {
			// Tile Selection
			final Tile selectedSceneTile = client.getSelectedSceneTile();
			final boolean same = CitizenPanel.selectedPosition != null && CitizenPanel.selectedPosition.equals(selectedSceneTile.getWorldLocation());
			final String action = same ? "Deselect" : "Select";
			client.createMenuEntry(firstMenuIndex[0]++)
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
			// Entity Deselect (from anywhere)
			if (CitizenPanel.selectedEntity != null && !clickedCitizen[0].get()) {
				String name = "Scenery Object";
				if (CitizenPanel.selectedEntity instanceof Citizen) {
					name = CitizenPanel.selectedEntity.name;
				}
				client.createMenuEntry(firstMenuIndex[0] - 1)
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
		CitizenRegion.forEachEntity((entity) -> {
			if (event.getMenuTarget().equals("<col=fffe00>" + entity.name + "</col>")) {
				event.consume();
				String chatMessage = new ChatMessageBuilder()
					.append(ChatColorType.NORMAL)
					.append(entity.examine)
					.build();

				chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.NPC_EXAMINE)
					.runeLiteFormattedMessage(chatMessage)
					.timestamp((int) (System.currentTimeMillis() / 1000)).build());

			}
		});
	}

	private void checkRegions() throws IOException {
		List<Integer> loaded = Arrays.stream(client.getMapRegions()).boxed().collect(Collectors.toList());
		// Check for newly loaded regions
		for (int i : loaded) {
			if (!activeRegions.containsKey(i)) {
				CitizenRegion region = CitizenRegion.loadRegion(i);
				if (region != null) {
					activeRegions.put(i, region);
				}
			}
		}
	}

	private void cleanup() {
		activeRegions.clear();
	}

	private void cleanupAll() {
		shuttingDown = true;
		overlayManager.remove(citizensOverlay);
		this.cleanup();
		CitizenRegion.cleanUp();
		if (IS_DEVELOPMENT) {
			panel.cleanup();
			panel.update();
		}
		shuttingDown = false;
	}
}



