package jusePack.comm;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import jusePack.ArenaObjects.ArenaObjectType;
import jusePack.ArenaObjects.Obstacle;
import jusePack.sim.SimClock;
import jusePack.sim.SimEngine;
import jusePack.units.ControllerRobot;
import jusePack.units.RobotController;
import jusePack.units.RobotDummy;
import jusePack.units.collisions.CollisionDetector;
import jusePack.utils.Const;
import jusePack.utils.Position;

class CommNetworkTest {

    /** Ricevuto: istante di lettura, destinatario, mittente, contenuto. */
    record Got(double t, int at, int from, Object payload) {}

    private static RobotDummy robot(int id, double x, double y, RobotController c) {
        return new ControllerRobot(id, new Position(x, y, 0), 2, 0.1, c);
    }

    private static SimEngine run(List<RobotDummy> robots, List<Obstacle> obstacles, CommSettings s, double until) {
        SimClock clock = new SimClock(Const.physicsStep);
        SimEngine e = new SimEngine(clock, new CollisionDetector(obstacles), robots);
        e.setLogger(m -> {});
        e.setCommSettings(s);
        for (RobotDummy r : robots) { r.attachClock(clock); r.start(); }
        e.runUntil(until);
        e.stop();
        return e;
    }

    /** Controllore che annota ciò che riceve. */
    private static RobotController recorder(List<Got> log, RobotController then) {
        return (r, t) -> {
            for (Message m : r.receive()) log.add(new Got(t, r.getIDnum(), m.from(), m.payload()));
            if (then != null) then.control(r, t);
        };
    }

    @Test
    @Timeout(20)
    void threeInARowNeedARelayToReachTheLastOne() {
        List<Got> log = new CopyOnWriteArrayList<>();
        // A(1) — 5 celle — B(2) — 5 celle — C(3), raggio 6: A sente B, B sente C, A non sente C
        RobotDummy a = robot(1, 10, 20, recorder(log, (r, t) -> { if (t == 0) r.broadcast("hello from A"); }));
        RobotDummy b = robot(2, 15, 20, (r, t) -> {
            for (Message m : r.receive()) {
                log.add(new Got(t, 2, m.from(), m.payload()));
                if (m.from() == 1) r.broadcast(Map.of("relay", m.payload()));   // B ritrasmette
            }
        });
        RobotDummy c = robot(3, 20, 20, recorder(log, null));
        SimEngine e = run(List.of(a, b, c), List.of(), new CommSettings().setRange(6), 1.0);

        List<Got> atC = log.stream().filter(g -> g.at() == 3).toList();
        assertEquals(1, atC.size(), log.toString());
        assertEquals(2, atC.get(0).from(), "C riceve solo da B");
        assertEquals(Map.of("relay", "hello from A"), atC.get(0).payload());
        assertTrue(log.stream().anyMatch(g -> g.at() == 2 && g.from() == 1), "B riceve A");
        assertTrue(log.stream().anyMatch(g -> g.at() == 1 && g.from() == 2), "anche A sente la ritrasmissione di B");
        ConnectivityGraph g = e.getNetwork().getGraph();
        assertEquals(2, g.links().size());
        assertEquals(1, g.componentCount());
    }

    @Test
    @Timeout(20)
    void differentRangesMakeOneWayLinks() {
        List<Got> log = new CopyOnWriteArrayList<>();
        RobotDummy a = robot(1, 10, 20, recorder(log, (r, t) -> { if (t == 0) r.broadcast("A"); }));
        RobotDummy b = robot(2, 15, 20, recorder(log, (r, t) -> { if (t == 0) r.broadcast("B"); }));
        a.setCommRange(10);
        b.setCommRange(3);                          // distanza 5: A arriva a B, B non arriva ad A
        SimEngine e = run(List.of(a, b), List.of(), new CommSettings(), 0.5);
        assertTrue(log.stream().anyMatch(g -> g.at() == 2 && "A".equals(g.payload())));
        assertTrue(log.stream().noneMatch(g -> g.at() == 1), log.toString());
        ConnectivityGraph g = e.getNetwork().getGraph();
        assertTrue(g.reaches(g.indexOf(1), g.indexOf(2)));
        assertFalse(g.reaches(g.indexOf(2), g.indexOf(1)));
        // incidenza: +1 sul trasmettitore, -1 sul ricevitore
        int[][] inc = g.incidence();
        assertEquals(1, inc[g.indexOf(1)][0]);
        assertEquals(-1, inc[g.indexOf(2)][0]);
        assertArrayEquals(new int[] { 0, 1 }, g.adjacency()[g.indexOf(1)]);
    }

    @Test
    @Timeout(20)
    void unicastOnlyReachesARobotInRange() {
        List<Got> log = new CopyOnWriteArrayList<>();
        RobotDummy a = robot(1, 10, 20, (r, t) -> { if (t == 0) { r.send(2, "near"); r.send(3, "far"); } });
        RobotDummy b = robot(2, 15, 20, recorder(log, null));
        RobotDummy c = robot(3, 30, 20, recorder(log, null));
        SimEngine e = run(List.of(a, b, c), List.of(), new CommSettings().setRange(6), 0.5);
        assertEquals(1, log.size(), log.toString());
        assertEquals("near", log.get(0).payload());
        CommNetwork.Stats s = e.getNetwork().getStats();
        assertEquals(2, s.sent());
        assertEquals(1, s.delivered());
        assertEquals(1, s.unreachable());
    }

