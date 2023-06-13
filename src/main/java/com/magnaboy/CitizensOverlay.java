package com.magnaboy;

import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.*;

public class CitizensOverlay extends Overlay {
    private final CitizensPlugin plugin;

    @Inject
    public CitizensOverlay(CitizensPlugin plugin) {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        this.plugin = plugin;
    }

    private void renderText(Graphics2D graphics, LocalPoint lp, String text) {
        Font overheadFont = FontManager.getRunescapeSmallFont();
        graphics.setFont(overheadFont);

        Point p = Perspective.localToCanvas(plugin.client, lp, plugin
                .client
                .getPlane(), 0);
        if (p == null)
            return;
        FontMetrics metrics = graphics.getFontMetrics(overheadFont);
        Point shiftedP = new Point(p.getX() - (metrics.stringWidth(text) / 2), p.getY());
        OverlayUtil.renderTextLocation(graphics, shiftedP, text, new Color(0, 255, 3));
    }

    private void highlightTile(Graphics2D graphics, LocalPoint lp, Color color) {
        if (lp == null) {
            return;
        }
        final Polygon poly = Perspective.getCanvasTilePoly(plugin.client, lp);
        if (poly != null) {
            OverlayUtil.renderPolygon(graphics, poly, color);
        }
    }

    private void highlightTile(Graphics2D graphics, WorldPoint wp, Color color) {
        LocalPoint lp = LocalPoint.fromWorld(plugin.client, wp);
        if (lp == null) {
            return;
        }
        final Polygon poly = Perspective.getCanvasTilePoly(plugin.client, lp);
        if (poly != null) {
            OverlayUtil.renderPolygon(graphics, poly, color);
        }
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        for (Citizen citizen : plugin.citizens) {
            LocalPoint localLocation = citizen.getLocalLocation();

            if (!citizen.isActive() || !citizen.shouldRender() || localLocation == null) {
                continue;
            }

            // Render generic marker for debugging
            highlightTile(graphics, localLocation, new Color(0, 255, 0, 255 / 2));
            highlightTile(graphics, citizen.getWorldLocation(), new Color(0, 255, 0, 255 / 2));
            String debugText =
                    citizen.name + " D:" + citizen.distanceToPlayer() + " MH:" + citizen.rlObject.getModelHeight();
            renderText(graphics, localLocation, debugText);

            // Render remarks
            if (citizen.activeRemark != null) {
                Point p = Perspective.localToCanvas(plugin.client, citizen.getLocalLocation(), plugin
                        .client
                        .getPlane(), citizen
                        .rlObject.getModelHeight());
                if (p != null) {
                    Font overheadFont = FontManager.getRunescapeBoldFont();
                    graphics.setFont(overheadFont);
                    FontMetrics metrics = graphics.getFontMetrics(overheadFont);
                    Point shiftedP = new Point(p.getX() - (metrics.stringWidth(citizen.activeRemark) / 2), p.getY());
                    OverlayUtil.renderTextLocation(graphics, shiftedP, citizen.activeRemark,
                            JagexColors.YELLOW_INTERFACE_TEXT);
                }
            }

            // For wandering citizens, highlight their wandering area.
            if (citizen instanceof WanderingCitizen) {
                WorldArea boundingBox = ((WanderingCitizen) citizen).boundingBox;
                int x = boundingBox.getX();
                int y = boundingBox.getY();
                Color color = new Color(0, 0, 255, 20);
                for (int i = y; i < y + boundingBox.getHeight(); i++) {
                    for (int t = 0; t < boundingBox.getWidth(); t++) {
                        highlightTile(graphics, new WorldPoint(x + t, i, 0), color);
                    }
                }
            }

            // If the citizen has a walking target, mark it.
            if (citizen.currentTarget != null) {
                Citizen.Target target = ((Citizen<WanderingCitizen>.Target) citizen.currentTarget);
                if (target.localDestinationPosition != null) {
                    highlightTile(graphics, target.localDestinationPosition, new Color(235, 150, 52));
                }
            }
        }

        return null;
    }
}
