package jusePack.sim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Tempo simulato a passo fisso e sincronizzazione dei thread dei robot.
 *
 * Il tempo avanza solo quando il motore chiama {@link #advance()}, a passi di
 * {@code dt} secondi. I thread dei robot non usano Thread.sleep ma
 * {@link #sleep(Object, double)}: si bloccano finché il tempo simulato non ha
 * raggiunto l'istante richiesto.
 *
 * Barriera (modalità lockstep): prima di ogni passo il motore attende con
 * {@link #awaitParticipants(long)} che tutti i robot registrati siano bloccati
 * in sleep. In questo modo:
 *   - pausa, passo singolo e accelerazione funzionano senza toccare il codice dei robot;
 *   - una simulazione eseguita due volte con gli stessi dati dà lo stesso risultato,
 *     a qualsiasi velocità;
 *   - un controllore lento (es. remoto via rete) rallenta la simulazione invece di
 *     perdere comandi.
 *
 * Robot non cooperativi: se un robot non chiama sleep entro il timeout (ad es. usa
 * Thread.sleep o un ciclo senza attese) viene segnalato e marcato UNSYNCED: il motore
 * smette di aspettarlo finché non torna a chiamare sleep.
 *
 * Si usano ReentrantLock/Condition e non synchronized/wait: su Java 21 un virtual
 * thread bloccato dentro synchronized occupa il carrier thread, e con molti robot
 * si rischierebbe di esaurirli.
 */
public final class SimClock {

    private enum State { RUNNING, WAITING, UNSYNCED }

    private static final class Participant {
        final String name;
        State state = State.RUNNING;
        long wakeStep;
        Participant(String name) { this.name = name; }
    }

    private final double dt;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final Map<Object, Participant> participants = new LinkedHashMap<>();
    private long step = 0;
    private boolean stopped = false;

    public SimClock(double dtSeconds) {
        if (dtSeconds <= 0) throw new IllegalArgumentException("dt must be > 0");
        this.dt = dtSeconds;
    }

    public double getDt() { return dt; }

    /** Tempo simulato corrente in secondi (calcolato dal numero di passi: niente errore cumulato). */
    public double now() {
        lock.lock();
        try { return step * dt; } finally { lock.unlock(); }
    }

    public long getStep() {
        lock.lock();
        try { return step; } finally { lock.unlock(); }
    }

    // ------------------------------------------------------------------
    //  Lato robot
    // ------------------------------------------------------------------

    /** Registra un partecipante (da chiamare prima di avviarne il thread). */
    public void register(Object who, String name) {
        lock.lock();
        try {
            participants.put(who, new Participant(name));
            changed.signalAll();
        } finally { lock.unlock(); }
    }

    /** Rimuove un partecipante (thread terminato): il motore non lo aspetta più. */
    public void deregister(Object who) {
        lock.lock();
        try {
            participants.remove(who);
            changed.signalAll();
        } finally { lock.unlock(); }
    }

    /**
     * Blocca il chiamante per {@code seconds} di tempo simulato (almeno un passo).
     * @throws SimulationStoppedException se la simulazione viene fermata durante l'attesa
     */
    public void sleep(Object who, double seconds) {
        long steps = Math.max(1, Math.round(seconds / dt));
        lock.lock();
        try {
            if (stopped) throw new SimulationStoppedException();
            Participant p = participants.get(who);
            if (p == null) {            // non registrato: attende comunque in tempo simulato
                long target = step + steps;
                while (step < target && !stopped) changed.awaitUninterruptibly();
            } else {
                p.wakeStep = step + steps;
                p.state = State.WAITING;
                changed.signalAll();
                while (step < p.wakeStep && !stopped && participants.containsKey(who)) {
                    changed.awaitUninterruptibly();
                }
                p.state = State.RUNNING;
            }
            if (stopped) throw new SimulationStoppedException();
        } finally { lock.unlock(); }
    }

    // ------------------------------------------------------------------
    //  Lato motore
    // ------------------------------------------------------------------

    /**
     * Attende che tutti i robot sincronizzati siano bloccati in sleep su un istante futuro.
     * @return i nomi dei robot che hanno superato il timeout (marcati UNSYNCED)
     */
    public List<String> awaitParticipants(long timeoutMs) {
        List<String> late = new ArrayList<>();
        lock.lock();
        try {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            while (!stopped && anyRunning()) {
                long left = deadline - System.nanoTime();
                if (left <= 0) {
                    for (Participant p : participants.values()) {
                        if (isRunning(p)) { p.state = State.UNSYNCED; late.add(p.name); }
                    }
                    break;
                }
                try { changed.awaitNanos(left); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        } finally { lock.unlock(); }
        return late;
    }

    /** Avanza di un passo e sveglia i robot il cui istante è arrivato. */
    public void advance() {
        lock.lock();
        try {
            step++;
            changed.signalAll();
        } finally { lock.unlock(); }
    }

    /** Ferma la simulazione: tutti i robot in sleep ricevono SimulationStoppedException. */
    public void stop() {
        lock.lock();
        try {
            stopped = true;
            changed.signalAll();
        } finally { lock.unlock(); }
    }

    public boolean isStopped() {
        lock.lock();
        try { return stopped; } finally { lock.unlock(); }
    }

    private boolean anyRunning() {
        for (Participant p : participants.values()) if (isRunning(p)) return true;
        return false;
    }

    /** "In esecuzione" = sta calcolando, oppure il suo istante di risveglio è già arrivato. */
    private boolean isRunning(Participant p) {
        return p.state == State.RUNNING || (p.state == State.WAITING && p.wakeStep <= step);
    }

}
