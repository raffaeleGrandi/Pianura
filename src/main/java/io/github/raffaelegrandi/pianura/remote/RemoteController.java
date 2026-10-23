package io.github.raffaelegrandi.pianura.remote;

import java.util.Locale;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import io.github.raffaelegrandi.pianura.comm.Message;
import io.github.raffaelegrandi.pianura.units.RobotController;
import io.github.raffaelegrandi.pianura.units.RobotUnit;
import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * {@link RobotController} that forwards sensors to a remote client and applies its commands.
 * Created by {@link RemoteServer}; see there for the protocol and the two modes.
 */
public final class RemoteController implements RobotController {

    /** A command: either body speeds (v, w) or wheel speeds (left, right, same units as setMotorSpeed). */
    private record Command(long seq, boolean wheels, double a, double b) {}

    /** A radio message asked by the client: to = Message.BROADCAST or a robot id. */
    private record Outgoing(int to, Object data) {}

    /** Sensor datagrams stay below this size: if the inbox is too big, the oldest messages are dropped. */
    static final int MAX_DATAGRAM = 60_000;

    private final RemoteServer server;
    private final int robotId;
    private volatile double period = Double.NaN;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition replied = lock.newCondition();
    private SocketAddress client;          // guarded by lock
    private Command last;                  // guarded by lock
    private long seq = 0;                  // last sensor message sent, guarded by lock
    private boolean lagging = false;       // lockstep: client missed a reply, guarded by lock
    private boolean idleApplied = false;
    private boolean stoppedSent = false;   // guarded by lock
    private final List<Outgoing> outgoing = new ArrayList<>();   // radio messages to send, guarded by lock

    RemoteController(RemoteServer server, int robotId) {
        this.server = server;
        this.robotId = robotId;
    }

    void setPeriod(double p) { period = p; }

    public int getRobotId() { return robotId; }

    /** True once a client has said hello (and not bye). */
    public boolean isConnected() {
        lock.lock();
        try { return client != null; } finally { lock.unlock(); }
    }

    // ------------------------------------------------------------------ from the network

    void connect(SocketAddress from) {
        lock.lock();
        try {
            client = from;
            lagging = false;
        } finally { lock.unlock(); }
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("type", "welcome");
        w.put("protocol", RemoteServer.PROTOCOL_VERSION);
        w.put("robot", robotId);
        w.put("mode", server.getMode().name().toLowerCase());
        if (!Double.isNaN(period)) w.put("period", period);
        Map<String, Object> units = new LinkedHashMap<>();
        units.put("length", "cell");
        units.put("time", "s");
        units.put("angle", "rad");
        w.put("units", units);
        server.send(from, w);
        server.log("Remote client for robot #" + robotId + " connected from " + from);
    }

    void disconnect(SocketAddress from) {
        lock.lock();
        try {
            if (from.equals(client)) client = null;
            replied.signalAll();
        } finally { lock.unlock(); }
        server.log("Remote client for robot #" + robotId + " disconnected");
    }

