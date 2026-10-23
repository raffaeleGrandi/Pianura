package io.github.raffaelegrandi.pianura.remote;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import io.github.raffaelegrandi.pianura.units.ControllerRobot;
import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * UDP server that lets programs outside Pianura (Python, C, another JVM, ...) drive robots.
 *
 * One server, one UDP socket, any number of robots: every message carries the robot id.
 * Messages are JSON objects, one per datagram (see {@code clients/python/README.md}):
 * <pre>
 *   client → Pianura   {"type":"hello","robot":1}
 *   Pianura → client   {"type":"welcome","protocol":2,"robot":1,"mode":"lockstep","period":0.1,...}
 *   Pianura → client   {"type":"sensors","robot":1,"seq":7,"t":0.7,"pose":{...},"ir":[...],"bump":[...],"floor":[...],
 *                    ...,"inbox":[{"from":2,"to":"all","t":0.6,"data":{...}}]}
 *   client → Pianura   {"type":"cmd","robot":1,"seq":7,"v":1.0,"w":0.3}      (or "left"/"right" wheel speeds)
 *                   optional radio messages: "broadcast": data, "send": [{"to":3,"data":...}]
 *   client → Pianura   {"type":"bye","robot":1}
 * </pre>
 * Radio messages go through the simulated network ({@link io.github.raffaelegrandi.pianura.comm.CommNetwork}): range,
 * losses and latency apply as for Java robots. They are sent once, at the control instant of the
 * command that carries them. A cmd with messages but no speeds keeps the previous speeds.
 *
 * Two modes:
 * <ul>
 *   <li>{@link Mode#REALTIME}: each control period the robot sends its sensors and applies the
 *       latest command received, like a real robot over a radio link.</li>
 *   <li>{@link Mode#LOCKSTEP}: each control period the robot waits for the reply to that very
 *       sensor message (matched by {@code seq}). Thanks to the engine barrier the whole simulation
 *       waits too, so runs are reproducible even with an external controller.</li>
 * </ul>
 * Until a client says hello, its robot stands still and the simulation is not blocked.
 * If a client stops answering in lockstep mode, after {@code replyTimeoutMs} the robot keeps
 * its last command and a warning is logged; waiting resumes as soon as the client answers again.
 *
 * By default the server listens on 127.0.0.1 only (this computer). Pass another bind address
 * to accept clients from the network.
 *
 * <pre>
 *   RemoteServer server = RemoteServer.start(5555, RemoteServer.Mode.LOCKSTEP);
 *   addRobot(server.createRobot(1, new Position(5, 5, 0), 2, 0.1));
 * </pre>
 */
public final class RemoteServer implements AutoCloseable {

    public enum Mode { REALTIME, LOCKSTEP }

    /** 2 = protocol 1 + radio messages ("inbox" in sensors, "broadcast"/"send" in cmd). */
    public static final int PROTOCOL_VERSION = 2;
    public static final int DEFAULT_PORT = 5555;
    public static final long DEFAULT_REPLY_TIMEOUT_MS = 1000;

    private final DatagramSocket socket;
    private final Mode mode;
    private final long replyTimeoutMs;
    private final Map<Integer, RemoteController> controllers = new ConcurrentHashMap<>();
    private final Thread receiver;
    private volatile boolean open = true;
    private volatile Consumer<String> logger = System.out::println;

    private RemoteServer(DatagramSocket socket, Mode mode, long replyTimeoutMs) {
        this.socket = socket;
        this.mode = mode;
        this.replyTimeoutMs = replyTimeoutMs;
        this.receiver = Thread.ofPlatform().name("pianura-remote-" + socket.getLocalPort()).daemon(true).start(this::receiveLoop);
    }

    /** Listens on 127.0.0.1:{@code port} (use 0 for any free port). */
    public static RemoteServer start(int port, Mode mode) throws SocketException {
        return start(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), mode, DEFAULT_REPLY_TIMEOUT_MS);
    }

    /** Listens on the given address, e.g. {@code new InetSocketAddress(5555)} for every network interface. */
    public static RemoteServer start(InetSocketAddress bind, Mode mode, long replyTimeoutMs) throws SocketException {
        return new RemoteServer(new DatagramSocket(bind), mode, replyTimeoutMs);
    }

    public int getPort() { return socket.getLocalPort(); }

    public Mode getMode() { return mode; }

    long getReplyTimeoutMs() { return replyTimeoutMs; }

    /** Where connections, timeouts and protocol errors are reported (e.g. the Pianura log). */
    public void setLogger(Consumer<String> logger) { this.logger = logger != null ? logger : m -> {}; }

    void log(String msg) { logger.accept(msg); }

    /** Controller for robot {@code id}, created on first use. */
    public RemoteController controller(int robotId) {
        return controllers.computeIfAbsent(robotId, id -> new RemoteController(this, id));
    }

    /** A robot driven by a remote client, sending sensors every {@code controlPeriod} s of simulated time. */
    public ControllerRobot createRobot(int id, Position pos, double sensorRange, double controlPeriod) {
        RemoteController c = controller(id);
        c.setPeriod(controlPeriod);
        return new ControllerRobot(id, pos, sensorRange, controlPeriod, c);
    }

    /** Tells every connected client that the simulation is over, then closes the socket. */
    @Override
    public void close() {
        if (!open) return;
        for (RemoteController c : controllers.values()) c.notifyStopped();
        open = false;
        socket.close();
        for (RemoteController c : controllers.values()) c.wakeUp();
    }

    // ------------------------------------------------------------------ I/O

    void send(SocketAddress to, Map<String, Object> msg) {
        if (to == null || !open) return;
        byte[] data = MiniJson.write(msg).getBytes(StandardCharsets.UTF_8);
        try {
            socket.send(new DatagramPacket(data, data.length, to));
        } catch (IOException e) {
            if (open) log("Remote: cannot send to " + to + ": " + e.getMessage());
        }
    }

    private void receiveLoop() {
        byte[] buf = new byte[65507];
        while (open) {
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(p);
            } catch (IOException e) {
                if (open) log("Remote: receive error: " + e.getMessage());
                continue;
            }
            String text = new String(p.getData(), p.getOffset(), p.getLength(), StandardCharsets.UTF_8);
            handle(p.getSocketAddress(), text);
        }
    }

    private void handle(SocketAddress from, String text) {
        Map<String, Object> msg;
        try {
            msg = MiniJson.parseObject(text);
        } catch (MiniJson.JsonException e) {
            sendError(from, "malformed JSON: " + e.getMessage());
            return;
        }
        String type = MiniJson.str(msg, "type", "");
        double id = MiniJson.num(msg, "robot", Double.NaN);
        if (Double.isNaN(id)) { sendError(from, "missing \"robot\" field"); return; }
        RemoteController c = controllers.get((int) id);
        if (c == null) { sendError(from, "unknown robot " + (int) id + " (remote robots: " + controllers.keySet() + ")"); return; }
        switch (type) {
            case "hello" -> c.connect(from);
            case "cmd" -> {
                String err = c.onCommand(from, msg);
                if (err != null) sendError(from, err);
            }
            case "bye" -> c.disconnect(from);
            default -> sendError(from, "unknown message type \"" + type + "\"");
        }
    }

    void sendError(SocketAddress to, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "error");
        m.put("message", message);
        send(to, m);
    }

}
