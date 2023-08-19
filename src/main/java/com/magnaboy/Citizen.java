package com.magnaboy;

import static com.magnaboy.Util.getRandomItem;
import java.util.Timer;
import java.util.TimerTask;
import javax.annotation.Nullable;
import net.runelite.api.Animation;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

public class Citizen<T extends Citizen<T>> extends Entity<T> {
	public String[] remarks;
	@Nullable
	public String activeRemark = null;
	public int speed = 4;
	public AnimationID[] randomAnimations;
	public AnimationID movingAnimationId = AnimationID.HumanWalk;
	@Nullable()
	Target currentTarget;
	private int remarkTimer = 0;

	public Citizen(CitizensPlugin plugin) {
		super(plugin);
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

	public boolean despawn() {
		this.currentTarget = null;
		this.activeRemark = null;
		this.remarkTimer = 0;
		boolean didDespawn = super.despawn();

		if (didDespawn) {
			Util.log("Despawning " + name + ", they are " + distanceToPlayer() + "x tiles away");
		}
		return didDespawn;
	}

	public boolean spawn() {
		boolean didSpawn = super.spawn();
		if (didSpawn) {
			Util.log(name + " spawned " + distanceToPlayer() + "x tiles from player");
		}

		return didSpawn;
	}

	public void sayRandomRemark() {
		if (activeRemark == null && remarks != null && remarks.length > 0) {
			say(getRandomItem(remarks));
		}
	}

	public void say(String message) {
		if (distanceToPlayer() > 30) {
			return;
		}
		this.activeRemark = message;
		this.remarkTimer = 120;
	}

	public void moveTo(WorldPoint worldPosition) {
		if (!rlObject.isActive()) {
			spawn();
		}

		LocalPoint localPosition = LocalPoint.fromWorld(plugin.client, worldPosition);

		WorldPoint prevWorldPosition;
		if (currentTarget != null) {
			prevWorldPosition = currentTarget.worldDestinationPosition;
		} else {
			prevWorldPosition = WorldPoint.fromLocal(plugin.client, getLocalLocation());
		}

		int distance = prevWorldPosition.distanceTo(worldPosition);

		currentTarget = new Target();
		currentTarget.worldDestinationPosition = worldPosition;
		currentTarget.localDestinationPosition = localPosition;
		currentTarget.currentDistance = distance;
	}

	public void onClientTick() {
		if (remarkTimer > 0) {
			remarkTimer--;
		}
		if (remarkTimer == 0) {
			this.activeRemark = null;
		}
		movementTick();
	}

	public void stopMoving() {
		currentTarget = null;
		rlObject.setAnimation(plugin.getAnimation(this.idleAnimationId));
	}

	private static class JTarget {
		public WorldPoint worldDestinationPosition;
		public LocalPoint localDestinationPosition;
		public int tileMovementSpeed;
		public int jauDestinationOrientation;
		public int primaryAnimationID;
		public boolean isPoseAnimation;
		public boolean isInteracting;
		public boolean isMidPoint;
		public boolean isInstanced;
	}

	private final int MAX_TARGET_QUEUE_SIZE = 10;
	private final JTarget[] targetQueue = new JTarget[MAX_TARGET_QUEUE_SIZE];
	private int currentTargetIndex;
	private int targetQueueSize;

	public void spawn(WorldPoint position, int jauOrientation) {
		LocalPoint localPosition = LocalPoint.fromWorld(plugin.client, position);
		if (localPosition != null && plugin.client.getPlane() == position.getPlane()) {
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

	public void moveTo(WorldPoint worldPosition, int jauOrientation, int primaryAnimationID, boolean isInteracting, boolean isPoseAnimation, boolean isInstanced) {
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
		LocalPoint localPosition = LocalPoint.fromWorld(plugin.client, worldPosition);

		if (localPosition == null) {
			return;
		}

		// use current position if nothing is in queue
		WorldPoint prevWorldPosition;
		if (targetQueueSize++ > 0) {
			prevWorldPosition = targetQueue[prevTargetIndex].worldDestinationPosition;
			// TODO: check if a different primaryAnimationID exists; if so, modify the old one with our new one (hopefully this prevents the extra tick of animation repeating)
		} else {
			prevWorldPosition = WorldPoint.fromLocal(plugin.client, rlObject.getLocation());
		}

		int distance = prevWorldPosition.distanceTo(worldPosition);
		if (distance > 0 && distance <= 2) {
			int dx = worldPosition.getX() - prevWorldPosition.getX();
			int dy = worldPosition.getY() - prevWorldPosition.getY();

			boolean useMidPointTile = false;

			if (distance == 1 && dx != 0 && dy != 0) // test for blockage along diagonal
			{
				// if blocked diagonally, go around in an L shape (2 options)
				int[][] colliders = plugin.client.getCollisionMaps()[worldPosition.getPlane()].getFlags();
				final int diagonalTest = Util.BLOCKING_DIRECTIONS_5x5[Util.CENTER_INDEX_5X5 - dy][Util.CENTER_INDEX_5X5 + dx];
				final int axisXTest = Util.BLOCKING_DIRECTIONS_5x5[Util.CENTER_INDEX_5X5][Util.CENTER_INDEX_5X5 + dx] | Util.BLOCKING_DIRECTIONS_5x5[Util.CENTER_INDEX_5X5 + dy][Util.CENTER_INDEX_5X5] | CollisionDataFlag.BLOCK_MOVEMENT_FULL;
				final int axisYTest = Util.BLOCKING_DIRECTIONS_5x5[Util.CENTER_INDEX_5X5 - dy][Util.CENTER_INDEX_5X5] | Util.BLOCKING_DIRECTIONS_5x5[Util.CENTER_INDEX_5X5][Util.CENTER_INDEX_5X5 - dx] | CollisionDataFlag.BLOCK_MOVEMENT_FULL;

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
				int[][] colliders = plugin.client.getCollisionMaps()[worldPosition.getPlane()].getFlags();
				final int diagonalTest = Util.BLOCKING_DIRECTIONS_5x5[Util.CENTER_INDEX_5X5 - dy][Util.CENTER_INDEX_5X5 + dx];
				final int axisXTest = Util.BLOCKING_DIRECTIONS_5x5[Util.CENTER_INDEX_5X5][Util.CENTER_INDEX_5X5 + dx] | Util.BLOCKING_DIRECTIONS_5x5[Util.CENTER_INDEX_5X5 + dy][Util.CENTER_INDEX_5X5] | CollisionDataFlag.BLOCK_MOVEMENT_FULL;
				final int axisYTest = Util.BLOCKING_DIRECTIONS_5x5[Util.CENTER_INDEX_5X5 - dy][Util.CENTER_INDEX_5X5] | Util.BLOCKING_DIRECTIONS_5x5[Util.CENTER_INDEX_5X5][Util.CENTER_INDEX_5X5 - dx] | CollisionDataFlag.BLOCK_MOVEMENT_FULL;

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
					jauOrientation = Util.JAU_DIRECTIONS_5X5[Util.CENTER_INDEX_5X5 - dy][Util.CENTER_INDEX_5X5 + dx];
				}

				this.targetQueue[newTargetIndex].worldDestinationPosition = midPoint;
				this.targetQueue[newTargetIndex].localDestinationPosition = LocalPoint.fromWorld(plugin.client, midPoint);
				this.targetQueue[newTargetIndex].tileMovementSpeed = distance;
				this.targetQueue[newTargetIndex].jauDestinationOrientation = jauOrientation;
				this.targetQueue[newTargetIndex].primaryAnimationID = primaryAnimationID;
				this.targetQueue[newTargetIndex].isPoseAnimation = isPoseAnimation;
				this.targetQueue[newTargetIndex].isInteracting = isInteracting;
				this.targetQueue[newTargetIndex].isMidPoint = true;
				this.targetQueue[newTargetIndex].isInstanced = isInstanced;

				newTargetIndex = (currentTargetIndex + targetQueueSize++) % MAX_TARGET_QUEUE_SIZE;
				prevWorldPosition = midPoint;
			}

			// handle rotation if we have no interacting target
			if (!isInteracting) {
				// the actor needs to look in the direction being moved toward
				// the distance between these points may be up to 2
				dx = worldPosition.getX() - prevWorldPosition.getX();
				dy = worldPosition.getY() - prevWorldPosition.getY();
				jauOrientation = Util.JAU_DIRECTIONS_5X5[Util.CENTER_INDEX_5X5 - dy][Util.CENTER_INDEX_5X5 + dx];
			}
		}

		this.targetQueue[newTargetIndex].worldDestinationPosition = worldPosition;
		this.targetQueue[newTargetIndex].localDestinationPosition = localPosition;
		this.targetQueue[newTargetIndex].tileMovementSpeed = distance;
		this.targetQueue[newTargetIndex].jauDestinationOrientation = jauOrientation;
		this.targetQueue[newTargetIndex].primaryAnimationID = primaryAnimationID;
		this.targetQueue[newTargetIndex].isInteracting = isInteracting;
		this.targetQueue[newTargetIndex].isPoseAnimation = isPoseAnimation;
		this.targetQueue[newTargetIndex].isMidPoint = false;
		this.targetQueue[newTargetIndex].isInstanced = isInstanced;
	}

	public void movementTick() {
		if (entityType == EntityType.StationaryCitizen) {
			return;
		}
		if (currentTarget != null) {
			if (currentTarget.worldDestinationPosition == null) {
				stopMoving();
				return;
			}
			LocalPoint targetPosition = currentTarget.localDestinationPosition;

			LocalPoint localLoc = getLocalLocation();
			if (localLoc == null) {
				throw new RuntimeException("Tried to movement tick for citizen with no local location: " + debugName());
			}

			if (targetPosition == null) {
				Util.log(debugName() + " is cancelling movement due to targetPosition being null.");
				stopMoving();
				return;
			}

			double intx = localLoc.getX() - targetPosition.getX();
			double inty = localLoc.getY() - targetPosition.getY();

			boolean rotationDone = rotateObject(intx, inty);

			LocalPoint currentPosition = rlObject.getLocation();
			int dx = targetPosition.getX() - currentPosition.getX();
			int dy = targetPosition.getY() - currentPosition.getY();

			if (dx != 0 || dy != 0) {
				if (rlObject.getAnimation().getId() != movingAnimationId.getId()) {
					rlObject.setAnimation(plugin.getAnimation(movingAnimationId));
				}

				if (Math.abs(dx) > speed) {
					dx = Integer.signum(dx) * speed;
				}

				if (Math.abs(dy) > speed) {
					dy = Integer.signum(dy) * speed;
				}

				LocalPoint newLocation = new LocalPoint(currentPosition.getX() + dx, currentPosition.getY() + dy);

				setLocation(newLocation);

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
				stopMoving();
			}
		}
	}

	public class Target {
		public WorldPoint worldDestinationPosition;
		public LocalPoint localDestinationPosition;
		public int currentDistance;
	}


}
