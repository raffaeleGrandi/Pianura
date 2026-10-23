package io.github.raffaelegrandi.pianura.remote;

import static org.junit.jupiter.api.Assertions.*;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.raffaelegrandi.pianura.sim.SimClock;
import io.github.raffaelegrandi.pianura.sim.SimEngine;
import io.github.raffaelegrandi.pianura.units.RobotDummy;
import io.github.raffaelegrandi.pianura.units.collisions.CollisionDetector;
import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.utils.Position;

/** Protocol tests with a UDP client written in Java, against a headless engine. */
class RemoteServerTest {

    /** Minimal client: what a Python program would do. */
    static final class Client implements AutoCloseable {
        final DatagramSocket socket;
        final InetSocketAddress server;
        Client(int port) throws Exception {
            socket = new DatagramSocket();
            socket.setSoTimeout(3000);
            server = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
        }
        void send(Map<String, Object> m) throws Exception {
            byte[] b = MiniJson.write(m).getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(b, b.length, server));
        }
        void sendRaw(String s) throws Exception {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(b, b.length, server));
        }
        Map<String, Object> receive() throws Exception {
            byte[] buf = new byte[65507];
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            socket.receive(p);
            return MiniJson.parseObject(new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8));
        }
        static Map<String, Object> msg(Object... kv) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
            return m;
        }
        @Override public void close() { socket.close(); }
    }

    /** Engine with one remote robot, run in a background thread up to {@code until} seconds. */
    static final class Sim {
        final RemoteServer server;
        final RobotDummy robot;
        final SimEngine engine;
        final List<String> log = new CopyOnWriteArrayList<>();
        Thread runner;
        Sim(RemoteServer.Mode mode, long timeoutMs) throws Exception {
            server = RemoteServer.start(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), mode, timeoutMs);
            server.setLogger(log::add);
            robot = server.createRobot(1, new Position(10, 20, 0), 2, 0.1);
            SimClock clock = new SimClock(Const.physicsStep);
            engine = new SimEngine(clock, new CollisionDetector(List.of()), List.of(robot));
            engine.setLogger(log::add);
            robot.attachClock(clock);
        }
        /** Avvia robot e motore: il client si è già presentato (come premere Run dopo averlo collegato). */
        void runAsync(double until) {
            robot.start();
            runner = new Thread(() -> engine.runUntil(until));
            runner.start();
        }
        void finish() throws Exception {
            runner.join(15000);
            engine.stop();
            server.close();
        }
    }

    @Test
    @Timeout(20)
    void helloGetsWelcome() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 1000);
        try (Client c = new Client(sim.server.getPort())) {
            c.send(Client.msg("type", "hello", "robot", 1));
            Map<String, Object> w = c.receive();
            assertEquals("welcome", w.get("type"));
            assertEquals(2.0, w.get("protocol"));
            assertEquals("lockstep", w.get("mode"));
            assertEquals(0.1, w.get("period"));
        } finally { sim.engine.stop(); sim.server.close(); }
    }

    @Test
    @Timeout(20)
    void lockstepClientDrivesTheRobotExactly() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 1000);
        List<Double> times = new ArrayList<>();
        try (Client c = new Client(sim.server.getPort())) {
            c.send(Client.msg("type", "hello", "robot", 1));
            assertEquals("welcome", c.receive().get("type"));
            sim.runAsync(2.0);
            // ogni periodo: sensori → comando con lo stesso seq
            while (true) {
                Map<String, Object> s = c.receive();
                if (!"sensors".equals(s.get("type"))) continue;
                times.add((Double) s.get("t"));
                assertEquals(18, ((List<?>) s.get("ir")).size());
                assertEquals(2, ((List<?>) s.get("floor")).size());
                c.send(Client.msg("type", "cmd", "robot", 1, "seq", s.get("seq"), "v", 1.0, "w", 0.0));
                if ((Double) s.get("t") >= 2.0 - 1e-9) break;
            }
            sim.finish();
        }
        assertEquals(21, times.size(), "sensori a t = 0.0, 0.1, …, 2.0: " + times);
        assertEquals(12.0, sim.robot.getPosition().x, 1e-6, "1 cella/s per 2 s a partire da x = 10");
        assertTrue(sim.log.stream().noneMatch(m -> m.contains("no reply")), sim.log.toString());
    }

    @Test
    @Timeout(20)
    void wheelCommandsUseMotorUnits() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 1000);
        try (Client c = new Client(sim.server.getPort())) {
            c.send(Client.msg("type", "hello", "robot", 1));
            c.receive();
            sim.runAsync(1.0);
            while (true) {
                Map<String, Object> s = c.receive();
                if (!"sensors".equals(s.get("type"))) continue;
                c.send(Client.msg("type", "cmd", "robot", 1, "seq", s.get("seq"), "left", 400.0, "right", 400.0));
                if ((Double) s.get("t") >= 1.0 - 1e-9) break;
            }
            sim.finish();
        }
        // setMotorSpeed(400, 400): v = 0.25 * 4 = 1 cella/s
        assertEquals(11.0, sim.robot.getPosition().x, 1e-6);
    }

    @Test
    @Timeout(20)
    void withoutClientTheSimulationIsNotBlocked() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 1000);
        long t0 = System.nanoTime();
        sim.runAsync(5.0);
        sim.finish();
        assertTrue((System.nanoTime() - t0) / 1e9 < 3, "senza client la simulazione non deve aspettare");
        assertEquals(10.0, sim.robot.getPosition().x, 1e-9, "senza client il robot resta fermo");
    }

    @Test
    @Timeout(20)
    void silentClientInLockstepTimesOutOnceAndKeepsLastCommand() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 200);
        try (Client c = new Client(sim.server.getPort())) {
            c.send(Client.msg("type", "hello", "robot", 1));
            c.receive();
            sim.runAsync(2.0);
            Map<String, Object> s = c.receive();
            c.send(Client.msg("type", "cmd", "robot", 1, "seq", s.get("seq"), "v", 1.0));   // un solo comando, poi silenzio
            sim.finish();
        }
        assertEquals(12.0, sim.robot.getPosition().x, 0.11, "continua con l'ultimo comando");
        assertEquals(1, sim.log.stream().filter(m -> m.contains("no reply")).count(), sim.log.toString());
    }

    @Test
    @Timeout(20)
    void realtimeAppliesLatestCommandWithoutWaiting() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.REALTIME, 1000);
        try (Client c = new Client(sim.server.getPort())) {
            c.send(Client.msg("type", "hello", "robot", 1));
            c.receive();
            c.send(Client.msg("type", "cmd", "robot", 1, "v", 1.0));   // senza seq: in tempo reale va bene
            Thread.sleep(200);
            sim.runAsync(3.0);
            sim.finish();
        }
        assertEquals(13.0, sim.robot.getPosition().x, 0.11);
        assertTrue(sim.log.stream().noneMatch(m -> m.contains("no reply")));
    }

    @Test
    @Timeout(20)
    void errorsAreReportedToTheClient() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 1000);
        try (Client c = new Client(sim.server.getPort())) {
            c.sendRaw("{not json");
            assertTrue(((String) c.receive().get("message")).startsWith("malformed JSON"));
            c.send(Client.msg("type", "hello", "robot", 42));
            assertTrue(((String) c.receive().get("message")).contains("unknown robot 42"));
            c.send(Client.msg("type", "cmd", "robot", 1, "v", "fast"));
            assertTrue(((String) c.receive().get("message")).startsWith("cmd:"));
            c.send(Client.msg("type", "dance", "robot", 1));
            assertTrue(((String) c.receive().get("message")).contains("unknown message type"));
            c.send(Client.msg("type", "cmd", "robot", 1, "v", 1.0, "send", List.of(Map.of("data", "no recipient"))));
            assertTrue(((String) c.receive().get("message")).contains("\"send\""));
        } finally { sim.engine.stop(); sim.server.close(); }
    }

    @Test
    @Timeout(20)
    void closingTheServerTellsConnectedClients() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 1000);
        try (Client c = new Client(sim.server.getPort())) {
            c.send(Client.msg("type", "hello", "robot", 1));
            c.receive();
            sim.engine.stop();
            sim.server.close();
            Map<String, Object> m = c.receive();
            assertEquals("stopped", m.get("type"));
            assertEquals(1.0, m.get("robot"));
        }
    }

    @Test
    @Timeout(20)
    void remoteRobotExchangesRadioMessagesWithAJavaRobot() throws Exception {
        RemoteServer server = RemoteServer.start(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), RemoteServer.Mode.LOCKSTEP, 1000);
        List<String> log = new CopyOnWriteArrayList<>();
        server.setLogger(log::add);
        RobotDummy remote = server.createRobot(1, new Position(10, 20, 0), 2, 0.1);
        List<String> gotByJava = new CopyOnWriteArrayList<>();
        RobotDummy local = new io.github.raffaelegrandi.pianura.units.ControllerRobot(2, new Position(15, 20, Math.PI), 2, 0.1, (r, t) -> {
            for (io.github.raffaelegrandi.pianura.comm.Message m : r.receive()) gotByJava.add(m.from() + ":" + m.to() + ":" + m.payload());
            if (t == 0) r.broadcast(Map.of("ping", 2));
        });
        SimClock clock = new SimClock(Const.physicsStep);
        SimEngine engine = new SimEngine(clock, new CollisionDetector(List.of()), List.of(remote, local));
        engine.setLogger(log::add);
        engine.setCommSettings(new io.github.raffaelegrandi.pianura.comm.CommSettings().setRange(6));
        remote.attachClock(clock);
        local.attachClock(clock);

        List<Object> inbox = new ArrayList<>();
        try (Client c = new Client(server.getPort())) {
            c.send(Client.msg("type", "hello", "robot", 1));
            c.receive();
            remote.start();
            local.start();
            Thread runner = new Thread(() -> engine.runUntil(1.0));
            runner.start();
            while (true) {
                Map<String, Object> s = c.receive();
                if (!"sensors".equals(s.get("type"))) continue;
                double t = (Double) s.get("t");
                inbox.addAll((List<?>) s.get("inbox"));
                Map<String, Object> cmd = Client.msg("type", "cmd", "robot", 1, "seq", s.get("seq"));
                if (t == 0) { cmd.put("v", 1.0); cmd.put("broadcast", Map.of("hi", "from remote")); }
                else if (Math.abs(t - 0.5) < 1e-9) cmd.put("send", List.of(Map.of("to", 2, "data", "unicast")));   // solo messaggi
                else cmd.put("v", 1.0);
                c.send(cmd);
                if (t >= 1.0 - 1e-9) break;
            }
            runner.join(15000);
            engine.stop();
            server.close();
        }
        // il client remoto riceve il ping del robot Java nel campo "inbox"
        assertEquals(1, inbox.size(), inbox.toString());
        Map<?, ?> m = (Map<?, ?>) inbox.get(0);
        assertEquals(2.0, m.get("from"));
        assertEquals("all", m.get("to"));
        assertEquals(0.0, m.get("t"));
        assertEquals(Map.of("ping", 2.0), m.get("data"));
        // il robot Java riceve il broadcast e il messaggio diretto, una volta sola ciascuno
        assertEquals(List.of("1:-1:{hi=from remote}", "1:2:unicast"), gotByJava);
        // un comando con soli messaggi mantiene la velocità precedente
        assertEquals(11.0, remote.getPosition().x, 1e-6);
        assertTrue(log.stream().noneMatch(s -> s.contains("no reply")), log.toString());
    }

    @Test
    @Timeout(30)
    void lockstepRunsAreReproducible() throws Exception {
        double[] a = steeringRun(), b = steeringRun();
        assertArrayEquals(a, b, 0.0);
    }

    /** Client che sterza in base al tempo: deve dare lo stesso risultato a ogni esecuzione. */
    private static double[] steeringRun() throws Exception {
        Sim sim = new Sim(RemoteServer.Mode.LOCKSTEP, 1000);
        try (Client c = new Client(sim.server.getPort())) {
            c.send(Client.msg("type", "hello", "robot", 1));
            c.receive();
            sim.runAsync(3.0);
            while (true) {
                Map<String, Object> s;
                try { s = c.receive(); } catch (SocketTimeoutException e) { break; }
                if (!"sensors".equals(s.get("type"))) continue;
                double t = (Double) s.get("t");
                c.send(Client.msg("type", "cmd", "robot", 1, "seq", s.get("seq"), "v", 1.0, "w", Math.sin(t * 3)));
                if (t >= 3.0 - 1e-9) break;
            }
            sim.finish();
        }
        Position p = sim.robot.getPosition();
        return new double[] { p.x, p.y, p.theta };
    }

}
