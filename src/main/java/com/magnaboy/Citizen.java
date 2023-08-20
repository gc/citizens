package com.magnaboy;

import static com.magnaboy.Util.getRandomItem;
import javax.annotation.Nullable;
import net.runelite.api.Actor;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.Model;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

public class Citizen<T extends Citizen<T>> extends Entity<T> {
	private final Client client;
	public final CitizensPlugin plugin;

	public static class Target {
		public WorldPoint worldDestinationPosition;
		public LocalPoint localDestinationPosition;
		public int jauDestinationOrientation;
		public boolean isPoseAnimation;
		public boolean isInteracting;
		public boolean isMidPoint;
	}

	private final int MAX_TARGET_QUEUE_SIZE = 10;
	private final Target[] targetQueue = new Target[MAX_TARGET_QUEUE_SIZE];
	private int currentTargetIndex;
	private int targetQueueSize;

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

	Animation[] animationPoses = new Animation[8];

	public Citizen(CitizensPlugin plugin) {
		super(plugin);
		this.plugin = plugin;
		this.client = plugin.client;
		this.rlObject = client.createRuneLiteObject();
		for (int i = 0; i < MAX_TARGET_QUEUE_SIZE; i++) {
			targetQueue[i] = new Target();
		}
		setPoseAnimations(plugin.client.getLocalPlayer());
	}

	public void setAnimation(int animationID) {
		plugin.clientThread.invoke(() -> {
			Animation anim = plugin.client.loadAnimation(animationID);
			rlObject.setAnimation(anim);
		});
	}

	public int getOrientation() {
		return rlObject.getOrientation();
	}

