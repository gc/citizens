package com.magnaboy;

import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import java.awt.*;

class CitizenPanel extends PluginPanel {
    private CitizensPlugin plugin;
    private JLabel label;

    public void init(CitizensPlugin plugin) {
        this.plugin = plugin;
        setLayout(new BorderLayout());
        label = new JLabel();
        label.setHorizontalAlignment(SwingConstants.CENTER);
        add(label, BorderLayout.CENTER);
        update();
    }

    public void update() {
        int activeEntities = plugin.countActiveEntities();
        int inactiveEntities = plugin.countInactiveEntities();
        int totalEntities = activeEntities + inactiveEntities;
        label.setText(activeEntities + "/" + totalEntities + " entities are active");
    }
}
