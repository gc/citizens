package com.magnaboy;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.geometry.SimplePolygon;
import net.runelite.api.model.Jarvis;

import java.util.ArrayList;

import static net.runelite.api.Perspective.COSINE;
import static net.runelite.api.Perspective.SINE;

public class Entity<T extends Entity<T>> {
    public final int plane = 0;
    public WorldPoint location;
    public CitizensPlugin plugin;
    public AnimationID idleAnimationId;
    protected RuneLiteObject rlObject;
    Integer baseOrientation;
    private int[] modelIDs;
    private int[] recolorsToFind;
    private int[] recolorsToReplace;
    public float[] scale;
    public float[] translate;

    public Entity(CitizensPlugin plugin) {
        this.plugin = plugin;
    }

    public void update() {
        boolean inScene = shouldRender();

        if (inScene) {
            spawn();
        } else {
            despawn();
        }
    }

    public T setScale(float scaleX, float scaleY, float scaleZ) {
        this.scale = new float[]{scaleX, scaleY, scaleZ};
        return (T) this;
    }

    public T setTranslate(float translateX, float translateY, float translateZ) {
        this.translate = new float[]{translateX, translateY, translateZ};
        return (T) this;
    }

    static int radToJau(double a) {
        int j = (int) Math.round(a / Perspective.UNIT);
        return j & 2047;
    }

    protected static SimplePolygon calculateAABB(Client client, Model m, Integer jauOrient, int x, int y, int z,
                                                 int zOff) {
        if (m == null) {
            throw new IllegalStateException("model is null");
        }
        if (jauOrient == null) {
            throw new IllegalStateException("jauOrient is null");
        }
        AABB aabb = m.getAABB(jauOrient);

        int x1 = aabb.getCenterX();
        int y1 = aabb.getCenterZ();
        int z1 = aabb.getCenterY() + zOff;

        int ex = aabb.getExtremeX();
        int ey = aabb.getExtremeZ();
        int ez = aabb.getExtremeY();

        int x2 = x1 + ex;
        int y2 = y1 + ey;
        int z2 = z1 + ez;

        x1 -= ex;
        y1 -= ey;
        z1 -= ez;

        int[] xa = new int[]{
                x1, x2, x1, x2,
                x1, x2, x1, x2
        };
        int[] ya = new int[]{
                y1, y1, y2, y2,
                y1, y1, y2, y2
        };
        int[] za = new int[]{
                z1, z1, z1, z1,
                z2, z2, z2, z2
        };

        int[] x2d = new int[8];
        int[] y2d = new int[8];

        Entity.modelToCanvasCpu(client, 8, x, y, z, 0, xa, ya, za, x2d, y2d);

        return Jarvis.convexHull(x2d, y2d);
    }

    private static void modelToCanvasCpu(Client client, int end, int x3dCenter, int y3dCenter, int z3dCenter, int rotate, int[] x3d, int[] y3d, int[] z3d, int[] x2d, int[] y2d) {
        final int
                cameraPitch = client.getCameraPitch(),
                cameraYaw = client.getCameraYaw(),

                pitchSin = SINE[cameraPitch],
                pitchCos = COSINE[cameraPitch],
                yawSin = SINE[cameraYaw],
                yawCos = COSINE[cameraYaw],
                rotateSin = SINE[rotate],
                rotateCos = COSINE[rotate],

                cx = x3dCenter - client.getCameraX(),
                cy = y3dCenter - client.getCameraY(),
                cz = z3dCenter - client.getCameraZ(),

                viewportXMiddle = client.getViewportWidth() / 2,
                viewportYMiddle = client.getViewportHeight() / 2,
                viewportXOffset = client.getViewportXOffset(),
                viewportYOffset = client.getViewportYOffset(),

                zoom3d = client.getScale();

        for (int i = 0; i < end; i++) {
            int x = x3d[i];
            int y = y3d[i];
            int z = z3d[i];

            if (rotate != 0) {
                int x0 = x;
                x = x0 * rotateCos + y * rotateSin >> 16;
                y = y * rotateCos - x0 * rotateSin >> 16;
            }

            x += cx;
            y += cy;
            z += cz;

            final int
                    x1 = x * yawCos + y * yawSin >> 16,
                    y1 = y * yawCos - x * yawSin >> 16,
                    y2 = z * pitchCos - y1 * pitchSin >> 16,
                    z1 = y1 * pitchCos + z * pitchSin >> 16;

            int viewX, viewY;

            if (z1 < 50) {
                viewX = Integer.MIN_VALUE;
                viewY = Integer.MIN_VALUE;
            } else {
                viewX = (viewportXMiddle + x1 * zoom3d / z1) + viewportXOffset;
                viewY = (viewportYMiddle + y2 * zoom3d / z1) + viewportYOffset;
            }

            x2d[i] = viewX;
            y2d[i] = viewY;
        }
    }

    public T setBaseOrientation(CardinalDirection baseOrientation) {
        this.baseOrientation = baseOrientation.getAngle();
        return (T) this;
    }

    public T setModelIDs(int[] modelIDs) {
        this.modelIDs = modelIDs;
        return (T) this;
    }

