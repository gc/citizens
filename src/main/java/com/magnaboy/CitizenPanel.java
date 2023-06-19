package com.magnaboy;

import static com.magnaboy.Util.worldPointToShortCoord;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.util.HashSet;
import java.util.UUID;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

class CitizenPanel extends PluginPanel {
	private CitizensPlugin plugin;
	private JLabel label;
	private final static String RELOAD_BUTTON_READY = "Reload All Entites";
	public static WorldPoint selectedPosition;
	private CitizensOverlay overlay;
	//Editor Panel Fields
	private HashSet<JComponent> allElements;
	private JButton reloadButton;
	private JButton saveChangesButton;
	private JButton spawnButton;
	private JLabel selectedPositionLbl;
	private JTextField entityNameField;
	private JComboBox<EntityType> entityTypeSelection;
	private JComboBox<AnimationID> animIdIdleSelect;
	private JComboBox<AnimationID> animIdMoveSelect;
	private JTextField modelIdsField;
	private JTextField recolorFindField;
	private JTextField recolorReplaceField;
	private JComboBox<CardinalDirection> orientationField;
	private JTextField examineTextField;
	private JTextField remarksField;
	private JTextField scaleFieldX;
	private JTextField scaleFieldY;
	private JTextField scaleFieldZ;
	private JTextField translateFieldX;
	private JTextField translateFieldY;
	private JTextField translateFieldZ;
	private JButton selectWanderBL;
	private JButton selectWanderTR;
	public WorldPoint wanderRegionBL;
	public WorldPoint wanderRegionTR;
	public JLabel editingTargetLabel;
	public JButton updateButton;
	public JButton deleteButton;
	public JLabel reloadWarning;
	public JCheckBox manualFieldsToggle;
	private JTextField manualAnimIdIdleSelect;
	private JTextField manualAnimIdMoveSelect;
	public static Entity selectedEntity;

	//End Editor Fields

	public void init(CitizensPlugin plugin, CitizensOverlay overlay) {
		this.plugin = plugin;
		this.overlay = overlay;
		final JPanel layoutPanel = new JPanel();
		layoutPanel.setLayout(new GridBagLayout());
		add(layoutPanel, BorderLayout.CENTER);

		label = new JLabel();
		label.setHorizontalAlignment(SwingConstants.CENTER);

		// DEV ONLY
		allElements = new HashSet<>();
		if (plugin.IS_DEVELOPMENT) {
			addEditorComponents(layoutPanel);
			entityTypeChanged();
		}
		update();
	}

	public void update() {
		int activeEntities = plugin.countActiveEntities();
		int inactiveEntities = plugin.countInactiveEntities();
		int totalEntities = activeEntities + inactiveEntities;
		label.setText(activeEntities + "/" + totalEntities + " entities are active");

		UpdateEditorFields();
	}

	private void UpdateEditorFields() {
		GameState state = plugin.client.getGameState();

		if (state == GameState.LOGIN_SCREEN || state == GameState.LOGIN_SCREEN_AUTHENTICATOR) {
			selectedPosition = null;
		}
		int dirtySize = CitizenRegion.dirtyRegionCount();

		reloadButton.setEnabled(state == GameState.LOGGED_IN);
		selectedPositionLbl.setText(selectedPosition == null ? "N/A" : worldPointToShortCoord(selectedPosition));

		String errorMessage = validateFields();
		boolean valid = errorMessage.isEmpty();
		spawnButton.setEnabled(state == GameState.LOGGED_IN && valid);
		spawnButton.setText(spawnButton.isEnabled() ? "Spawn Entity" : "Can't Spawn: " + errorMessage);

		saveChangesButton.setEnabled(dirtySize > 0);
		saveChangesButton.setText(dirtySize > 0 ? "Save Changes" : "Nothing To Save");

		if (selectedEntity != null && !CitizenPanel.selectedEntity.isActive()) {
			selectedEntity = null;
		}

		updateButton.setVisible(selectedEntity != null);

		if (selectedEntity instanceof Citizen) {
			editingTargetLabel.setText("Editing: " + ((Citizen) selectedEntity).name);
		} else {
			editingTargetLabel.setText("Editing: Scenery Object");
		}
		editingTargetLabel.setVisible(selectedEntity != null);
		deleteButton.setVisible(selectedEntity != null);

		reloadWarning.setVisible(dirtySize > 0);

		selectWanderBL.setText(wanderRegionBL == null ? "Select BL" : Util.worldPointToShortCoord(wanderRegionBL));
		selectWanderTR.setText(wanderRegionTR == null ? "Select TR" : Util.worldPointToShortCoord(wanderRegionTR));
	}