    @Test
    @Timeout(20)
    void lineOfSightIsBlockedByObstaclesWhenEnabled() {
        List<Obstacle> wall = List.of(new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.GRAY, new Position(12.5, 20, 0), 1, 6));
        for (boolean los : new boolean[] { false, true }) {
            List<Got> log = new CopyOnWriteArrayList<>();
            RobotDummy a = robot(1, 10, 20, (r, t) -> { if (t == 0) r.broadcast("x"); });
            RobotDummy b = robot(2, 15, 20, recorder(log, null));
            run(List.of(a, b), wall, new CommSettings().setRange(6).setLineOfSight(los), 0.5);
            assertEquals(los ? 0 : 1, log.size(), "linea di vista " + los);
        }
    }

    @Test
    @Timeout(20)
    void lossesAreDeterministic() {
        int[] counts = new int[3];
        double[] p = { 1.0, 0.5, 0.5 };
        for (int k = 0; k < 3; k++) {
            List<Got> log = new CopyOnWriteArrayList<>();
            RobotDummy a = robot(1, 10, 20, (r, t) -> r.broadcast(t));
            RobotDummy b = robot(2, 13, 20, recorder(log, null));
            run(List.of(a, b), List.of(), new CommSettings().setLossProbability(p[k]).setSeed(42), 5.0);
            counts[k] = log.size();
        }
        assertEquals(0, counts[0], "perdita 100%");
        assertTrue(counts[1] > 10 && counts[1] < 40, "perdita 50% su ~50 messaggi: " + counts[1]);
        assertEquals(counts[1], counts[2], "stesso seme, stesse perdite");
    }

    @Test
    @Timeout(20)
    void latencyDelaysDelivery() {
        List<Got> log = new CopyOnWriteArrayList<>();
        RobotDummy a = robot(1, 10, 20, (r, t) -> { if (t == 0) r.broadcast("late"); });
        RobotDummy b = robot(2, 13, 20, recorder(log, null));
        run(List.of(a, b), List.of(), new CommSettings().setLatency(0.35), 1.0);
        assertEquals(1, log.size());
        assertEquals(0.4, log.get(0).t(), 1e-9, "inviato a 0, arriva a 0.35: letto al ciclo di 0.4");
    }

    @Test
    @Timeout(20)
    void payloadIsCopiedAndMustBeJsonLike() {
        List<Got> log = new CopyOnWriteArrayList<>();
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        RobotDummy a = robot(1, 10, 20, (r, t) -> {
            if (t != 0) return;
            List<Object> mutable = new ArrayList<>(List.of(1, 2));
            r.broadcast(Map.of("n", 7, "list", mutable));
            mutable.add(3);                                     // non deve toccare il messaggio già inviato
            try { r.broadcast(new Object()); } catch (IllegalArgumentException ex) { errors.add(ex); }
        });
        RobotDummy b = robot(2, 13, 20, recorder(log, null));
        run(List.of(a, b), List.of(), new CommSettings(), 0.5);
        assertEquals(1, errors.size(), "un oggetto qualsiasi non è un contenuto valido");
        @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) log.get(0).payload();
        assertEquals(7.0, m.get("n"), "i numeri arrivano come Double, come in JSON");
        assertEquals(List.of(1.0, 2.0), m.get("list"));
    }

    @Test
    @Timeout(20)
    void disabledRobotsNeitherSendNorReceive() {
        List<Got> log = new CopyOnWriteArrayList<>();
        RobotDummy a = robot(1, 10, 20, (r, t) -> r.broadcast(t));
        RobotDummy b = new ControllerRobot(2, new Position(13, 20, 0), 2, 0.1, recorder(log, null), Const.unitColor, 500);
        SimEngine e = run(List.of(a, b), List.of(), new CommSettings(), 2.0);
        assertTrue(log.stream().allMatch(g -> g.t() < 0.5 + 1e-9), "dopo il guasto (0.5 s) niente più messaggi");
        assertEquals(0, e.getNetwork().getGraph().links().size());
        assertTrue(e.getNetwork().getLinkEvents().stream().anyMatch(ev -> !ev.up()), "evento di collegamento rotto");
    }

    @Test
    @Timeout(30)
    void chatterIsReproducible() {
        assertEquals(chatter(), chatter());
    }

    /** Cinque robot in movimento che si scambiano messaggi: sequenza ricevuta da tutti. */
    private static List<String> chatter() {
        List<String> got = new CopyOnWriteArrayList<>();
        List<RobotDummy> rs = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            final int id = i;
            rs.add(robot(id, 8 + 4 * i, 20, (r, t) -> {
                for (Message m : r.receive()) got.add(id + "<" + m.from() + ":" + m.payload());
                r.setRobotSpeed(0.6, Math.sin(t * id) * 0.8);
                if (Math.round(t * 10) % id == 0) r.broadcast(id * 100 + Math.round(t * 10));
            }));
        }
        run(rs, List.of(), new CommSettings().setRange(5).setLossProbability(0.2).setSeed(3), 8.0);
        List<String> sorted = new ArrayList<>(got);
        // l'ordine tra robot diversi nello stesso istante dipende dai thread: confronto per contenuto
        sorted.sort(null);
        return sorted;
    }

}
