package com.magnaboy;

import net.runelite.api.Actor;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.magnaboy.Util.getRandomItem;

public class Citizen<T extends Citizen<T>> extends Entity<T> {
    private final Client client;
    public final CitizensPlugin plugin;

    @Nullable
    public String activeRemark = null;
    private int remarkTimer = 0;

    public static class Target {
        public WorldPoint worldDestinationPosition;
        public LocalPoint localDestinationPosition;
        public int jauDestinationOrientation;
        public boolean isPoseAnimation;
        public boolean isInteracting;

        @Override
        public String toString() {
            return "Target{" +
                    "worldDestinationPosition=" + worldDestinationPosition +
                    ", localDestinationPosition=" + localDestinationPosition +
                    ", jauDestinationOrientation=" + jauDestinationOrientation +
                    ", isPoseAnimation=" + isPoseAnimation +
                    ", isInteracting=" + isInteracting +
                    '}';
        }
    }

    //	private final int MAX_TARGET_QUEUE_SIZE = 10;
    private final List<Target> targetQueue = new ArrayList<>();
    Animation[] animationPoses = new Animation[8];

    public String[] remarks;
    @Nullable
    public AnimationID movingAnimationId = AnimationID.HumanWalk;
    @Nullable()

    private static final int[][] BLOCKING_DIRECTIONS_5x5 = {
            {CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST},
            {CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST},
            {CollisionDataFlag.BLOCK_MOVEMENT_EAST, CollisionDataFlag.BLOCK_MOVEMENT_EAST, 0, CollisionDataFlag.BLOCK_MOVEMENT_WEST, CollisionDataFlag.BLOCK_MOVEMENT_WEST},
            {CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST, CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST, CollisionDataFlag.BLOCK_MOVEMENT_NORTH, CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST, CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST},
            {CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST, CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST, CollisionDataFlag.BLOCK_MOVEMENT_NORTH, CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST, CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST}};

    private static final int[][] JAU_DIRECTIONS_5X5 = {
            {768, 768, 1024, 1280, 1280},
            {768, 768, 1024, 1280, 1280},
            {512, 512, 0, 1536, 1536},
            {256, 256, 0, 1792, 1792},
            {256, 256, 0, 1792, 1792}};
    private static final int CENTER_INDEX_5X5 = 2;

    private enum POSE_ANIM {
        IDLE,
        WALK,
        RUN,
        WALK_ROTATE_180,
        WALK_STRAFE_LEFT,
        WALK_STRAFE_RIGHT,
        IDLE_ROTATE_LEFT,
        IDLE_ROTATE_RIGHT
    }