	private String validateFields() {
		if (selectedPosition == null) {
			return "No Position Selected";
		}

		EntityType selectedType = (EntityType) entityTypeSelection.getSelectedItem();

		if (fieldEmpty(entityNameField) && selectedType != EntityType.Scenery) {
			return "Empty Name";
		}

		if (fieldEmpty(modelIdsField)) {
			return "No Model IDs";
		}

		if (csvToIntArray(modelIdsField.getText()).length == 0) {
			return "Invalid Model Ids";
		}

		if (csvToIntArray(recolorFindField.getText()).length !=
			csvToIntArray(recolorReplaceField.getText()).length) {
			return "Model Color Mismatch";
		}

		if (selectedType == EntityType.WanderingCitizen) {
			if (wanderRegionBL == null || wanderRegionTR == null) {
				return "Incomplete Wander Region";
			}
		}
		return "";
	}

	private boolean fieldEmpty(JTextField f) {
		return f.getText() == null || f.getText().trim().isEmpty();
	}

	//DEV ONLY
	private void addEditorComponents(JPanel layoutPanel) {

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.BOTH;
		gbc.insets = new Insets(0, 0, 7, 2);
		gbc.weightx = 0.5;
		gbc.gridwidth = GridBagConstraints.REMAINDER;

		//Active Entities
		{
			gbc.gridx = 0;
			gbc.gridy = 0;
			layoutPanel.add(label, gbc);
		}

		//Reload Entities
		{
			gbc.gridy++;

			gbc.gridx = 0;
			reloadButton = new JButton();
			reloadButton.setText(RELOAD_BUTTON_READY);
			reloadButton.setHorizontalAlignment(SwingConstants.CENTER);
			reloadButton.setFocusable(false);

			reloadButton.addActionListener(e ->
			{
				selectedEntity = null;
				CitizenRegion.clearCache();
				CitizensPlugin.reloadCitizens(plugin);
			});
			layoutPanel.add(reloadButton, gbc);

			gbc.gridy++;
			reloadWarning = new JLabel("Unsaved Changes Will Be Lost");
			reloadWarning.setFont(FontManager.getRunescapeSmallFont());
			reloadWarning.setBorder(new EmptyBorder(0, 0, 0, 0));
			reloadWarning.setForeground(Color.ORANGE);
			reloadWarning.setVerticalAlignment(SwingConstants.NORTH);
			reloadWarning.setHorizontalAlignment(SwingConstants.CENTER);
			reloadWarning.setVisible(false);
			layoutPanel.add(reloadWarning, gbc);

		}

		//Editing Target
		{
			gbc.gridy++;
			editingTargetLabel = new JLabel();
			editingTargetLabel.setHorizontalAlignment(SwingConstants.CENTER);
			editingTargetLabel.setForeground(Color.orange);
			editingTargetLabel.setVisible(false);
			layoutPanel.add(editingTargetLabel, gbc);
		}

		//Selected Position Label
		{
			gbc.gridy++;

			gbc.gridx = 0;
			selectedPositionLbl = createLabeledComponent(new JLabel(), "Selected Position", layoutPanel, gbc);
		}

		//Name Field
		{
			gbc.gridy++;

			gbc.gridx = 0;
			entityNameField = createLabeledComponent(new JTextField(), "Entity Name", layoutPanel, gbc);
		}

		//Examine Text
		{
			gbc.gridy++;

			gbc.gridx = 0;
			examineTextField = createLabeledComponent(new JTextField(), "Examine Text", layoutPanel, gbc);
			examineTextField.setText("A Citizen of Gielinor");
		}

		//Entity Type
		{
			gbc.gridy++;

			gbc.gridx = 0;

			entityTypeSelection = createLabeledComponent(new JComboBox<>(EntityType.values()), "Entity Type", layoutPanel, gbc);
			entityTypeSelection.setFocusable(false);
			entityTypeSelection.addActionListener(e ->
			{
				entityTypeChanged();
			});
		}

		//Cardinal Direction
		{
			gbc.gridy++;

			gbc.gridx = 0;
			orientationField = createLabeledComponent(new JComboBox<>(CardinalDirection.values()), "Base Orientation", layoutPanel, gbc);
			orientationField.setSelectedItem(CardinalDirection.South);
			orientationField.setFocusable(false);
		}

		//Animations
		{
			gbc.gridy++;
			gbc.gridwidth = 2;

			gbc.insets = new Insets(15, 0, 0, 2);
			manualFieldsToggle = new JCheckBox("Manual Animation IDs");
			manualFieldsToggle.setFont(FontManager.getRunescapeSmallFont());
			manualFieldsToggle.setHorizontalAlignment(SwingConstants.RIGHT);
			layoutPanel.add(manualFieldsToggle, gbc);

			manualFieldsToggle.addItemListener(e -> {
				if (e.getStateChange() == ItemEvent.SELECTED || e.getStateChange() == ItemEvent.DESELECTED) {
					boolean checked = manualFieldsToggle.isSelected();

					animIdIdleSelect.getParent().setVisible(!checked);
					if (entityTypeSelection.getSelectedItem() != EntityType.Scenery) {
						animIdMoveSelect.getParent().setVisible(!checked);
					}

					manualAnimIdIdleSelect.getParent().setVisible(checked);
					if (entityTypeSelection.getSelectedItem() != EntityType.Scenery) {
						manualAnimIdMoveSelect.getParent().setVisible(checked);
					}
				}
			});

			AnimationID[] animIds = AnimationID.values();

			gbc.gridy++;
			gbc.gridx = 0;
			gbc.gridwidth = 2;  //Set grid with to 1 so the next element gets placed next to this one
			gbc.insets = new Insets(0, 0, 7, 2);
			animIdIdleSelect = createLabeledComponent(new JComboBox<>(animIds), "Idle Animation", layoutPanel, gbc);
			animIdIdleSelect.setSelectedItem(AnimationID.HumanIdle);
			animIdIdleSelect.setFocusable(false);

			gbc.gridy++;
			gbc.gridwidth = 2;
			animIdMoveSelect = createLabeledComponent(new JComboBox<>(animIds), "Move Animation", layoutPanel, gbc);
			animIdMoveSelect.setSelectedItem(AnimationID.HumanWalk);
			animIdMoveSelect.setFocusable(false);

			gbc.gridy++;
			gbc.gridwidth = 2;
			manualAnimIdIdleSelect = createLabeledComponent(new JTextField(), "Idle Animation", layoutPanel, gbc);
			manualAnimIdIdleSelect.setText("Not Yet Implemented");
			manualAnimIdIdleSelect.getParent().setVisible(false);

			gbc.gridy++;
			gbc.gridwidth = 2;
			manualAnimIdMoveSelect = createLabeledComponent(new JTextField(), "Move Animation", layoutPanel, gbc);
			manualAnimIdMoveSelect.setText("Not Yet Implemented");
			manualAnimIdMoveSelect.getParent().setVisible(false);
		}

		//Models
		{
			gbc.gridy++;
			gbc.gridx = 0;
			modelIdsField = createLabeledComponent(new JTextField(), "Model Ids", layoutPanel, gbc);
			modelIdsField.setToolTipText("Integers only, separated by commas");
		}

		//Remarks
		{
			gbc.gridy++;
			gbc.gridx = 0;
			gbc.gridwidth = 2;
			remarksField = createLabeledComponent(new JTextField(), "Remarks", layoutPanel, gbc);
			remarksField.setToolTipText("Phrases, separated by commas");
		}

		//Model Recolors
		{
			gbc.gridy++;
			gbc.gridwidth = 1;
			recolorFindField = createLabeledComponent(new JTextField(), "Find Model Colors", layoutPanel, gbc);
			recolorFindField.setToolTipText("Integers only, separated by commas");

			gbc.gridx = 1;
			recolorReplaceField = createLabeledComponent(new JTextField(), "Replace Model Colors", layoutPanel, gbc);
			recolorReplaceField.setToolTipText("Integers only, separated by commas");
		}

		//Scale
		{
			gbc.gridy++;
			gbc.gridwidth = 2;
			gbc.gridx = 0;
			scaleFieldX = new JTextField();
			scaleFieldY = new JTextField();
			scaleFieldZ = new JTextField();
			createLabeledMultiComponent("Scale", layoutPanel, gbc, scaleFieldX, scaleFieldY, scaleFieldZ);
		}

		//Translation
		{
			gbc.gridy++;
			gbc.gridwidth = 2;
			gbc.gridx = 0;
			translateFieldX = new JTextField();
			translateFieldY = new JTextField();
			translateFieldZ = new JTextField();
			createLabeledMultiComponent("Translation", layoutPanel, gbc, translateFieldX, translateFieldY, translateFieldZ);
		}

		//Wander Region
		{
			gbc.gridy++;
			gbc.gridwidth = 2;
			gbc.gridx = 0;

			selectWanderBL = new JButton();
			selectWanderBL.setText("Select BL");
			selectWanderBL.setFocusable(false);
			selectWanderBL.addActionListener(e ->
			{
				wanderRegionBL = selectedPosition;
				selectWanderBL.setText(Util.worldPointToShortCoord(selectedPosition));
			});

			selectWanderTR = new JButton();
			selectWanderTR.setText("Select TR");
			selectWanderTR.setFocusable(false);
			selectWanderTR.addActionListener(e ->
			{
				wanderRegionTR = selectedPosition;
				selectWanderTR.setText(Util.worldPointToShortCoord(selectedPosition));
			});

			createLabeledMultiComponent("Wander Region", layoutPanel, gbc, selectWanderBL, selectWanderTR);
		}

		//Spawn/Save Button
		{
			gbc.gridy++;
			gbc.gridx = 0;
			spawnButton = new JButton();
			spawnButton.setText("Spawn Entity");
			spawnButton.setFocusable(false);
			spawnButton.addActionListener(e ->
			{
				if (entityTypeSelection.getSelectedItem() == EntityType.Scenery) {
					SceneryInfo info = buildSceneryInfo();
					Scenery scenery = CitizenRegion.spawnScenery(info);
					selectedEntity = scenery;
				} else {
					CitizenInfo info = buildCitizenInfo();
					Citizen citizen = CitizenRegion.spawnCitizenFromPanel(info);
					selectedEntity = citizen;
				}
				selectedPosition = null;
				update();
			});
			layoutPanel.add(spawnButton, gbc);

			gbc.gridy++;
			gbc.gridx = 0;
			updateButton = new JButton();
			updateButton.setText("Update Entity");
			updateButton.setFocusable(false);
			updateButton.addActionListener(e ->
			{
				CitizenInfo info = buildCitizenInfo();
				if (selectedEntity != null) {
					info.uuid = selectedEntity.uuid;
					CitizenRegion.saveEntity(info);
				}

				update();
			});
			layoutPanel.add(updateButton, gbc);
		}

		//Delete Button
		{
			gbc.gridy++;
			gbc.gridx = 0;
			deleteButton = new JButton();
			deleteButton.setText("Delete Entity");
			deleteButton.setFocusable(false);
			deleteButton.setVisible(false);
			deleteButton.setBackground(new Color(135, 58, 58));
			deleteButton.addActionListener(e ->
			{
				if (selectedEntity instanceof Citizen) {
					CitizenRegion.deleteEntity((Citizen) selectedEntity);
				} else {
					CitizenRegion.deleteEntity((Scenery) selectedEntity);
				}
				selectedEntity.despawn();
			});
			layoutPanel.add(deleteButton, gbc);
		}

		//Last ROW
		{
			gbc.gridy++;

			saveChangesButton = new JButton();
			saveChangesButton.setText("Save Changes");
			saveChangesButton.setFocusable(false);
			saveChangesButton.addActionListener(e ->
			{
				CitizenRegion.saveDirtyRegions();
			});

			gbc.gridx = 0;
			layoutPanel.add(saveChangesButton, gbc);
		}
	}

