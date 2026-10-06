package jusePack.comm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import jusePack.remote.MiniJson;
import jusePack.sim.SimClock;
import jusePack.units.RobotUnit;
import jusePack.units.collisions.CollisionDetector;
import jusePack.utils.Const;
import jusePack.utils.Position;

/**
 * Radio network between robots, run by the engine once per physics step.
 *
 * Robots call {@code broadcast}/{@code send} while they think; the messages wait in an outbox.
 * At the end of the step the network recomputes who can reach whom ({@link ConnectivityGraph}),
 * routes the messages one hop (to every robot in range for a broadcast, to the receiver only
 * if it is in range for a unicast), applies losses and latency, and puts the due messages in
 * the receivers' inboxes before the robots wake up at the next instant.
 *
 * Determinism: messages are processed in the order (sending time, sender id, sender sequence),
 * whatever the order of the robot threads; losses use a seeded generator.
 * Disabled robots (simulated fault) neither transmit nor receive.
 */
public final class CommNetwork {

    /** A delivered message, kept for a short while for the "message in flight" animation. */
    public record Delivery(int from, int to, double sentAt, double deliveredAt) {}

    /** A link that appeared or disappeared (robot ids, direction from → to). */
    public record LinkEvent(double time, int from, int to, boolean up) {}

    /** Message counters since the start of the simulation. */
    public record Stats(long sent, long delivered, long lost, long unreachable) {}

    private record Outgoing(Message msg, long senderSeq) {}
    private record Pending(double deliverAt, long order, RobotUnit receiver, Message msg) {}

    private static final int MAX_EVENTS = 100_000;
    private static final int MAX_RECENT = 2_000;

    private final SimClock clock;
    private final CollisionDetector detector;
    private final List<? extends RobotUnit> robots;
    private volatile CommSettings settings = new CommSettings();
    private Random random = new Random(1);

    private final ConcurrentLinkedQueue<Outgoing> outbox = new ConcurrentLinkedQueue<>();
    private final List<Pending> pending = new ArrayList<>();      // solo thread del motore
    private long pendingOrder = 0;
    private volatile ConnectivityGraph graph = ConnectivityGraph.empty();
    private final Deque<LinkEvent> events = new ArrayDeque<>();
    private final Deque<Delivery> recent = new ArrayDeque<>();
    private long sent, delivered, lost, unreachable;

    public CommNetwork(SimClock clock, CollisionDetector detector, List<? extends RobotUnit> robots) {
        this.clock = clock;
        this.detector = detector;
        this.robots = robots;
    }

    public void setSettings(CommSettings s) {
        settings = s != null ? s : new CommSettings();
        random = new Random(settings.getSeed());
    }

    public CommSettings getSettings() { return settings; }

    /** Transmission range of a robot in cells (its own, or the scenario default). */
    public double rangeOf(RobotUnit r) {
        double own = r.getCommRange();
        return Double.isNaN(own) ? settings.getRange() : own;
    }

    // ------------------------------------------------------------------ robot side

    /**
     * Queues a message (called from the robot thread). The payload is copied through JSON,
     * which also checks that it is JSON-like data.
     */
    public void submit(RobotUnit sender, int to, Object payload, long senderSeq) {
        Object copy;
        try {
            copy = MiniJson.parse(MiniJson.write(payload));
        } catch (MiniJson.JsonException e) {
            throw new IllegalArgumentException("message payload must be JSON-like data (String, number, boolean, "
                + "null, Map<String,?>, List<?>): " + e.getMessage());
        }
        outbox.add(new Outgoing(new Message(sender.getIDnum(), to, clock.now(), copy), senderSeq));
    }

    // ------------------------------------------------------------------ engine side

    /** Recomputes the graph at the current positions (also used at start and after scene edits). */
    public void updateGraph(double time) {
        ConnectivityGraph old = graph;
        ConnectivityGraph g = computeGraph(time);
        graph = g;
        recordEvents(old, g, time);
    }

