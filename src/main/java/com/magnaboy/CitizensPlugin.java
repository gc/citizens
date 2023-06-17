package com.magnaboy;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.geometry.SimplePolygon;
import net.runelite.client.RuneLite;
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
import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.magnaboy.Util.getRandom;

@Slf4j
@PluginDescriptor(name = "Citizens", description = "Adds citizens to help bring life to the world")
public class CitizensPlugin extends Plugin {
    @Inject
    public Client client;

    @Inject
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

        //For now, the only thing in the panel is dev stuff
        if(IS_DEVELOPMENT) {
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
        overlayManager.remove(citizensOverlay);
        CitizenRegion.cleanUp();
        despawnAll();
        if(IS_DEVELOPMENT)
            panel.cleanup();
    }

    protected void updateAll() {
        getAllEntities().forEach(Entity::update);
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
        if(gameStateChanged.getGameState() == GameState.LOGGED_IN) {
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
    public void onGameTick(GameTick tick)
    {
        updateAll();    //Doing this in State Changed caused a lot of delays
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
        if(IS_DEVELOPMENT)
            panel.update();
    }
    @Subscribe
    public void onMenuOpened(MenuOpened ignored) {
        int firstMenuIndex = 1;

        Point mousePos = client.getMouseCanvasPosition();

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
                    break;
                }
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
        return getAllEntities().filter(Entity::isActive).toArray().length;
    }

    public int countInactiveEntities() {
        return getAllEntities().filter(ent -> {
            return !ent.isActive() && ent != null;
        }).toArray().length;
    }

    public void refreshEntityCollection()
    {
        entitiesAreReady = false;
        entityCollection.clear();
        entityCollection.add(citizens);
        entityCollection.add(scenery);
        entitiesAreReady = true;
    }

    private void checkRegions() throws IOException {
        List<Integer> loaded = Arrays.stream(client.getMapRegions()).boxed().collect(Collectors.toList());
        //Check for newly loaded regions
        for (int i : loaded) {
            if (!activeRegions.containsKey(i)) {
                CitizenRegion region = CitizenRegion.loadRegion(i, this);
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
        //Just clearing the hashmap should trigger a complete reload on the next 'CheckRegions()' call
        if (!plugin.entityCollection.removeAll(new ArrayList<>(plugin.citizens)))
            Util.log("ReloadCitizens(): Did not remove any citizens from list");

        plugin.despawnAll();
        plugin.citizens.clear();
        activeRegions.clear();
        CitizenRegion.clearDirtyRegions();
        try {
            plugin.checkRegions();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Util.log("Reloaded Citizens");
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if(!IS_DEVELOPMENT)
            return;

        if (event.getOption().equals("Walk here")) {
            final Tile selectedSceneTile = client.getSelectedSceneTile();

            client.createMenuEntry(-1)
                    .setOption(ColorUtil.wrapWithColorTag("Citizens", Color.cyan))
                    .setTarget("Select Starting Tile")
                    .setType(MenuAction.RUNELITE)
                    .onClick(e ->
                    {
                        CitizenPanel.selectedPosition = selectedSceneTile.getWorldLocation();
                        panel.update();
                    });
        }
    }
}




