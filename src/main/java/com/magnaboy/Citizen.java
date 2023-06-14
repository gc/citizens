package com.magnaboy;

import net.runelite.api.Animation;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.geometry.SimplePolygon;

import javax.annotation.Nullable;
import java.util.*;

import static com.magnaboy.Util.getRandomItem;


public class Citizen<T extends Citizen<T>> extends Entity<T> {
    public String[] remarks;
    public String name;
    public String examine;
    private int cTargetIndex;
    private int lastDistance;
    @Nullable
    public String activeRemark = null;
    private int remarkTimer = 0;
    public int speed = 4;
    private SimplePolygon clickbox;
    protected final int MAX_TARGET_QUEUE_SIZE = 10;
    final ArrayList<Target> targetQueue = new ArrayList<Target>(MAX_TARGET_QUEUE_SIZE);

    protected int targetQueueSize;
    protected final List<ExtraObject> extraObjects = new ArrayList<>();
    public AnimationID[] randomAnimations;
    public AnimationID movingAnimationId = AnimationID.HumanWalk;
    // Remember last known locations so after logging in/out, they are in the same place.
    public WorldPoint lastKnownLocation;

    public class Target {
        public WorldPoint worldDestinationPosition;
        public LocalPoint localDestinationPosition;
        public int currentDistance;
    }

    public Citizen(CitizensPlugin plugin) {
        super(plugin);
    }

    public T setMovAnimID(AnimationID anim) {
        this.movingAnimationId = anim;
        return (T) this;
    }

    public T setName(String name) {
        this.name = name;
        return (T) this;
    }

    public T setExamine(String examine) {
        this.examine = examine;
        return (T) this;
    }

    public T setRemarks(String[] remarks) {
        this.remarks = remarks;
        return (T) this;
    }

    public T addExtraObject(ExtraObject obj) {
        obj.setCitizen(this);
        this.extraObjects.add(obj);
        return (T) this;
    }

    public T setRandomAnimations(AnimationID[] randomAnimations) {
        this.randomAnimations = randomAnimations;
        return (T) this;
    }

