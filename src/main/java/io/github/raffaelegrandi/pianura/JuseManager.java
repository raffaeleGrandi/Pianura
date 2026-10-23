package io.github.raffaelegrandi.pianura;

import java.util.Locale;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import io.github.raffaelegrandi.pianura.gui.JuseMF;
import io.github.raffaelegrandi.pianura.sim.SimClock;
import io.github.raffaelegrandi.pianura.sim.SimEngine;
import io.github.raffaelegrandi.pianura.structures.ScenarioManager;
import io.github.raffaelegrandi.pianura.units.RobotDummy;
import io.github.raffaelegrandi.pianura.units.UnitObject;
import io.github.raffaelegrandi.pianura.units.collisions.CollisionDetector;
import io.github.raffaelegrandi.pianura.utils.Const;

/**
 * Punto d'ingresso: costruisce motore di simulazione e finestra per uno scenario.
 *
 * <pre>
 *   JuseManager sim = new JuseManager(new Point(30, 30), "log", new MyScenario());
 *   sim.startSim(2);          // parte dopo 2 s
 *   // oppure: sim.startSimPaused();  e si avvia dalla barra (Run / Spazio)
 * </pre>
 */
public class JuseManager {

	private final ScenarioManager scenarioMngRef;
	private final SimClock clock;
	private final SimEngine engine;
	private final JuseMF mainFrame;
	private boolean started = false;
	private final List<io.github.raffaelegrandi.pianura.remote.RemoteServer> remoteServers = new ArrayList<>();

	public JuseManager(Point frameLoc, String logInitStr, ScenarioManager scenarioMng){
		scenarioMngRef = scenarioMng;

		clock = new SimClock(Const.physicsStep);
		CollisionDetector collDetect = new CollisionDetector(scenarioMngRef.getObstacles(),
			scenarioMngRef.getArenaWidthPx(), scenarioMngRef.getArenaHeightPx());
		for (RobotDummy r : scenarioMngRef.getRobots()) r.attachClock(clock);
		engine = new SimEngine(clock, collDetect, scenarioMngRef.getRobots());
		engine.setFloor(scenarioMngRef.getFloor());   // letto dai sensori ottici e mostrato dalla vista
		engine.setCommSettings(scenarioMngRef.getCommSettings());
		scenarioMngRef.setUnitsManagerSet(true);

		JFrame.setDefaultLookAndFeelDecorated(true);
		mainFrame = new JuseMF(Const.guiFrameTitle, frameLoc, logInitStr, scenarioMngRef, engine);
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		engine.setLogger(msg -> printLogMsg(false, msg));
		if (mainFrame.simPanel.getControlBar() != null) mainFrame.simPanel.getControlBar().setStopAction(this::stopSim);

		CollisionDetector.Validation validation = engine.validate();
		mainFrame.simPanel.getArenaView().setValidation(validation);   // elementi non validi in rosso
		for (String problem : validation.messages()) {
			System.err.println("SCENARIO WARNING: " + problem);
			printLogMsg(false, "WARNING: " + problem);
		}
	}

	public void setNewScenario(ScenarioManager newScenario){
		mainFrame.simPanel.configureGUI(newScenario.getRobots(), newScenario.getObstacles());
	}

	/** Avvia motore e robot in pausa: la simulazione parte con Run (o Spazio) o con resume(). */
	public void startSimPaused(){
		if (started) return;
		started = true;
		engine.start();                  // in pausa, sensori calcolati all'istante 0
		scenarioMngRef.enableRobots();   // i robot partono e si fermano alla prima sleepSim()
		mainFrame.startRefresh();
		printLogMsg(false, "Simulation ready (paused) - Run / Space to start");
	}

	/** Avvia la simulazione dopo {@code delay_sec} secondi di tempo reale. */
	public void startSim(int delay_sec){
		startSimPaused();
		if (delay_sec > 0) {
			printLogMsg(false, "Simulation starting in " + delay_sec + " s");
			try { Thread.sleep(delay_sec * 1000L); }
			catch (InterruptedException e) { Thread.currentThread().interrupt(); }
		}
		resume();
		printLogMsg(false, "Robots enabled");
	}

	/**
	 * Collega un server di controllo remoto: i suoi messaggi (connessioni, client che non
	 * rispondono, errori di protocollo) finiscono nel log di JUSE, e viene chiuso con stopSim().
	 */
	public void attach(io.github.raffaelegrandi.pianura.remote.RemoteServer server){
		remoteServers.add(server);
		server.setLogger(msg -> printLogMsg(false, msg));
		printLogMsg(false, "Remote control: UDP port " + server.getPort() + ", " + server.getMode().name().toLowerCase() + " mode");
	}

	public void stopSim(){
		engine.stop();
		for (io.github.raffaelegrandi.pianura.remote.RemoteServer s : remoteServers) s.close();
		scenarioMngRef.disableRobots();
		printLogMsg(false, "Simulation stopped at t = " + String.format(Locale.ROOT, "%.2f", clock.now()) + " s");
	}

	// ---- controlli anche da codice (stessi della barra in GUI) ----

	public void pause()                 { engine.pause(); }
	public void resume()                { engine.resume(); }
	public void step(double seconds)    { engine.stepTime(seconds); }
	/** 1.0 = tempo reale, SimEngine.MAX_SPEED = più veloce possibile. */
	public void setSpeed(double factor) { engine.setSpeed(factor); }
	public double getSimTime()          { return clock.now(); }
	public SimEngine getEngine()        { return engine; }

	/** Opzioni di visualizzazione, es. {@code getViewOptions().labelDistanceFromCenter = 0}. */
	public io.github.raffaelegrandi.pianura.gui.view.ViewOptions getViewOptions() { return mainFrame.simPanel.getViewOptions(); }

	/** Opens the live connectivity window (graph + adjacency / incidence matrix). */
	public void openConnectivityWindow() { SwingUtilities.invokeLater(mainFrame.simPanel::openConnectivityWindow); }

	/** Thread-safe: può essere chiamato dal motore o dai robot. */
	public void printLogMsg(boolean setClear, String msg){
		if (SwingUtilities.isEventDispatchThread()) mainFrame.showMSG(setClear, msg);
		else SwingUtilities.invokeLater(() -> mainFrame.showMSG(setClear, msg));
	}

}
