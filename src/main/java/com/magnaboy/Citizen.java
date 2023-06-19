package com.magnaboy;

import static com.magnaboy.Util.getRandomItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import javax.annotation.Nullable;
import net.runelite.api.Animation;
import net.runelite.api.ChatMessageType;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

public class Citizen<T extends Citizen<T>> extends Entity<T> {
	public String[] remarks;
	public String name;
	public String examine;
	@Nullable
	public String activeRemark = null;
	private int remarkTimer = 0;
	public int speed = 4;
	protected final List<ExtraObject> extraObjects = new ArrayList<>();
	public AnimationID[] randomAnimations;
	public AnimationID movingAnimationId = AnimationID.HumanWalk;

	@Nullable()
	Target currentTarget;

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

	public boolean spawn() {
		boolean didSpawn = super.spawn();
		if (didSpawn) {
			Util.log(name + " spawned " + distanceToPlayer() + "x tiles from player");
		}

		for (ExtraObject obj : extraObjects) {
			obj.spawn();
		}

		return didSpawn;
	}

	public boolean despawn() {
		this.currentTarget = null;
		this.activeRemark = null;
		this.remarkTimer = 0;
		boolean didDespawn = super.despawn();
		for (ExtraObject obj : extraObjects) {
			obj.despawn();
		}
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
		plugin.clientThread.invokeLater(() -> {
			plugin.client.addChatMessage(ChatMessageType.PUBLICCHAT, this.name, message, null);
		});
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

			// are we not where we need to be?
			if (dx != 0 || dy != 0) {
				if (rlObject.getAnimation().getId() != movingAnimationId.getId()) {
					rlObject.setAnimation(plugin.getAnimation(movingAnimationId));
				}

				// only use the delta if it won't send up past the target
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


}
