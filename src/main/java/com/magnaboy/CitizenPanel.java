package com.magnaboy;

import net.runelite.api.GameState;
import net.runelite.client.RuneLite;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import java.awt.*;

class CitizenPanel extends PluginPanel {
    private CitizensPlugin plugin;
    private JLabel label;
    private JButton ReloadButton;

    private final static String RELOAD_BUTTON_READY = "Reload Citizens";
    private final static String RELOAD_BUTTON_NEEDLOGIN = "Login To Use";
    public void init(CitizensPlugin plugin) {
        this.plugin = plugin;

        final JPanel layoutPanel = new JPanel();
        layoutPanel.setLayout(new GridBagLayout());
        add(layoutPanel, BorderLayout.CENTER);

        label = new JLabel();
        label.setHorizontalAlignment(SwingConstants.CENTER);


        ReloadButton = new JButton();
        ReloadButton.setText(RELOAD_BUTTON_READY);
        ReloadButton.setHorizontalAlignment(SwingConstants.CENTER);
        ReloadButton.setFocusable(false);

        ReloadButton.addActionListener(e ->
        {
            CitizensPlugin.ReloadCitizens();
        });


        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0,2,20,2);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        layoutPanel.add(label, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        layoutPanel.add(ReloadButton, gbc);

        update();
    }

    public void update() {
        int activeEntities = plugin.countActiveEntities();
        int inactiveEntities = plugin.countInactiveEntities();
        int totalEntities = activeEntities + inactiveEntities;
        label.setText(activeEntities + "/" + totalEntities + " entities are active");

        GameState state = plugin.client.getGameState();
        ReloadButton.setText(state == GameState.LOGIN_SCREEN || state == GameState.LOGIN_SCREEN_AUTHENTICATOR ? RELOAD_BUTTON_NEEDLOGIN : RELOAD_BUTTON_READY);
        ReloadButton.setEnabled(state == GameState.LOGGED_IN);
    }
}
