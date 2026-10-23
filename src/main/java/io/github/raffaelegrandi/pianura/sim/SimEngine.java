package io.github.raffaelegrandi.pianura.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import io.github.raffaelegrandi.pianura.units.RobotDummy;
import io.github.raffaelegrandi.pianura.units.RobotUnit;
import io.github.raffaelegrandi.pianura.units.UnitObject;
import io.github.raffaelegrandi.pianura.units.collisions.CircleShape;
import io.github.raffaelegrandi.pianura.units.collisions.CollisionDetector;

/**
 * Motore della simulazione: un thread dedicato fa avanzare il mondo a passo fisso.
 *
 * Ogni passo:
 *   1. attende che tutti i robot abbiano finito di "pensare" (barriera di SimClock);
 *   2. muove i robot con i comandi correnti (risposta ai contatti a scivolamento);
 *   3. ricalcola i sensori nelle nuove posizioni;
 *   4. avanza il tempo e sveglia i robot il cui istante è arrivato.
 * I robot che si svegliano trovano quindi sensori già aggiornati all'istante corrente.
 *
 * Il motore non dipende dalla GUI: può girare anche senza finestra (es. nei test
 * o per esperimenti in serie a velocità massima).
 *
 * Velocità: 1.0 = tempo reale, 2.0 = doppia, ..., {@link #MAX_SPEED} = più veloce possibile.
 */
public final class SimEngine {

    public static final double MAX_SPEED = Double.POSITIVE_INFINITY;

    /** Attesa massima (ms di tempo reale) di un robot prima di segnalarlo come non sincronizzato. */
    private static final long BARRIER_TIMEOUT_MS = 2000;
    /** Oltre questo ritardo (s) sul tempo reale il pacing si riallinea invece di "recuperare". */
    private static final double MAX_LAG_S = 0.25;

    private final SimClock clock;
    private final CollisionDetector detector;
    private final List<? extends RobotDummy> robots;
    private final List<UnitObject> bodies = new ArrayList<>();

    private final ReentrantLock ctrlLock = new ReentrantLock();
    private final Condition ctrlChanged = ctrlLock.newCondition();
    private boolean paused = true;
    private long stepsToRun = 0;          // passi residui richiesti con step() mentre in pausa
    private double speed = 1.0;
    private boolean pacingReset = true;   // riallinea l'orologio reale alla prossima iterazione

    /** Un passo fisico e una modifica della scena (dall'editor) non si sovrappongono mai. */
    private final ReentrantLock stepLock = new ReentrantLock();

    private volatile boolean running = false;
    private Thread thread;
    private Consumer<String> logger = System.out::println;
    private volatile io.github.raffaelegrandi.pianura.units.sensors.FloorMap floorMap;
    private final io.github.raffaelegrandi.pianura.comm.CommNetwork network;

    public SimEngine(SimClock clock, CollisionDetector detector, List<? extends RobotDummy> robots) {
        this.clock = clock;
        this.detector = detector;
        this.robots = robots;
        for (RobotUnit r : robots) bodies.add(r.getUnitObject());
        network = new io.github.raffaelegrandi.pianura.comm.CommNetwork(clock, detector, robots);
        for (RobotUnit r : robots) r.attachNetwork(network);
        setFloor(io.github.raffaelegrandi.pianura.gui.floor.FloorStyle.checkerboard());
    }

    /**
     * Pavimento letto dai sensori ottici (e mostrato dalla vista). Si può cambiare anche
     * a simulazione avviata: i sensori ottici vengono ricalcolati subito.
     */
    public void setFloor(io.github.raffaelegrandi.pianura.gui.floor.FloorStyle style) {
        double w = detector.getArenaWidthPx(), h = detector.getArenaHeightPx();
        io.github.raffaelegrandi.pianura.units.sensors.FloorMap map = new io.github.raffaelegrandi.pianura.units.sensors.FloorMap(style, w, h,
            io.github.raffaelegrandi.pianura.units.sensors.FloorMap.defaultScale(w, h));
        io.github.raffaelegrandi.pianura.units.sensors.FloorMap old;
        stepLock.lock();
        try {
            old = floorMap;
            floorMap = map;
            if (running || thread != null) sense();
        } finally { stepLock.unlock(); }
        if (old != null) old.dispose();   // ferma il disegno in background dei riquadri vecchi
    }

    public io.github.raffaelegrandi.pianura.units.sensors.FloorMap getFloorMap() { return floorMap; }

    /** Radio network between the robots (graph, link events, message counters). */
    public io.github.raffaelegrandi.pianura.comm.CommNetwork getNetwork() { return network; }

