package io.github.raffaelegrandi.pianura.gui;

import io.github.raffaelegrandi.pianura.units.sensors.FloorMap;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.*;

import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.gui.floor.FloorStyle;
import io.github.raffaelegrandi.pianura.gui.panels.ArenaFloorPanel;
import io.github.raffaelegrandi.pianura.gui.view.ArenaView;
import io.github.raffaelegrandi.pianura.gui.view.ViewOptions;
import io.github.raffaelegrandi.pianura.gui.view.ViewSidePanel;
import io.github.raffaelegrandi.pianura.sim.SimEngine;
import io.github.raffaelegrandi.pianura.structures.ScenarioManager;
import io.github.raffaelegrandi.pianura.units.RobotDummy;
import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.units.collisions.CollisionDetector;
import io.github.raffaelegrandi.pianura.utils.LogArea;

/**
 * Pannello principale: barra dei controlli (in alto), vista dell'arena (al centro),
 * pannello laterale di visualizzazione (a destra, richiudibile), log (in basso).
 */
@SuppressWarnings("serial")
public class SimPanel extends JPanel implements ActionListener{

	protected LogArea logArea;
	private final ArenaView arena;
	private final ViewSidePanel sidePanel;
	private final ViewOptions options = new ViewOptions();
	private SimControlBar controlBar;
	protected Timer repainter;
	private int refreshCounter = 0;
	private final int arenaWpx, arenaHpx;
	private final SimEngine engine;
	private List<RobotDummy> robots;
	private io.github.raffaelegrandi.pianura.gui.view.ConnectivityWindow connectivity;

	public SimPanel(String logAreaInit, ScenarioManager scenario, SimEngine engine) {
		arenaWpx = (int) Math.round(scenario.getArenaWidthPx());
		arenaHpx = (int) Math.round(scenario.getArenaHeightPx());
		this.engine = engine;

		logArea = new LogArea(logAreaInit, 4, 5);
		repainter = new Timer(Const.repaintTime, this);
		setLayout(new BorderLayout());

		// con il motore la vista mostra lo stesso pavimento letto dai sensori ottici
		FloorMap floorMap = engine != null ? engine.getFloorMap() : newFloorMap(scenario.getFloor());
		arena = new ArenaView(floorMap, arenaWpx, arenaHpx, scenario.getObstacles(), scenario.getRobots(), options);
		add(arena, BorderLayout.CENTER);
		if (engine != null) {
			arena.setSceneEditor(new ArenaView.SceneEditor() {
				@Override public boolean canEdit() { return engine.isEditable(); }
				@Override public CollisionDetector.Validation changed(boolean finished) {
					engine.sceneChanged();
					CollisionDetector.Validation v = engine.validate();
					if (finished) reportValidation(v);
					return v;
				}
			});
		}

		if (engine != null) arena.setNetwork(engine.getNetwork(), engine.getClock());
		sidePanel = new ViewSidePanel(arena, options, scenario.getFloor(), this::changeFloor,
			ViewSidePanel.arenaInfo(scenario.getArenaWidth(), scenario.getArenaHeight(),
				scenario.getNumObstacles(), scenario.getNumRobots()),
			engine != null ? engine.getNetwork() : null, this::openConnectivityWindow);
		this.robots = scenario.getRobots();
		JScrollPane sideScroll = new JScrollPane(sidePanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		sideScroll.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Color.LIGHT_GRAY));
		add(sideScroll, BorderLayout.EAST);
		arena.setSelectionListener(r -> sidePanel.refresh());
		arena.setViewListener(sidePanel::refresh);

		if (engine != null) {
			controlBar = new SimControlBar(engine);
			controlBar.installKeyBindings(this);
			JToggleButton panelToggle = new JToggleButton("Panel", true);
			panelToggle.setFocusable(false);
			panelToggle.setToolTipText("Show/hide the view panel");
			panelToggle.addActionListener(e -> { sideScroll.setVisible(panelToggle.isSelected()); revalidate(); });
			controlBar.addSeparator();
			controlBar.add(panelToggle);
			add(controlBar, BorderLayout.NORTH);
		}
		JScrollPane logScroll = new JScrollPane(logArea, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		add(logScroll, BorderLayout.SOUTH);
	}

	/** Cambia il pavimento: vista e sensori ottici. */
	private void changeFloor(FloorStyle style){
		if (engine != null) {
			engine.setFloor(style);   // cambia anche ciò che leggono i sensori ottici
			arena.setFloor(engine.getFloorMap());
		} else {
			FloorMap old = arena.getFloor();
			arena.setFloor(newFloorMap(style));
			if (old != null) old.dispose();
		}
	}

	private FloorMap newFloorMap(FloorStyle style){
		return new FloorMap(style, arenaWpx, arenaHpx, FloorMap.defaultScale(arenaWpx, arenaHpx));
	}

	/** Esito della validazione dopo una modifica della scena, nel log. */
	private void reportValidation(CollisionDetector.Validation v){
		if (v.isOk()) {
			showMSG(false, "Scenario valid: no overlaps");
		} else {
			showMSG(false, "Scenario: " + v.messages().size() + (v.messages().size() == 1 ? " warning" : " warnings"));
			for (String m : v.messages()) showMSG(false, "  - " + m);
		}
	}

	/** Sostituisce robot e ostacoli visualizzati (pavimento predefinito). */
	public void configureGUI(List<RobotDummy> dummyVec, List<? extends Obstacle> obstVec){
		if (obstVec == null || dummyVec == null) {
			showMSG(true, "Unable to initialize Arena: robots or obstacles list is null");
			return;
		}
		// nota: gli ostacoli della vista restano quelli dello scenario iniziale
		changeFloor(FloorStyle.checkerboard());
		arena.setRobots(dummyVec);
	}

	/** Da chiamare sull'EDT (Simulator.printLogMsg se ne occupa). */
	public void showMSG(boolean setClear, String msg){
		if (setClear)logArea.setClear(true);
		logArea.addMSG(msg);
	}

	public ArenaView getArenaView(){ return arena; }

	/** Opens (or brings to front) the live connectivity window. */
	public void openConnectivityWindow(){
		if (engine == null) return;
		if (connectivity == null || !connectivity.isDisplayable()) {
			connectivity = new io.github.raffaelegrandi.pianura.gui.view.ConnectivityWindow(engine.getNetwork(), engine.getClock(), robots, arenaWpx, arenaHpx);
			java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
			if (owner != null) connectivity.setLocation(owner.getX() + owner.getWidth() - 60, owner.getY() + 40);
		}
		connectivity.setVisible(true);
		connectivity.toFront();
	}

	/** Opzioni di visualizzazione (livelli, distanza delle etichette, ...). */
	public ViewOptions getViewOptions(){ return options; }

	/** Barra dei controlli (null se il pannello è stato creato senza motore). */
	public SimControlBar getControlBar(){ return controlBar; }

	public void startRefresh(){repainter.start();}

	@Override
	public void actionPerformed(ActionEvent arg0) {
		arena.repaint();
		// etichette e pannello non servono a 60 Hz
		if (++refreshCounter % 6 == 0) {
			if (controlBar != null) controlBar.refresh();
			sidePanel.refresh();
		}
	}

}