	public void setModel(Model model) {
		rlObject.setModel(model);
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
			if (remark == "") {
				throw new IllegalStateException(debugName() + " has empty remark.");
			}
		}
	}

	public void spawn(WorldPoint position, int jauOrientation) {
		LocalPoint localPosition = LocalPoint.fromWorld(client, position);
		if (localPosition != null && client.getPlane() == position.getPlane()) {
			rlObject.setLocation(localPosition, position.getPlane());
		} else {
			return;
		}
		rlObject.setOrientation(jauOrientation);
		rlObject.setAnimation(null);
		rlObject.setShouldLoop(true);
		rlObject.setActive(true);
		this.currentTargetIndex = 0;
		this.targetQueueSize = 0;
	}

	public boolean despawn() {
		this.targetQueueSize = 0;
		this.currentTargetIndex = 0;
		this.activeRemark = null;
		this.remarkTimer = 0;
		boolean didDespawn = super.despawn();

		if (didDespawn) {
			Util.log("Despawning " + name + ", they are " + distanceToPlayer() + "x tiles away");
		}
		return didDespawn;
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
		Target target = targetQueue[currentTargetIndex];
		if (target.localDestinationPosition == null) {
			return null;
		}
		return target;
	}

	public WorldPoint getWorldLocation() {
		return targetQueueSize > 0 ? targetQueue[currentTargetIndex].worldDestinationPosition : WorldPoint.fromLocal(client, rlObject.getLocation());
	}

	public LocalPoint getLocalLocation() {
		return rlObject.getLocation();
	}

	public boolean isActive() {
		return rlObject.isActive();
	}

	@Nullable
	public String activeRemark = null;
	private int remarkTimer = 0;

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

	// moveTo() adds target movement states to the queue for later per-frame updating for rendering in onClientTick()
	// Set this every game tick for each new position (usually only up to 2 tiles out)
	// This is not set up for pathfinding to the final destination of distant targets (you will just move there directly)
	// It will, however, handle nearby collision detection (1-2 tiles away from you) under certain scenarios
	// jauOrientation is not used if isInteracting is false; it will instead default to the angle being moved towards
	public void moveTo(WorldPoint worldPosition, int jauOrientation, boolean isInteracting, boolean isPoseAnimation) {
		if (entityType == EntityType.StationaryCitizen) {
			throw new IllegalStateException(debugName() + " is a stationary citizen and cannot move.");
		}

		// respawn this actor if it was previously despawned
		if (!rlObject.isActive()) {
			spawn(worldPosition, jauOrientation);

			// if still not active, just exit
			if (!rlObject.isActive()) {
				return;
			}
		}

		// just clear the queue and move immediately to the destination if many ticks behind
		if (targetQueueSize >= MAX_TARGET_QUEUE_SIZE - 2) {
			targetQueueSize = 0;
		}

		int prevTargetIndex = (currentTargetIndex + targetQueueSize - 1) % MAX_TARGET_QUEUE_SIZE;
		int newTargetIndex = (currentTargetIndex + targetQueueSize) % MAX_TARGET_QUEUE_SIZE;
		LocalPoint localPosition = LocalPoint.fromWorld(client, worldPosition);

		if (localPosition == null) {
			return;
		}

		// use current position if nothing is in queue
		WorldPoint prevWorldPosition;
		if (targetQueueSize++ > 0) {
			prevWorldPosition = targetQueue[prevTargetIndex].worldDestinationPosition;
			// TODO: check if a different primaryAnimationID exists; if so, modify the old one with our new one (hopefully this prevents the extra tick of animation repeating)
		} else {
			prevWorldPosition = WorldPoint.fromLocal(client, rlObject.getLocation());
		}

		int distance = prevWorldPosition.distanceTo(worldPosition);
		if (distance > 0 && distance <= 2) {
			int dx = worldPosition.getX() - prevWorldPosition.getX();
			int dy = worldPosition.getY() - prevWorldPosition.getY();

			boolean useMidPointTile = false;

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
					// the path along the diagonal is blocked
					useMidPointTile = true;
					distance = 2; // we are now running in an L shape

					// if the priority East-West path is clear, we'll default to this direction
					if ((axisXFlag & axisXTest) == 0) {
						dy = 0;
					} else {
						dx = 0;
					}
				}
			} else if (distance == 2 && Math.abs(Math.abs(dy) - Math.abs(dx)) == 1) // test for blockage along knight-style moves
			{
				useMidPointTile = true; // we will always need a midpoint for these types of moves
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

			if (useMidPointTile) {
				WorldPoint midPoint = new WorldPoint(prevWorldPosition.getX() + dx, prevWorldPosition.getY() + dy, prevWorldPosition.getPlane());

				// handle rotation if we have no interacting target
				if (!isInteracting) {
					// the actor needs to look in the direction being moved toward
					// the distance between these points should be guaranteed to be 1 here
					dx = midPoint.getX() - prevWorldPosition.getX();
					dy = midPoint.getY() - prevWorldPosition.getY();
					jauOrientation = JAU_DIRECTIONS_5X5[CENTER_INDEX_5X5 - dy][CENTER_INDEX_5X5 + dx];
				}

				this.targetQueue[newTargetIndex].worldDestinationPosition = midPoint;
				this.targetQueue[newTargetIndex].localDestinationPosition = LocalPoint.fromWorld(client, midPoint);
				this.targetQueue[newTargetIndex].jauDestinationOrientation = jauOrientation;
				this.targetQueue[newTargetIndex].isPoseAnimation = isPoseAnimation;
				this.targetQueue[newTargetIndex].isInteracting = isInteracting;
				this.targetQueue[newTargetIndex].isMidPoint = true;

				newTargetIndex = (currentTargetIndex + targetQueueSize++) % MAX_TARGET_QUEUE_SIZE;
				prevWorldPosition = midPoint;
			}

			// handle rotation if we have no interacting target
			if (!isInteracting) {
				// the actor needs to look in the direction being moved toward
				// the distance between these points may be up to 2
				dx = worldPosition.getX() - prevWorldPosition.getX();
				dy = worldPosition.getY() - prevWorldPosition.getY();
				jauOrientation = JAU_DIRECTIONS_5X5[CENTER_INDEX_5X5 - dy][CENTER_INDEX_5X5 + dx];
			}
		}

		this.targetQueue[newTargetIndex].worldDestinationPosition = worldPosition;
		this.targetQueue[newTargetIndex].localDestinationPosition = localPosition;
		this.targetQueue[newTargetIndex].jauDestinationOrientation = jauOrientation;
		this.targetQueue[newTargetIndex].isInteracting = isInteracting;
		this.targetQueue[newTargetIndex].isPoseAnimation = isPoseAnimation;
		this.targetQueue[newTargetIndex].isMidPoint = false;
	}

	// onClientTick() updates the per-frame state needed for rendering actor movement
	public boolean onClientTick() {
		if (remarkTimer > 0) {
			remarkTimer--;
		}
		if (remarkTimer == 0) {
			this.activeRemark = null;
		}
		if (rlObject.isActive()) {
			if (targetQueueSize > 0) {
				int targetPlane = targetQueue[currentTargetIndex].worldDestinationPosition.getPlane();
				LocalPoint targetPosition = targetQueue[currentTargetIndex].localDestinationPosition;
				int targetOrientation = targetQueue[currentTargetIndex].jauDestinationOrientation;

				if (client.getPlane() != targetPlane || targetPosition == null || !targetPosition.isInScene() || targetOrientation < 0) {
					despawn();
					return false;
				}

				LocalPoint currentPosition = getLocalLocation();
				int dx = targetPosition.getX() - currentPosition.getX();
				int dy = targetPosition.getY() - currentPosition.getY();

				if (dx != 0 || dy != 0) {
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
				}

				LocalPoint localLoc = getLocalLocation();
				double intx = localLoc.getX() - targetPosition.getX();
				double inty = localLoc.getY() - targetPosition.getY();

				boolean rotationDone = rotateObject(intx, inty);

				if (dx == 0 && dy == 0 && rotationDone) {
					currentTargetIndex = (currentTargetIndex + 1) % MAX_TARGET_QUEUE_SIZE;
					targetQueueSize--;
				}

				if (targetQueueSize == 0) {
					stopMoving();
				}
			}

			return true;
		}

		return false;
	}

	public void stopMoving() {
		setAnimation(idleAnimationId.getId());
	}
}