    /** Radio channel of the scenario (range, line of sight, losses, latency). */
    public void setCommSettings(io.github.raffaelegrandi.pianura.comm.CommSettings s) {
        stepLock.lock();
        try { network.setSettings(s); network.updateGraph(clock.now()); } finally { stepLock.unlock(); }
    }

    public SimClock getClock() { return clock; }

    public void setLogger(Consumer<String> logger) { this.logger = logger; }

    // ------------------------------------------------------------------
    //  Ciclo di vita
    // ------------------------------------------------------------------

    /** Avvia il thread del motore (in pausa: usare resume() o step()). */
    public void start() {
        if (thread != null) return;
        running = true;
        sense();  // sensori validi già all'istante 0, prima che i robot li leggano
        network.updateGraph(clock.now());
        thread = Thread.ofPlatform().name("juse-engine").daemon(true).start(this::loop);
    }

    /** Ferma motore e robot (i robot in sleepSim ricevono SimulationStoppedException). */
    public void stop() {
        running = false;
        clock.stop();
        ctrlLock.lock();
        try { ctrlChanged.signalAll(); } finally { ctrlLock.unlock(); }
    }

    /** Attende la terminazione del thread del motore (utile nei test). */
    public void join() throws InterruptedException {
        if (thread != null) thread.join();
    }

    // ------------------------------------------------------------------
    //  Controlli
    // ------------------------------------------------------------------

    public void pause() {
        ctrlLock.lock();
        try { paused = true; stepsToRun = 0; ctrlChanged.signalAll(); } finally { ctrlLock.unlock(); }
    }

    public void resume() {
        ctrlLock.lock();
        try { paused = false; pacingReset = true; ctrlChanged.signalAll(); } finally { ctrlLock.unlock(); }
    }

    /** In pausa: esegue {@code n} passi e torna in pausa. */
    public void step(int n) {
        ctrlLock.lock();
        try {
            if (paused) { stepsToRun += n; ctrlChanged.signalAll(); }
        } finally { ctrlLock.unlock(); }
    }

    /** In pausa: avanza di {@code seconds} di tempo simulato. */
    public void stepTime(double seconds) {
        step((int) Math.max(1, Math.round(seconds / clock.getDt())));
    }

    public void setSpeed(double factor) {
        if (!(factor > 0)) throw new IllegalArgumentException("speed must be > 0");
        ctrlLock.lock();
        try { speed = factor; pacingReset = true; } finally { ctrlLock.unlock(); }
    }

    public double getSpeed() {
        ctrlLock.lock();
        try { return speed; } finally { ctrlLock.unlock(); }
    }

    public boolean isPaused() {
        ctrlLock.lock();
        try { return paused && stepsToRun == 0; } finally { ctrlLock.unlock(); }
    }

    public boolean isRunning() { return running; }

    /** La simulazione è stata fermata con stop(): non può più ripartire. */
    public boolean isStopped() { return clock.isStopped(); }

    /**
     * Esegue la simulazione nel thread chiamante fino all'istante {@code simTime},
     * alla massima velocità. Pensato per esperimenti senza GUI e per i test;
     * non va combinato con start().
     */
    public void runUntil(double simTime) {
        running = true;
        sense();
        network.updateGraph(clock.now());
        while (running && clock.now() < simTime - 1e-9) doStep();
        // i robot svegliati all'istante finale completano il loro ciclo prima del ritorno:
        // lo stato restituito è deterministico (nessun robot "a metà")
        clock.awaitParticipants(BARRIER_TIMEOUT_MS);
    }

    // ------------------------------------------------------------------
    //  Ciclo principale
    // ------------------------------------------------------------------

    private void loop() {
        long wallAnchor = 0;
        double simAnchor = 0;
        while (running) {
            // --- pausa / passo singolo ---
            double currentSpeed;
            boolean reanchor;
            ctrlLock.lock();
            try {
                while (running && paused && stepsToRun == 0) ctrlChanged.awaitUninterruptibly();
                if (!running) break;
                boolean single = paused;          // in pausa con passi richiesti: niente pacing
                if (single) stepsToRun--;
                currentSpeed = single ? MAX_SPEED : speed;
                reanchor = pacingReset || single;
                pacingReset = false;
            } finally { ctrlLock.unlock(); }

            if (reanchor) { wallAnchor = System.nanoTime(); simAnchor = clock.now(); }

            doStep();

            // --- pacing sul tempo reale ---
            if (currentSpeed != MAX_SPEED) {
                double simElapsed = clock.now() - simAnchor;
                long targetWall = wallAnchor + (long) (simElapsed / currentSpeed * 1e9);
                long delay = targetWall - System.nanoTime();
                if (delay > 0) {
                    sleepNanos(delay);
                } else if (-delay > MAX_LAG_S * 1e9) {
                    // troppo indietro (macchina lenta o debug): si riparte da qui invece di accelerare
                    wallAnchor = System.nanoTime();
                    simAnchor = clock.now();
                }
            }
        }
    }

