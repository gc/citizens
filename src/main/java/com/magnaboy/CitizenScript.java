package com.magnaboy;

import net.runelite.api.coords.WorldPoint;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CitizenScript {
    private ScriptedCitizen citizen;
    private final Queue<Runnable> actions = new LinkedList<>();
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public CitizenScript setCitizen(ScriptedCitizen _citizen) {
        this.citizen = _citizen;
        return this;
    }

    public CitizenScript walkTo(int x, int y) {
        actions.add(() -> {
            citizen.moveTo(new WorldPoint(x, y, citizen.plane));
            final ScheduledFuture<?>[] locationCheckTask = new ScheduledFuture<?>[1];
            locationCheckTask[0] = executorService.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    WorldPoint currentLocation = citizen.location;
                    if (currentLocation.getX() == x && currentLocation.getY() == y) {
                        locationCheckTask[0].cancel(false); // Stop the location checking
                        scheduleNextAction(0); // Proceed with the next action
                    }
                }
            }, 0, 500, TimeUnit.MILLISECONDS); // Check the location every 500 ms
        });
        return this;
    }


    public CitizenScript wait(int seconds) {
        actions.add(() -> scheduleNextAction(seconds));
        return this;
    }

    public CitizenScript playAnimation(String animationID) {
        actions.add(() -> {
            // implement playing animation
            System.out.println("Playing animation " + animationID);
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
            if (actions.isEmpty()) {
                // All actions have been completed. Restart from the beginning.
                actions.addAll(actions);
            }
            actions.poll().run();
        }, delayInSeconds, TimeUnit.SECONDS);
    }

    public void run() {
        if (!actions.isEmpty()) {
            actions.poll().run();
        }
    }

    public void stop() {
        executorService.shutdown();
    }
}
