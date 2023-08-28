package com.magnaboy;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.events.*;
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

import javax.inject.Inject;
import javax.inject.Named;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(name = "Citizens", description = "Adds citizens to help bring life to the world")
public class CitizensPlugin extends Plugin {
	public static HashMap<Integer, CitizenRegion> activeRegions = new HashMap<>();
	public static boolean shuttingDown;
	@Inject
	public Client client;
	@Inject
	public ClientThread clientThread;
	public CitizenPanel panel;
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

	@Provides
	CitizensConfig getConfig(ConfigManager configManager) {
		return configManager.getConfig(CitizensConfig.class);
	}

	public boolean isReady() {
		return entitiesAreReady && client.getLocalPlayer() != null;
	}

	@Override
	protected void startUp() {
		CitizenRegion.init(this);

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

		if (isReady()) {
			checkRegions();
		}
		CitizenRegion.updateAllEntities();
	}

	@Override
	protected void shutDown() {
		cleanupAll();
	}

	protected void despawnAll() {
		for (CitizenRegion r : activeRegions.values()) {
			CitizenRegion.forEachActiveEntity((Entity::despawn));
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		GameState newState = gameStateChanged.getGameState();

		if (newState == GameState.LOGGED_IN) {
			checkRegions();
		}

		if (newState == GameState.LOADING) {
			despawnAll();
			CitizenRegion.updateAllEntities();
		}
	}

	@Schedule(
		period = 5,
		unit = ChronoUnit.SECONDS,
		asynchronous = true
	)
	public void citizenBehaviourTick() {
		if (!isReady()) {
			return;
		}

		for (CitizenRegion r : activeRegions.values()) {
			r.percentileAction(75, 4, entity -> {
				if (entity instanceof WanderingCitizen) {
					((WanderingCitizen) entity).wander();
				}
			});

			r.percentileAction(20, 4, entity -> {
				if (entity instanceof Citizen) {
					((Citizen) entity).sayRandomRemark();
				}
			});
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick) {
		CitizenRegion.updateAllEntities();
	}

	@Subscribe
	public void onClientTick(ClientTick ignored) {
		CitizenRegion.forEachActiveEntity((entity) -> {
			if (entity.isCitizen()) {
				((Citizen) entity).onClientTick();
			}
		});
	}

	@Subscribe
	public void onMenuOpened(MenuOpened ignored) {
		final int[] firstMenuIndex = {1};

		Point mousePos = client.getMouseCanvasPosition();
		final AtomicBoolean[] clickedCitizen = {new AtomicBoolean(false)};
		CitizenRegion.forEachActiveEntity(entity -> {
			if (entity.entityType == EntityType.Scenery && !IS_DEVELOPMENT) return;
			if ((entity.name != null && entity.examine != null) || IS_DEVELOPMENT) {
				SimplePolygon clickbox;
				try {
					clickbox = entity.getClickbox();
				} catch (IllegalStateException err) {
					return;
				}
				if (clickbox == null) {
					return;
				}
				boolean doesClickBoxContainMousePos = clickbox.contains(mousePos.getX(), mousePos.getY());
				if (doesClickBoxContainMousePos) {
					if (doesClickBoxContainMousePos) {
						client.createMenuEntry(firstMenuIndex[0])
							.setOption("Examine")
							.setTarget("<col=fffe00>" + entity.name + "</col>")
							.setType(MenuAction.RUNELITE)
							.setParam0(0)
							.setParam1(0)
							.setDeprioritized(true);
					}
				}

				// Select/Deselect
				if (IS_DEVELOPMENT && doesClickBoxContainMousePos) {
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
		CitizenRegion.forEachActiveEntity((entity) -> {
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

	private void checkRegions() {
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
		entitiesAreReady = true;
	}

	private void cleanupAll() {
		shuttingDown = true;
		despawnAll();
		activeRegions.clear();
		overlayManager.remove(citizensOverlay);
		CitizenRegion.cleanUp();
		if (IS_DEVELOPMENT) {
			panel.cleanup();
			panel.update();
		}
		shuttingDown = false;
	}
}