    private void doStep() {
        stepLock.lock();
        try { doStepLocked(); } finally { stepLock.unlock(); }
    }

    private void doStepLocked() {
        // 1. barriera: tutti i robot hanno finito di decidere i comandi
        for (String late : clock.awaitParticipants(BARRIER_TIMEOUT_MS)) {
            logger.accept("WARNING: " + late + " did not call sleepSim() for "
                + BARRIER_TIMEOUT_MS + " ms: the simulation goes on without waiting for it "
                + "(use sleepSim() instead of Thread.sleep() or busy loops)");
        }
        double t = clock.now();

        // guasti simulati (in tempo simulato)
        for (RobotDummy r : robots) {
            if (r.isActive() && r.getFaultTime() >= 0 && t >= r.getFaultTime()) {
                r.setActive(false);
                logger.accept("Robot #" + r.getIDnum() + " disabled (simulated fault at t=" + r.getFaultTime() + " s)");
            }
        }

        // 2. movimento
        double dt = clock.getDt();
        for (RobotUnit r : robots) if (r.isActive()) r.evaluateMovement(dt);

        // 3. sensori nelle nuove posizioni
        sense();

        // 3b. rete radio: grafo, instradamento e consegna dei messaggi per l'istante che i robot vedranno
        network.step(t + dt);

        // 4. avanzamento del tempo
        clock.advance();
    }

    // ------------------------------------------------------------------
    //  Modifiche della scena (spostamento di ostacoli e robot dalla GUI)
    // ------------------------------------------------------------------

    public CollisionDetector getDetector() { return detector; }

    /** La scena si può modificare prima dell'avvio o in pausa (non dopo stop()). */
    public boolean isEditable() { return !isStopped() && (!running || isPaused()); }

    /**
     * Da chiamare dopo aver spostato o ruotato ostacoli o robot: ricostruisce le forme
     * di collisione e ricalcola i sensori, così i robot vedono subito la nuova scena.
     */
    public void sceneChanged() {
        stepLock.lock();
        try {
            detector.rebuild();
            sense();
            network.updateGraph(clock.now());
        } finally { stepLock.unlock(); }
    }

    /** Validazione della scena corrente (ostacoli e posizioni dei robot). */
    public CollisionDetector.Validation validate() {
        return detector.validate(bodies);
    }

    /** Aggiorna i sensori di tutti i robot attivi sullo stesso istante (snapshot condiviso). */
    private void sense() {
        List<CircleShape> shapes = detector.snapshotRobots(bodies);
        io.github.raffaelegrandi.pianura.units.sensors.FloorMap fm = floorMap;
        for (RobotUnit r : robots) {
            if (!r.isActive()) continue;
            detector.testUnitForCollisions(r.getUnitObject(), shapes);
            if (fm != null) senseFloor(r.getUnitObject(), fm);
        }
    }

    /** Sensori ottici: colore medio del pavimento sotto ciascun sensore. */
    private static void senseFloor(UnitObject u, io.github.raffaelegrandi.pianura.units.sensors.FloorMap fm) {
        io.github.raffaelegrandi.pianura.units.sensors.FloorSensorArray fs = u.getFloorSensors();
        io.github.raffaelegrandi.pianura.units.sensors.FloorSensorArray.Layout l = fs.getLayout();
        io.github.raffaelegrandi.pianura.utils.Position p = u.getExactPos();
        double cell = io.github.raffaelegrandi.pianura.utils.Const.cellSize, d = l.radiusFraction() * io.github.raffaelegrandi.pianura.utils.Const.unitRadius;
        int[] rgb = new int[l.count()];
        java.awt.geom.Point2D[] at = new java.awt.geom.Point2D[l.count()];
        for (int i = 0; i < l.count(); i++) {
            double a = p.theta + l.angleRad(i);
            double x = p.x * cell + d * Math.cos(a), y = p.y * cell + d * Math.sin(a);
            rgb[i] = fm.sampleRGB(x, y, l.footprintPx());
            at[i] = new java.awt.geom.Point2D.Double(x / cell, y / cell);
        }
        fs.publish(rgb, at);
    }

    private static void sleepNanos(long nanos) {
        try { Thread.sleep(nanos / 1_000_000, (int) (nanos % 1_000_000)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

}