    /**
     * One network step at {@code time} (the instant the robots are about to see):
     * graph, routing of the queued messages, delivery of the due ones.
     */
    public void step(double time) {
        updateGraph(time);
        ConnectivityGraph g = graph;
        double dt = clock.getDt();
        CommSettings s = settings;

        List<Outgoing> out = new ArrayList<>();
        for (Outgoing o; (o = outbox.poll()) != null; ) out.add(o);
        out.sort(Comparator.comparingDouble((Outgoing o) -> o.msg().sentAt())
            .thenComparingInt(o -> o.msg().from()).thenComparingLong(Outgoing::senderSeq));

        for (Outgoing o : out) {
            Message m = o.msg();
            sent++;
            int si = g.indexOf(m.from());
            if (si < 0) { unreachable++; continue; }                  // mittente fermo / guasto
            double deliverAt = m.sentAt() + Math.max(s.getLatency(), dt);
            for (int j = 0; j < g.size(); j++) {
                if (!g.reaches(si, j)) continue;
                if (!m.isBroadcast() && g.id(j) != m.to()) continue;
                if (s.getLossProbability() > 0 && random.nextDouble() < s.getLossProbability()) { lost++; continue; }
                pending.add(new Pending(deliverAt, pendingOrder++, robotById(g.id(j)), m));
            }
            if (!m.isBroadcast() && (g.indexOf(m.to()) < 0 || !g.reaches(si, g.indexOf(m.to())))) unreachable++;
        }

        // consegna dei messaggi arrivati entro l'istante corrente
        pending.sort(Comparator.comparingDouble(Pending::deliverAt).thenComparingLong(Pending::order));
        while (!pending.isEmpty() && pending.get(0).deliverAt() <= time + 1e-9) {
            Pending p = pending.remove(0);
            if (p.receiver() != null && p.receiver().isActive()) {
                p.receiver().deliverMessage(p.msg());
                delivered++;
                synchronized (recent) {
                    recent.addLast(new Delivery(p.msg().from(), p.receiver().getIDnum(), p.msg().sentAt(), time));
                    if (recent.size() > MAX_RECENT) recent.removeFirst();
                }
            }
        }
    }

    private RobotUnit robotById(int id) {
        for (RobotUnit r : robots) if (r.getIDnum() == id) return r;
        return null;
    }

    private ConnectivityGraph computeGraph(double time) {
        List<RobotUnit> act = new ArrayList<>();
        for (RobotUnit r : robots) if (r.isActive()) act.add(r);
        int n = act.size();
        int[] ids = new int[n];
        double[] x = new double[n], y = new double[n], range = new double[n];
        for (int i = 0; i < n; i++) {
            RobotUnit r = act.get(i);
            Position p = r.getPosition();
            ids[i] = r.getIDnum();
            x[i] = p.x * Const.cellSize;
            y[i] = p.y * Const.cellSize;
            range[i] = rangeOf(r) * Const.cellSize;
        }
        boolean los = settings.isLineOfSight();
        boolean[][] reach = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double d = Math.hypot(x[i] - x[j], y[i] - y[j]);
                boolean ij = d <= range[i], ji = d <= range[j];
                if ((ij || ji) && los && !detector.lineOfSight(x[i], y[i], x[j], y[j])) { ij = false; ji = false; }
                reach[i][j] = ij;
                reach[j][i] = ji;
            }
        }
        return new ConnectivityGraph(time, ids, reach);
    }

    private void recordEvents(ConnectivityGraph a, ConnectivityGraph b, double time) {
        synchronized (events) {
            for (int i = 0; i < b.size(); i++) {
                for (int j = 0; j < b.size(); j++) {
                    if (i == j) continue;
                    int ai = a.indexOf(b.id(i)), aj = a.indexOf(b.id(j));
                    boolean before = ai >= 0 && aj >= 0 && a.reaches(ai, aj);
                    boolean now = b.reaches(i, j);
                    if (before != now) add(new LinkEvent(time, b.id(i), b.id(j), now));
                }
            }
            // collegamenti spariti perché un robot non è più attivo
            for (int i = 0; i < a.size(); i++) {
                for (int j = 0; j < a.size(); j++) {
                    if (i == j || !a.reaches(i, j)) continue;
                    if (b.indexOf(a.id(i)) < 0 || b.indexOf(a.id(j)) < 0) add(new LinkEvent(time, a.id(i), a.id(j), false));
                }
            }
        }
    }

    private void add(LinkEvent e) {
        events.addLast(e);
        if (events.size() > MAX_EVENTS) events.removeFirst();
    }

    // ------------------------------------------------------------------ queries (any thread)

    /** Graph at the last step. */
    public ConnectivityGraph getGraph() { return graph; }

    /** Link changes since the start (most recent last, capped). */
    public List<LinkEvent> getLinkEvents() {
        synchronized (events) { return List.copyOf(events); }
    }

    /** The {@code max} most recent link changes (most recent last). */
    public List<LinkEvent> getLatestLinkEvents(int max) {
        synchronized (events) {
            List<LinkEvent> out = new ArrayList<>(Math.min(max, events.size()));
            java.util.Iterator<LinkEvent> it = events.descendingIterator();
            while (it.hasNext() && out.size() < max) out.add(0, it.next());
            return out;
        }
    }

    /** Deliveries with {@code deliveredAt >= since}. */
    public List<Delivery> getRecentDeliveries(double since) {
        List<Delivery> out = new ArrayList<>();
        synchronized (recent) {
            for (Delivery d : recent) if (d.deliveredAt() >= since) out.add(d);
        }
        return out;
    }

    public Stats getStats() { return new Stats(sent, delivered, lost, unreachable); }

}