    public T setModelRecolors(int[] recolorsToFind, int[] recolorsToReplace) {
        this.recolorsToFind = recolorsToFind;
        this.recolorsToReplace = recolorsToReplace;
        return (T) this;
    }

    public T setLocation(WorldPoint location) {
        this.location = location;
        if (rlObject != null) {
            LocalPoint lp = LocalPoint.fromWorld(plugin.client, location.getX(), location.getY());
            if (lp == null) {
                throw new IllegalStateException("Received null LocalPoint in setLocation");
            }
            rlObject.setLocation(lp, plane);
        }
        return (T) this;
    }

    public boolean shouldRender() {
        int distanceFromPlayer = distanceToPlayer();
        return distanceFromPlayer < 50;
    }

    public int distanceToPlayer() {
        if (location == null) {
            throw new IllegalStateException("Tried to get distance to player with no location");
        }
        Player player = plugin.client.getLocalPlayer();
        if (player == null) {
            throw new IllegalStateException("Tried to get distance to null player");
        }
        WorldPoint worldLoc = player.getWorldLocation();
        if (worldLoc == null) {
            throw new IllegalStateException("Tried to get distance with null worldLoc");
        }
        return worldLoc.distanceTo(location);
    }

    public void despawn() {
        if (rlObject == null) {
            return;
        }
        plugin.clientThread.invokeLater(() -> {
            rlObject.setActive(false);
        });
    }

    public void spawn() {
        if (location == null) {
            throw new IllegalStateException("Tried to spawn entity with no location");
        }

        this.rlObject = plugin.client.createRuneLiteObject();

        ArrayList<ModelData> models = new ArrayList<ModelData>();
        for (int modelID : modelIDs) {
            ModelData data = plugin.client.loadModelData(modelID);
            models.add(data);
        }

        ModelData finalModel = plugin.client.mergeModels(models.toArray(new ModelData[models.size()]), models.size());
        if (recolorsToReplace != null && recolorsToReplace.length > 0) {
            for (int i = 0; i < recolorsToReplace.length; i++) {
                finalModel.recolor((short) recolorsToFind[i], (short) recolorsToReplace[i]);
            }
        }

        if (scale != null) {
            finalModel.cloneVertices();
            finalModel.scale(
                    -(Math.round(scale[0] * 128)),
                    -(Math.round(scale[1] * 128)),
                    -(Math.round(scale[2] * 128))
            );
        }

        if (translate != null) {
            finalModel.cloneVertices();
            finalModel.translate(
                    -(Math.round(translate[0] * 128)),
                    -(Math.round(translate[1] * 128)),
                    -(Math.round(translate[2] * 128))
            );
        }

        rlObject.setModel(finalModel.light(64, 850, -30, -50, -30));

        LocalPoint localPosition = LocalPoint.fromWorld(plugin.client, location);
        boolean isInSamePlaneAsPlayer = plugin.client.getPlane() == location.getPlane();
        if (localPosition != null && isInSamePlaneAsPlayer) {
            rlObject.setLocation(localPosition, location.getPlane());
        } else {
            return;
        }

        if (baseOrientation != null) {
            rlObject.setOrientation(baseOrientation);
        }

        if (this.idleAnimationId != null) {
            rlObject.setAnimation(plugin.getAnimation(this.idleAnimationId));
        }
        rlObject.setShouldLoop(true);
        rlObject.setActive(true);
    }


    public boolean isActive() {
        if (rlObject == null) {
            return false;
        }
        return rlObject.isActive();
    }

    public LocalPoint getLocalLocation() {
        return rlObject.getLocation();
    }

    public boolean rotateObject(double intx, double inty) {
        final int JAU_FULL_ROTATION = 2048;
        int targetOrientation = Entity.radToJau(Math.atan2(intx, inty));
        int currentOrientation = rlObject.getOrientation();

        int dJau = (targetOrientation - currentOrientation) % JAU_FULL_ROTATION;

        if (dJau != 0) {
            final int JAU_HALF_ROTATION = 1024;
            final int JAU_TURN_SPEED = 32;
            int dJauCW = Math.abs(dJau);

            if (dJauCW > JAU_HALF_ROTATION)// use the shortest turn
            {
                dJau = (currentOrientation - targetOrientation) % JAU_FULL_ROTATION;
            } else if (dJauCW == JAU_HALF_ROTATION)// always turn right when turning around
            {
                dJau = dJauCW;
            }


            // only use the delta if it won't send up past the target
            if (Math.abs(dJau) > JAU_TURN_SPEED) {
                dJau = Integer.signum(dJau) * JAU_TURN_SPEED;
            }


            int newOrientation = (JAU_FULL_ROTATION + rlObject.getOrientation() + dJau) % JAU_FULL_ROTATION;

            rlObject.setOrientation(newOrientation);

            dJau = (targetOrientation - newOrientation) % JAU_FULL_ROTATION;
        }

        return dJau == 0;
    }

    public T setIdleAnimation(AnimationID idleAnimationId) {
        this.idleAnimationId = idleAnimationId;
        return (T) this;
    }


}