    /** @return an error message for the client, or null if the command was accepted */
    String onCommand(SocketAddress from, Map<String, Object> msg) {
        long s = (long) MiniJson.num(msg, "seq", -1);

        // radio messages (protocol 2): "broadcast": data, "send": [{"to": id, "data": data}, ...]
        List<Outgoing> radio = new ArrayList<>();
        if (msg.containsKey("broadcast")) radio.add(new Outgoing(Message.BROADCAST, msg.get("broadcast")));
        if (msg.containsKey("send")) {
            if (!(msg.get("send") instanceof List<?> list)) return "cmd: \"send\" must be a list of {\"to\": id, \"data\": ...}";
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m) || !m.containsKey("data")
                        || !(m.get("to") instanceof Number || "all".equals(m.get("to"))))
                    return "cmd: each \"send\" item must be {\"to\": robot id or \"all\", \"data\": ...}";
                int to = m.get("to") instanceof Number n ? n.intValue() : Message.BROADCAST;
                if (to < 0 && to != Message.BROADCAST) return "cmd: \"send\": invalid robot id " + to;
                radio.add(new Outgoing(to, m.get("data")));
            }
        }

        Command c = null;
        if (msg.containsKey("left") || msg.containsKey("right")) {
            double l = MiniJson.num(msg, "left", Double.NaN), r = MiniJson.num(msg, "right", Double.NaN);
            if (!Double.isFinite(l) || !Double.isFinite(r)) return "cmd: \"left\" and \"right\" must both be numbers";
            c = new Command(s, true, l, r);
        } else if (msg.containsKey("v") || radio.isEmpty()) {
            double v = MiniJson.num(msg, "v", Double.NaN), w = MiniJson.num(msg, "w", 0);
            if (!Double.isFinite(v) || !Double.isFinite(w)) return "cmd: \"v\" (and optional \"w\") must be numbers, or use \"left\"/\"right\"";
            c = new Command(s, false, v, w);
        }
        lock.lock();
        try {
            if (client == null) client = from;   // a command implies a connection
            if (c != null) last = c;
            else if (last != null) last = new Command(s, last.wheels(), last.a(), last.b());   // messages only: keep moving as before
            else last = new Command(s, false, 0, 0);
            outgoing.addAll(radio);
            lagging = false;
            replied.signalAll();
        } finally { lock.unlock(); }
        return null;
    }

    void wakeUp() {
        lock.lock();
        try { replied.signalAll(); } finally { lock.unlock(); }
    }

    // ------------------------------------------------------------------ from the simulation

    @Override
    public void control(RobotUnit robot, double simTime) {
        SocketAddress to;
        long mySeq;
        lock.lock();
        try {
            to = client;
            mySeq = ++seq;
        } finally { lock.unlock(); }

        if (to == null) {                       // nobody is driving this robot yet: stand still
            if (!idleApplied) { robot.setRobotSpeed(0, 0); idleApplied = true; }
            if (robot.hasMessages()) robot.receive();   // nobody listens: the radio messages are lost
            return;
        }
        idleApplied = false;
        server.send(to, sensors(robot, simTime, mySeq));

        Command cmd;
        List<Outgoing> radio;
        lock.lock();
        try {
            if (server.getMode() == RemoteServer.Mode.LOCKSTEP && !lagging) {
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(server.getReplyTimeoutMs());
                while (client != null && (last == null || last.seq() < mySeq)) {
                    long left = deadline - System.nanoTime();
                    if (left <= 0) {
                        lagging = true;
                        server.log("Remote: robot #" + robotId + " got no reply for t=" + String.format(Locale.ROOT, "%.2f", simTime)
                            + " s within " + server.getReplyTimeoutMs() + " ms: keeping its last command");
                        break;
                    }
                    try { replied.awaitNanos(left); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
            }
            cmd = last;
            radio = new ArrayList<>(outgoing);      // each message is sent once, now, from the robot thread
            outgoing.clear();
        } finally { lock.unlock(); }

        if (cmd != null) {
            if (cmd.wheels()) robot.setMotorSpeed(cmd.a(), cmd.b());
            else robot.setRobotSpeed(cmd.a(), cmd.b());
        }
        for (Outgoing o : radio) {
            try {
                if (o.to() == Message.BROADCAST) robot.broadcast(o.data());
                else robot.send(o.to(), o.data());
            } catch (IllegalStateException | IllegalArgumentException e) {
                server.sendError(to, "robot " + robotId + ": cannot send radio message: " + e.getMessage());
            }
        }
    }

    @Override
    public void onStop(RobotUnit robot) { notifyStopped(); }

    /** Sends "stopped" to the client (once): robot disabled, simulation stopped or server closing. */
    void notifyStopped() {
        SocketAddress to;
        lock.lock();
        try {
            to = client;
            if (stoppedSent || to == null) return;
            stoppedSent = true;
        } finally { lock.unlock(); }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "stopped");
        m.put("robot", robotId);
        server.send(to, m);
    }

    private Map<String, Object> sensors(RobotUnit r, double t, long s) {
        Position p = r.getPosition();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "sensors");
        m.put("robot", robotId);
        m.put("seq", s);
        m.put("t", Math.round(t * 1e6) / 1e6);
        Map<String, Object> pose = new LinkedHashMap<>();
        pose.put("x", p.x);
        pose.put("y", p.y);
        pose.put("theta", p.theta);
        m.put("pose", pose);
        m.put("ir", r.getAllSens());
        m.put("bump", r.getUnitObject().getBumpSensors().getSensors());
        m.put("contact", r.hasCollision());
        m.put("floor", r.getFloorGrays());
        Map<String, Object> wheels = new LinkedHashMap<>();
        wheels.put("left", r.getLeftWheelTravel());
        wheels.put("right", r.getRightWheelTravel());
        m.put("wheels", wheels);
        m.put("active", r.isActive());

        // radio messages received since the last sensor message (protocol 2)
        List<Map<String, Object>> inbox = new ArrayList<>();
        for (Message msg : r.receive()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("from", msg.from());
            e.put("to", msg.isBroadcast() ? "all" : msg.to());
            e.put("t", Math.round(msg.sentAt() * 1e6) / 1e6);
            e.put("data", msg.payload());
            inbox.add(e);
        }
        m.put("inbox", inbox);
        int dropped = 0;
        while (!inbox.isEmpty() && MiniJson.write(m).length() > MAX_DATAGRAM) { inbox.remove(0); dropped++; }
        if (dropped > 0) {
            m.put("inbox_dropped", dropped);
            server.log("Remote: robot #" + robotId + " inbox too large for one datagram, " + dropped + " oldest messages dropped");
        }
        return m;
    }

}