	private int[] csvToIntArray(String csv) {
		String[] separated = csv.split(",", -1);
		int[] validInts = new int[separated.length];
		for (int i = 0; i < validInts.length; i++) {
			try {
				validInts[i] = Integer.parseInt(separated[i].trim());
			} catch (Exception e) {
				return new int[0];
			}
		}
		return validInts;
	}

	private float parseOrDefault(Object o, float defaultResult) {
		float result = defaultResult;
		String s = "";
		if (o instanceof JTextField) {
			s = ((JTextField) o).getText();
		}
		if (o instanceof String) {
			s = (String) o;
		}
		try {
			result = Float.parseFloat(s);
		} catch (Exception ignored) {
		}

		return result;
	}

	private CitizenInfo buildCitizenInfo() {
		CitizenInfo info = new CitizenInfo();
		info.uuid = UUID.randomUUID();
		info.regionId = selectedPosition.getRegionID();
		info.name = entityNameField.getText();
		info.examineText = examineTextField.getText();
		info.worldLocation = selectedPosition;
		info.entityType = (EntityType) entityTypeSelection.getSelectedItem();
		info.idleAnimation = (AnimationID) animIdIdleSelect.getSelectedItem();
		info.moveAnimation = (AnimationID) animIdMoveSelect.getSelectedItem();
		info.modelIds = csvToIntArray(modelIdsField.getText());
		info.modelRecolorFind = csvToIntArray(recolorFindField.getText());
		info.modelRecolorReplace = csvToIntArray(recolorReplaceField.getText());
		info.baseOrientation = ((CardinalDirection) orientationField.getSelectedItem()).getAngle();
		info.remarks = remarksField.getText().split(",", -1);

		if (fieldEmpty(scaleFieldX) && fieldEmpty(scaleFieldY) && fieldEmpty(scaleFieldZ)) {
			info.scale = null;
		} else {
			info.scale = new float[]{
				parseOrDefault(scaleFieldX, 1),
				parseOrDefault(scaleFieldY, 1),
				parseOrDefault(scaleFieldZ, 1),
			};
		}
		if (fieldEmpty(translateFieldX) && fieldEmpty(translateFieldY) && fieldEmpty(translateFieldZ)) {
			info.scale = null;
		} else {
			info.translate = new float[]{
				parseOrDefault(translateFieldX, 0),
				parseOrDefault(translateFieldY, 0),
				parseOrDefault(translateFieldZ, 0),
			};
		}

		if (info.entityType == EntityType.WanderingCitizen) {
			info.wanderBoxTR = wanderRegionTR;
			info.wanderBoxBL = wanderRegionBL;
		}

		return info;
	}