    public void triggerIdleAnimation() {
        if (randomAnimations == null) {
            return;
        }
        AnimationID animID = getRandomItem(randomAnimations);
        Animation anim = plugin.getAnimation(animID);
        rlObject.setAnimation(anim);
        // TODO: cancel this timer on plugin shutdown
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                rlObject.setAnimation(plugin.getAnimation(idleAnimationId));
            }
            // TODO: this delay is random
        }, 600 * 8);
    }

    public T setLocation(WorldPoint location) {
        lastKnownLocation = location;
        return super.setLocation(location);
    }

    public void spawn() {
        System.out.println("Spawning " + name + ", " + distanceToPlayer() + "x" +
                " tiles away from the player");

        for (int i = 0; i < MAX_TARGET_QUEUE_SIZE; i++) {
            targetQueue.add(new Target());
        }
        this.targetQueueSize = 0;
        super.spawn();

        for (ExtraObject obj : extraObjects) {
            plugin.client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "Spawning an object for " + name + ", " + obj.distanceToPlayer() + "x tiles from player",
                    null);
            obj.spawn();
        }
    }

    public void despawn() {
        System.out.println("Despawning " + name + ".");
        this.targetQueueSize = 0;
        super.despawn();
        for (ExtraObject obj : extraObjects) {
            obj.despawn();
        }
    }

    public void say(String message) {
        if (distanceToPlayer() > 30) {
            return;
        }
        this.activeRemark = message;
        this.remarkTimer = 80;
        plugin.client.addChatMessage(ChatMessageType.PUBLICCHAT, this.name, message, null);
    }

    @Override
    public void update() {
        boolean inScene = shouldRender();
        System.out.println(name + " is updating: " + (inScene ? "in scene" : "NOT in scene") + " and " + (isActive()
                ? "active" : "inactive"));
        super.update();
    }

    public int getOrientation() {
        return rlObject.getOrientation();
    }

    public SimplePolygon getClickbox() {
        return clickbox;
    }

    public boolean isRemarking() {
        return this.activeRemark != null;
    }

    public void moveTo(WorldPoint worldPosition) {
        if (!rlObject.isActive()) {
            spawn();
        }

        LocalPoint localPosition = LocalPoint.fromWorld(plugin.client, worldPosition);

        // just clear the queue and move immediately to the destination if many ticks behind
        if (targetQueueSize >= MAX_TARGET_QUEUE_SIZE - 2) {
            targetQueueSize = 0;
        }

        int prevTargetIndex = (cTargetIndex + targetQueueSize - 1) % MAX_TARGET_QUEUE_SIZE;
        int newTargetIndex = (cTargetIndex + targetQueueSize) % MAX_TARGET_QUEUE_SIZE;

        if (localPosition == null) {
            return;
        }

        WorldPoint prevWorldPosition;
        if (targetQueueSize++ > 0) {
            prevWorldPosition = targetQueue.get(prevTargetIndex).worldDestinationPosition;
        } else {
            prevWorldPosition = WorldPoint.fromLocal(plugin.client, rlObject.getLocation());
        }

        int distance = prevWorldPosition.distanceTo(worldPosition);

        Target someTarget = this.targetQueue.get(newTargetIndex);
        someTarget.worldDestinationPosition = worldPosition;
        someTarget.localDestinationPosition = localPosition;
        someTarget.currentDistance = distance;
    }

    public void onClientTick() {
        movementTick();
    }

    public void movementTick() {
        if (remarkTimer > 0) {
            remarkTimer--;
        }
        if (remarkTimer == 0) {
            this.activeRemark = null;
        }
        if (rlObject.isActive()) {
            if (targetQueueSize > 0) {
                Target someTarget = targetQueue.get(cTargetIndex);
                if (someTarget == null || someTarget.worldDestinationPosition == null)
                    return;
                int targetPlane = someTarget.worldDestinationPosition.getPlane();

                LocalPoint targetPosition = someTarget.localDestinationPosition;

                if (targetPosition == null) {
                    despawn();
                    return;
                }

                double intx = rlObject
                        .getLocation()
                        .getX() - targetPosition.getX();
                double inty = rlObject
                        .getLocation()
                        .getY() - targetPosition.getY();

                boolean rotationDone = rotateObject(intx, inty);

                // Citizen is no longer in a visible area on our client, so let's despawn it
                if (plugin.client.getPlane() != targetPlane || !targetPosition.isInScene()) {
                    despawn();
                    return;
                }

                // Apply animation if move-speed / distance has changed
                if (lastDistance != someTarget.currentDistance) {
                    rlObject.setAnimation(plugin.getAnimation(movingAnimationId));
                }

                this.lastDistance = someTarget.currentDistance;

                LocalPoint currentPosition = rlObject.getLocation();
                int dx = targetPosition.getX() - currentPosition.getX();
                int dy = targetPosition.getY() - currentPosition.getY();

                // are we not where we need to be?
                if (dx != 0 || dy != 0) {
                    // only use the delta if it won't send up past the target
                    if (Math.abs(dx) > speed) {
                        dx = Integer.signum(dx) * speed;
                    }

                    if (Math.abs(dy) > speed) {
                        dy = Integer.signum(dy) * speed;
                    }

                    LocalPoint newLocation = new LocalPoint(currentPosition.getX() + dx, currentPosition.getY() + dy);

                    rlObject.setLocation(newLocation, plane);

                    int currentX = rlObject
                            .getLocation()
                            .getX();
                    int currentY = rlObject
                            .getLocation()
                            .getY();
                    dx = targetPosition.getX() - currentX;
                    dy = targetPosition.getY() - currentY;
                }

                if (dx == 0 && dy == 0 && rotationDone) {
                    cTargetIndex = (cTargetIndex + 1) % MAX_TARGET_QUEUE_SIZE;
                    targetQueueSize--;
                    rlObject.setAnimation(plugin.getAnimation(this.idleAnimationId));
                }
            }

            LocalPoint lp = getLocalLocation();
            int zOff = Perspective.getTileHeight(plugin.client, lp, plugin.client.getPlane());
            if (rlObject.getModel() == null) {
                System.out.println("[Citizens] Model is null for " + this.name);
                return;
            }
            clickbox = calculateAABB(plugin.client,
                    rlObject.getModel(),
                    rlObject.getOrientation(),
                    lp.getX(),
                    lp.getY(),
                    plugin.client.getPlane(),
                    zOff);

            location = WorldPoint.fromLocalInstance(plugin.client, getLocalLocation());
        }

    }

}