    public Citizen(CitizensPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        this.client = plugin.client;
        this.rlObject = client.createRuneLiteObject();
        setPoseAnimations(plugin.client.getLocalPlayer());
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

    public void validate() {
        super.validate();
        if (name == null) {
            throw new IllegalStateException(debugName() + " has no name.");
        }

        if (examine == null) {
            throw new IllegalStateException(debugName() + " has no examine.");
        }

        if (entityType == EntityType.WanderingCitizen) {
            WanderingCitizen casted = (WanderingCitizen) this;
            if (casted.boundingBox == null) {
                throw new IllegalStateException(debugName() + " has no boundingBox.");
            }
            if (casted.wanderRegionBL == null) {
                throw new IllegalStateException(debugName() + " has no wanderRegionBL.");
            }
            if (casted.wanderRegionTR == null) {
                throw new IllegalStateException(debugName() + " has no wanderRegionTR.");
            }
        }

        for (String remark : remarks) {
            if (remark.equals("")) {
                throw new IllegalStateException(debugName() + " has empty remark.");
            }
        }
    }

    public boolean despawn() {
        setAnimation(idleAnimationId.getId());
        targetQueue.clear();
        this.activeRemark = null;
        this.remarkTimer = 0;
        return super.despawn();
    }

    public void setPoseAnimations(Actor actor) {
        this.animationPoses[POSE_ANIM.IDLE.ordinal()] = client.loadAnimation(actor.getIdlePoseAnimation());
        this.animationPoses[POSE_ANIM.WALK.ordinal()] = client.loadAnimation(actor.getWalkAnimation());
        this.animationPoses[POSE_ANIM.RUN.ordinal()] = client.loadAnimation(actor.getRunAnimation());
        this.animationPoses[POSE_ANIM.WALK_ROTATE_180.ordinal()] = client.loadAnimation(actor.getWalkRotate180());
        this.animationPoses[POSE_ANIM.WALK_STRAFE_LEFT.ordinal()] = client.loadAnimation(actor.getWalkRotateLeft()); // rotate is a misnomer here
        this.animationPoses[POSE_ANIM.WALK_STRAFE_RIGHT.ordinal()] = client.loadAnimation(actor.getWalkRotateRight()); // rotate is a misnomer here
        this.animationPoses[POSE_ANIM.IDLE_ROTATE_LEFT.ordinal()] = client.loadAnimation(actor.getIdleRotateLeft());
        this.animationPoses[POSE_ANIM.IDLE_ROTATE_RIGHT.ordinal()] = client.loadAnimation(actor.getIdleRotateRight());
    }

    public Target getCurrentTarget() {
        if (targetQueue.size() == 0) {
            return null;
        }
        return targetQueue.get(0);
    }

    public WorldPoint getWorldLocation() {
        Target currentTarget = getCurrentTarget();
        if (currentTarget != null) {
            return currentTarget.worldDestinationPosition;
        }
        return super.getWorldLocation();
    }

    public void say(String message) {
        if (distanceToPlayer() > 30) {
            return;
        }
        this.activeRemark = message;
        this.remarkTimer = 120;
    }

    public void sayRandomRemark() {
        if (activeRemark == null && remarks != null && remarks.length > 0) {
            say(getRandomItem(remarks));
        }
    }

    public void moveTo(WorldPoint worldPosition) {
        moveTo(worldPosition, 0, false, false);
    }

    public void moveTo(WorldPoint worldPosition, Integer jauOrientation, boolean isInteracting, boolean isPoseAnimation) {
        if (entityType == EntityType.StationaryCitizen) {
            throw new IllegalStateException(debugName() + " is a stationary citizen and cannot move.");
        }

        LocalPoint localPosition = LocalPoint.fromWorld(client, worldPosition);

        if (localPosition == null) {
            log("Is ceasing movement because LP is null");
            return;
        }

        // use current position if nothing is in queue
        WorldPoint prevWorldPosition = getWorldLocation();

        int distance = prevWorldPosition.distanceTo(worldPosition);
        if (distance > 0 && distance <= 2) {
            int dx = worldPosition.getX() - prevWorldPosition.getX();
            int dy = worldPosition.getY() - prevWorldPosition.getY();

            if (distance == 1 && dx != 0 && dy != 0) // test for blockage along diagonal
            {
                // if blocked diagonally, go around in an L shape (2 options)
                int[][] colliders = client.getCollisionMaps()[worldPosition.getPlane()].getFlags();
                final int diagonalTest = BLOCKING_DIRECTIONS_5x5[CENTER_INDEX_5X5 - dy][CENTER_INDEX_5X5 + dx];
                final int axisXTest = BLOCKING_DIRECTIONS_5x5[CENTER_INDEX_5X5][CENTER_INDEX_5X5 + dx] | BLOCKING_DIRECTIONS_5x5[CENTER_INDEX_5X5 + dy][CENTER_INDEX_5X5] | CollisionDataFlag.BLOCK_MOVEMENT_FULL;
                final int axisYTest = BLOCKING_DIRECTIONS_5x5[CENTER_INDEX_5X5 - dy][CENTER_INDEX_5X5] | BLOCKING_DIRECTIONS_5x5[CENTER_INDEX_5X5][CENTER_INDEX_5X5 - dx] | CollisionDataFlag.BLOCK_MOVEMENT_FULL;

                int diagonalFlag = colliders[localPosition.getSceneX()][localPosition.getSceneY()];
                int axisXFlag = colliders[localPosition.getSceneX()][localPosition.getSceneY() - dy];
                int axisYFlag = colliders[localPosition.getSceneX() - dx][localPosition.getSceneY()];

                if ((axisXFlag & axisXTest) != 0 || (axisYFlag & axisYTest) != 0 || (diagonalFlag & diagonalTest) != 0) {
                    distance = 2;

                    // if the priority East-West path is clear, we'll default to this direction
                    if ((axisXFlag & axisXTest) == 0) {
                        dy = 0;
                    } else {
                        dx = 0;
                    }
                }
            } else if (distance == 2 && Math.abs(Math.abs(dy) - Math.abs(dx)) == 1) // test for blockage along knight-style moves
            {
                int[][] colliders = client.getCollisionMaps()[worldPosition.getPlane()].getFlags();
                final int diagonalTest = BLOCKING_DIRECTIONS_5x5[CENTER_INDEX_5X5 - dy][CENTER_INDEX_5X5 + dx];
                final int axisXTest = BLOCKING_DIRECTIONS_5x5[CENTER_INDEX_5X5][CENTER_INDEX_5X5 + dx] | BLOCKING_DIRECTIONS_5x5[CENTER_INDEX_5X5 + dy][CENTER_INDEX_5X5] | CollisionDataFlag.BLOCK_MOVEMENT_FULL;
                final int axisYTest = BLOCKING_DIRECTIONS_5x5[CENTER_INDEX_5X5 - dy][CENTER_INDEX_5X5] | BLOCKING_DIRECTIONS_5x5[CENTER_INDEX_5X5][CENTER_INDEX_5X5 - dx] | CollisionDataFlag.BLOCK_MOVEMENT_FULL;

                int dxSign = Integer.signum(dx);
                int dySign = Integer.signum(dy);
                int diagonalFlag = colliders[localPosition.getSceneX()][localPosition.getSceneY()];
                int axisXFlag = colliders[localPosition.getSceneX()][localPosition.getSceneY() - Integer.signum(dySign)];
                int axisYFlag = colliders[localPosition.getSceneX() - Integer.signum(dxSign)][localPosition.getSceneY()];

                // do we go straight or diagonal? test straight first and fall back to diagonal if it fails
                // priority is West > East > South > North > Southwest > Southeast > Northwest > Northeast
                if ((axisXFlag & axisXTest) == 0 && (axisYFlag & axisYTest) == 0 && (diagonalFlag & diagonalTest) == 0) {
                    // the cardinal direction is clear (or we glitched), so let's go straight
                    if (Math.abs(dx) == 2) {
                        dx = dxSign;
                        dy = 0;
                    } else {
                        dx = 0;
                        dy = dySign;
                    }
                } else {
                    // we've established that the cardinal direction is blocked, so let's go along the diagonal
                    if (Math.abs(dx) == 2) {
                        dx = dxSign;
                    } else {
                        dy = dySign;
                    }
                }
            }

            // handle rotation if we have no interacting target
            if (!isInteracting || jauOrientation == null) {
                // the actor needs to look in the direction being moved toward
                // the distance between these points may be up to 2
                dx = worldPosition.getX() - prevWorldPosition.getX();
                dy = worldPosition.getY() - prevWorldPosition.getY();
                jauOrientation = JAU_DIRECTIONS_5X5[CENTER_INDEX_5X5 - dy][CENTER_INDEX_5X5 + dx];
            }
        }

        Target newTarget = new Target();
        newTarget.worldDestinationPosition = worldPosition;
        newTarget.localDestinationPosition = localPosition;
        newTarget.jauDestinationOrientation = jauOrientation == null ? 0 : jauOrientation;
        newTarget.isInteracting = isInteracting;
        newTarget.isPoseAnimation = isPoseAnimation;
        targetQueue.add(newTarget);
    }

    public void onClientTick() {
        if (remarkTimer > 0) {
            remarkTimer--;
        }
        if (remarkTimer == 0) {
            this.activeRemark = null;
        }
        if (!rlObject.isActive()) {
            return;
        }
        if (entityType == EntityType.StationaryCitizen) return;

        Target nextTarget = getCurrentTarget();
        if (nextTarget != null) {
            int targetPlane = nextTarget.worldDestinationPosition.getPlane();
            LocalPoint targetPosition = nextTarget.localDestinationPosition;
            int targetOrientation = nextTarget.jauDestinationOrientation;

            if (client.getPlane() != targetPlane || targetPosition == null || !targetPosition.isInScene() || targetOrientation < 0) {
                despawn();
                return;
            }

            LocalPoint currentPosition = getLocalLocation();
            int dx = targetPosition.getX() - currentPosition.getX();
            int dy = targetPosition.getY() - currentPosition.getY();

            if (dx != 0 || dy != 0) {
                log("Moving to " + targetPosition + " from " + currentPosition + " dx=" + dx + " dy=" + dy);
                if (rlObject.getAnimation().getId() != movingAnimationId.getId()) {
                    setAnimation(movingAnimationId.getId());
                }

                int speed = 4;
                // only use the delta if it won't send up past the target
                if (Math.abs(dx) > speed) {
                    dx = Integer.signum(dx) * speed;
                }
                if (Math.abs(dy) > speed) {
                    dy = Integer.signum(dy) * speed;
                }

                LocalPoint newLocation = new LocalPoint(currentPosition.getX() + dx, currentPosition.getY() + dy);
                setLocation(newLocation);

                currentPosition = getLocalLocation();
                dx = targetPosition.getX() - currentPosition.getX();
                dy = targetPosition.getY() - currentPosition.getY();
            } else {
                targetQueue.remove(0);
                log("Not moving to " + targetPosition + " from " + currentPosition + " dx=" + dx + " dy=" + dy);
            }

            LocalPoint localLoc = getLocalLocation();
            double intx = localLoc.getX() - targetPosition.getX();
            double inty = localLoc.getY() - targetPosition.getY();

            boolean rotationDone = rotateObject(intx, inty);

            if (dx == 0 && dy == 0 && rotationDone) {
                log("NL222");
            }

            if (targetQueue.size() == 0) {
                stopMoving();
                log("finished moving");
            }
        }
    }

    public void stopMoving() {
        setAnimation(idleAnimationId.getId());
    }
}