	private SceneryInfo buildSceneryInfo() {
		SceneryInfo info = new SceneryInfo();
		info.uuid = UUID.randomUUID();
		info.regionId = selectedPosition.getRegionID();
		info.entityType = EntityType.Scenery;
		info.worldLocation = selectedPosition;
		info.modelIds = csvToIntArray(modelIdsField.getText());
		info.modelRecolorFind = csvToIntArray(recolorFindField.getText());
		info.modelRecolorReplace = csvToIntArray(recolorReplaceField.getText());
		info.baseOrientation = ((CardinalDirection) orientationField.getSelectedItem()).getAngle();

		if (fieldEmpty(scaleFieldX) && fieldEmpty(scaleFieldY) && fieldEmpty(scaleFieldZ)) {
			info.scale = null;
		} else {
			info.scale = new float[]{
				parseOrDefault(scaleFieldX, 1),
				parseOrDefault(scaleFieldY, 1),
				parseOrDefault(scaleFieldZ, 1),
			};
		}

		if (fieldEmpty(translateFieldX) && fieldEmpty(translateFieldY) && fieldEmpty(translateFieldZ)) {
			info.scale = null;
		} else {
			info.translate = new float[]{
				parseOrDefault(translateFieldX, 0),
				parseOrDefault(translateFieldY, 0),
				parseOrDefault(translateFieldZ, 0),
			};
		}
		return info;
	}

