package com.magnaboy;

import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
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

    private void highlightTile(Graphics2D graphics, WorldPoint point, Color color) {
        final Polygon poly = Perspective.getCanvasTilePoly(plugin.getClient(), LocalPoint.fromWorld(plugin.getClient(), point));

        if (poly != null) {
            OverlayUtil.renderPolygon(graphics, poly, color);
        }
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        for (Citizen citizen : plugin.citizens) {
            if (citizen.isActive() && citizen.isRemarking()) {
                if (citizen.location != null) {
                    Point p = Perspective.localToCanvas(plugin.getClient(), citizen.getLocalLocation(), plugin
                            .getClient()
                            .getPlane(), citizen
                            .getRlObject().getModelHeight());
                    if (p != null) {
                        Font overheadFont = FontManager.getRunescapeBoldFont();
                        FontMetrics metrics = graphics.getFontMetrics(overheadFont);
                        Point shiftedP = new Point(p.getX() - (metrics.stringWidth(citizen.activeRemark) / 2), p.getY());

                        graphics.setFont(overheadFont);
                        OverlayUtil.renderTextLocation(graphics, shiftedP, citizen.activeRemark,
                                JagexColors.YELLOW_INTERFACE_TEXT);
                    }
                }
            }

            //            if (citizen instanceof WanderingCitizen) {
            //                WorldArea boundingBox = ((WanderingCitizen) citizen).boundingBox;
            //                int x = boundingBox.getX();
            //                int y = boundingBox.getY();
            //
            //                Color color = new Color(255, 0, 0);
            //
            //                for (int i = y; i < y + boundingBox.getHeight(); i++) {
            //                    for (int t = 0; t < boundingBox.getWidth(); t++) {
            //                        highlightTile(graphics, new WorldPoint(x + t, i, 0), color);
            //                    }
            //                }
            //
            //                highlightTile(graphics, citizen.location, new Color(0, 255, 0));
            //            }
        }

        return null;
    }
}
