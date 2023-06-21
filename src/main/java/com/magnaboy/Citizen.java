package com.magnaboy;

import net.runelite.api.Animation;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

import javax.annotation.Nullable;
import java.util.Timer;
import java.util.TimerTask;

import static com.magnaboy.Util.getRandomItem;

public class Citizen<T extends Citizen<T>> extends Entity<T> {
	public String[] remarks;
	public String name;
	public String examine;
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

	public boolean spawn() {
		boolean didSpawn = super.spawn();
		if (didSpawn) {
			Util.log(name + " spawned " + distanceToPlayer() + "x tiles from player");
		}

		return didSpawn;
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
		this.remarkTimer = 80;
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
		movementTick();
	}

	public void movementTick() {
		if (remarkTimer > 0) {
			remarkTimer--;
		}
		if (remarkTimer == 0) {
			this.activeRemark = null;
		}

		if (currentTarget != null) {
			if (currentTarget.worldDestinationPosition == null) {
				return;
			}
			LocalPoint targetPosition = currentTarget.localDestinationPosition;

			double intx = rlObject
				.getLocation()
				.getX() - targetPosition.getX();
			double inty = rlObject
				.getLocation()
				.getY() - targetPosition.getY();

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
				currentTarget = null;
				rlObject.setAnimation(plugin.getAnimation(this.idleAnimationId));
			}
		}
	}

	public class Target {
		public WorldPoint worldDestinationPosition;
		public LocalPoint localDestinationPosition;
		public int currentDistance;
	}


}