	private <T extends JComponent> T createLabeledComponent(JComponent component, String label, JPanel panel, GridBagConstraints constraints) {
		final JPanel container = new JPanel();
		container.setLayout(new BorderLayout());

		final JLabel uiLabel = new JLabel(label);

		uiLabel.setFont(FontManager.getRunescapeSmallFont());
		uiLabel.setBorder(new EmptyBorder(0, 0, 0, 0));
		uiLabel.setForeground(Color.WHITE);

		container.add(uiLabel, BorderLayout.NORTH);
		container.add(component, BorderLayout.CENTER);

		panel.add(container, constraints);
		allElements.add(container);
		allElements.add(component);
		return (T) component;
	}

	//Creates multiple components under a single label
	private void createLabeledMultiComponent(String label, JPanel panel, GridBagConstraints constraints, JComponent... comps) {
		final JPanel container = new JPanel();
		container.setLayout(new GridBagLayout());

		GridBagConstraints containerGbc = new GridBagConstraints();
		containerGbc.fill = GridBagConstraints.HORIZONTAL;
		containerGbc.insets = new Insets(0, 0, 0, 2);
		containerGbc.weightx = 0.5;
		containerGbc.gridwidth = comps.length;
		final JLabel uiLabel = new JLabel(label);

		uiLabel.setFont(FontManager.getRunescapeSmallFont());
		uiLabel.setBorder(new EmptyBorder(0, 0, 0, 0));
		uiLabel.setForeground(Color.WHITE);

		containerGbc.gridx = 0;
		container.add(uiLabel, containerGbc);
		containerGbc.weightx = 0.5f;
		containerGbc.gridwidth = 1;
		int i = 0;
		for (JComponent comp : comps) {
			containerGbc.gridx = i++;
			container.add(comp, containerGbc);
			allElements.add(comp);
		}
		panel.add(container, constraints);
	}

