package com.magnaboy;

import com.magnaboy.Util.AnimData;
import com.magnaboy.scripting.ScriptAction;
import com.magnaboy.scripting.ScriptFile;
import com.magnaboy.scripting.ScriptLoader;
import net.runelite.api.coords.WorldPoint;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScriptedCitizen extends Citizen<ScriptedCitizen> {
	private ScriptFile script;
	private ExecutorService scriptExecutor;
	public ScriptAction currentAction;

	public ScriptedCitizen(CitizensPlugin plugin) {
		super(plugin);
		entityType = EntityType.ScriptedCitizen;
	}

	private void submitAction(ScriptAction action, Runnable task) {
		scriptExecutor.submit(() -> {
			long startTime = System.currentTimeMillis();
			this.currentAction = action;
			task.run();
			long endTime = System.currentTimeMillis();
		});
	}

	public ScriptedCitizen setScript(String scriptName) {
		if (scriptName == null || scriptName.isEmpty()) {
			return this;
		}
		this.script = ScriptLoader.loadScript(scriptName);
		buildRoutine();
		return this;
	}

	@Override
	public boolean despawn() {
		scriptExecutor.shutdownNow();
		return super.despawn();
	}

	private void refreshExecutor() {
		if (!isActive()) return;
		if (scriptExecutor == null || scriptExecutor.isShutdown()) {
			buildRoutine();
		}
	}

	public boolean spawn() {
		boolean didSpawn = super.spawn();
		if (didSpawn) {
			refreshExecutor();
		}
		return didSpawn;
	}

	public void update() {
		refreshExecutor();
		super.update();
	}

	private void buildRoutine() {
		if (script == null) {
			return;
		}
		scriptExecutor = Executors.newSingleThreadExecutor();

		for (ScriptAction action : script.actions) {
			addAction(action);
		}
		scriptExecutor.submit(this::buildRoutine);
	}

	private void addAction(ScriptAction action) {
		if (action != null) {
			switch (action.action) {
				case Idle:
					submitAction(action, () -> {
						setWait(action.secondsTilNextAction);
					});
					break;
				case Say:
					addSayAction(action);
					break;
				case WalkTo:
					addWalkAction(action);
					break;
				case Animation:
					addAnimationAction(action);
					break;
				case FaceDirection:
					addRotateAction(action);
					break;
			}
		}
	}

	private void addSayAction(ScriptAction action) {
		submitAction(action, () -> {
			say(action.message);
			setWait(action.secondsTilNextAction);
		});
	}

	private void addWalkAction(ScriptAction action) {
		submitAction(action, () -> {
			int tilesToWalk = action.targetPosition.distanceTo2D(getWorldLocation()) + 1;
			sleep(tilesToWalk * 100);
			plugin.clientThread.invokeLater(() -> {
				moveTo(action.targetPosition, action.targetRotation == null ? null : action.targetRotation.getAngle(),
					false, false);
			});
			while (!getWorldLocation().equals(action.targetPosition) ||
				getAnimationID() != idleAnimationId.getId() ||
				WorldPoint.fromLocal(plugin.client, getLocalLocation()).distanceTo2D(getWorldLocation()) > 0) {
				sleep();
			}

			setWait(action.secondsTilNextAction);
		});
	}

	private void addRotateAction(ScriptAction action) {
		submitAction(action, () -> {
			rlObject.setOrientation(action.targetRotation.getAngle());
			sleep(50);
			while (rlObject.getOrientation() != action.targetRotation.getAngle()) {
				sleep();
				rlObject.setOrientation(action.targetRotation.getAngle());
			}
			setWait(action.secondsTilNextAction);
		});
	}

	private void sleep() {
		sleep(30);
	}

	private void sleep(int millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			// Ignored, because this happens if the citizen despawns.
		}
	}

	private void addAnimationAction(ScriptAction action) {
		submitAction(action, () -> {
			AnimData animData = Util.getAnimData(action.animationId.getId());
			int loopCount = action.timesToLoop == null ? 1 : action.timesToLoop;
			for (int i = 0; i < loopCount; i++) {
				setAnimation(action.animationId.getId());
				try {
					Thread.sleep(animData.realDurationMillis);
				} catch (InterruptedException e) {
					// Ignored, because this happens if the citizen despawns.
				}
			}
			setAnimation(idleAnimationId.getId());
			setWait(action.secondsTilNextAction);
		});
	}

	private void setWait(Float seconds) {
		if (seconds == null) {
			return;
		}
		// We never want thread.sleep(0)
		seconds = Math.max(0.1f, seconds);
		try {
			Thread.sleep((long) (seconds * 1000L));
		} catch (InterruptedException e) {
			// Ignored, because this happens if the citizen despawns.
		}
	}
}
