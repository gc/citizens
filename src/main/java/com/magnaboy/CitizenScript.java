package com.magnaboy;

import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class CitizenScript {

	private final List<Runnable> actions = new ArrayList<>();
	private final ScheduledExecutorService executorService;
	private ScriptedCitizen citizen;
	private Iterator<Runnable> actionIterator;

	public CitizenScript() {
		this.executorService = Executors.newSingleThreadScheduledExecutor();
	}

	public CitizenScript setCitizen(ScriptedCitizen scriptedCitizen) {
		this.citizen = scriptedCitizen;
		this.actionIterator = actions.iterator();
		return this;
	}

	public CitizenScript setAnimation(AnimationID anim) {
		actions.add(() -> {
			citizen.rlObject.setAnimation(citizen.plugin.getAnimation(anim));
			scheduleNextAction(0);
		});
		return this;
	}

	public CitizenScript walkTo(int x, int y) {
		actions.add(() -> {
			WorldPoint wp = new WorldPoint(x, y, citizen.getPlane());
			citizen.moveTo(wp);
			scheduleNextActionWhenArrived(wp, 500);
		});
		return this;
	}

	public CitizenScript wait(int seconds) {
		actions.add(() -> {
			try {
				Thread.sleep(seconds * 1000L);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			scheduleNextAction(0);
		});
		return this;
	}

	public CitizenScript say(String message) {
		actions.add(() -> {
			citizen.say(message);
			scheduleNextAction(0);
		});
		return this;
	}

	private void scheduleNextAction(int delayInSeconds) {
		executorService.schedule(() -> {
			if (!actionIterator.hasNext()) {
				actionIterator = actions.iterator(); // Loop back to the first action
			}
			actionIterator.next().run();
		}, delayInSeconds, TimeUnit.SECONDS);
	}

	private void scheduleNextActionWhenArrived(WorldPoint wp, int pollingRateMillis) {
		AtomicReference<Future<?>> futureRef = new AtomicReference<>();
		Future<?> future = executorService.scheduleAtFixedRate(() -> {
			WorldPoint currentLocation = citizen.getWorldLocation();
			int dist = currentLocation.distanceTo(wp);
			if (dist <= 0) {
				futureRef.get().cancel(false);
				scheduleNextAction(0);
			}
		}, 0, pollingRateMillis, TimeUnit.MILLISECONDS);
		futureRef.set(future);
	}

	public void run() {
		if (!actionIterator.hasNext()) {
			actionIterator = actions.iterator(); // Restart from the beginning
		}
		actionIterator.next().run();
	}

	public void stop() {
		executorService.shutdown();
	}
}