	private void entityTypeChanged() {
		for (JComponent jc : allElements) {
			jc.setVisible(true);
			jc.getParent().setVisible(true);
		}

		EntityType type = (EntityType) entityTypeSelection.getSelectedItem();

		boolean checked = manualFieldsToggle.isSelected();
		//Turn off irrelevant components
		switch (type) {
			//We get the parents because they are each in individual containers with their labels
			case StationaryCitizen:
			case ScriptedCitizen:
				selectWanderTR.getParent().setVisible(false);
				selectWanderBL.getParent().setVisible(false);

				animIdIdleSelect.getParent().setVisible(!checked);
				animIdMoveSelect.getParent().setVisible(!checked);
				manualAnimIdIdleSelect.getParent().setVisible(checked);
				manualAnimIdMoveSelect.getParent().setVisible(checked);
				break;

			case Scenery:
				entityNameField.getParent().setVisible(false);
				examineTextField.getParent().setVisible(false);
				animIdMoveSelect.getParent().setVisible(false);
				manualAnimIdMoveSelect.getParent().setVisible(false);
				animIdIdleSelect.getParent().setVisible(!checked);
				manualAnimIdIdleSelect.getParent().setVisible(checked);
				remarksField.getParent().setVisible(false);
				selectWanderTR.getParent().setVisible(false);
				selectWanderBL.getParent().setVisible(false);
				break;
		}
	}

	public void setSelectedEntity(Entity e) {
		if (selectedEntity == e) {
			selectedEntity = null;
		} else {
			selectedEntity = e;
		}

		entityTypeSelection.setSelectedItem(e.entityType);
		orientationField.setSelectedItem(CardinalDirection.fromInteger(e.baseOrientation));
		animIdIdleSelect.setSelectedItem(e.idleAnimationId);
		modelIdsField.setText(e.getModelIDsString());
		recolorFindField.setText(e.getRecolorFindString());
		recolorReplaceField.setText(e.getRecolorReplaceString());

		if (e instanceof Citizen) {
			Citizen c = (Citizen) e;
			entityNameField.setText(c.name);
			examineTextField.setText(c.examine);
			animIdMoveSelect.setSelectedItem(c.movingAnimationId);
			remarksField.setText(String.join(",", c.remarks));
		}

		if (e instanceof WanderingCitizen) {
			WanderingCitizen w = (WanderingCitizen) e;
			wanderRegionBL = w.wanderRegionBL;
			wanderRegionTR = w.wanderRegionTR;
		}
	}

	public void cleanup() {
		selectedPosition = null;
	}
}
