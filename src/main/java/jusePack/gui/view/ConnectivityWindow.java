package jusePack.gui.view;

import java.util.Locale;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.Timer;

import jusePack.comm.CommNetwork;
import jusePack.comm.ConnectivityGraph;
import jusePack.sim.SimClock;
import jusePack.units.RobotUnit;
import jusePack.utils.Const;
import jusePack.utils.Position;

/**
 * Live view of the radio network: the connectivity graph (nodes at the robots' positions or on
 * a circle) and its matrix (adjacency, robots × robots, or incidence, robots × links).
 * Cells that change flash for a second: green when a link appears, red when it breaks.
 */
@SuppressWarnings("serial")
public class ConnectivityWindow extends JFrame {

    private static final long FLASH_MS = 1200;
    private static final Color UP = new Color(40, 180, 70), DOWN = new Color(220, 50, 50);

    private final CommNetwork network;
    private final SimClock clock;
    private final List<? extends RobotUnit> robots;
    private final double arenaW, arenaH;

    private final JComboBox<String> matrixKind = new JComboBox<>(new String[] { "Adjacency", "Incidence" });
    private final JComboBox<String> layout = new JComboBox<>(new String[] { "Positions", "Circle" });
    private final JLabel stats = new JLabel();
    private final GraphPanel graphPanel = new GraphPanel();
    private final MatrixPanel matrixPanel = new MatrixPanel();
    private final JTextArea eventsArea = new JTextArea(7, 30);
    private final Timer timer;

    private ConnectivityGraph shown = ConnectivityGraph.empty();
    // celle che lampeggiano: chiave "da>a" (id dei robot) → [istante di fine (ms), 1 = nato / 0 = rotto]
    private final Map<String, long[]> flashes = new HashMap<>();
    private Set<String> previousLinks = new HashSet<>();
    private final Map<String, Long> newLinkFlash = new HashMap<>();   // colonne di incidenza nuove

    public ConnectivityWindow(CommNetwork network, SimClock clock, List<? extends RobotUnit> robots,
                              double arenaWidthPx, double arenaHeightPx) {
        super("JUSE - Connectivity");
        this.network = network;
        this.clock = clock;
        this.robots = robots;
        this.arenaW = arenaWidthPx;
        this.arenaH = arenaHeightPx;

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.add(new JLabel("Matrix:"));
        controls.add(matrixKind);
        controls.add(new JLabel("  Graph layout:"));
        controls.add(layout);
        JPanel top = new JPanel(new BorderLayout());
        top.add(controls, BorderLayout.NORTH);
        stats.setBorder(BorderFactory.createEmptyBorder(0, 10, 4, 10));
        top.add(stats, BorderLayout.SOUTH);
        matrixKind.setFocusable(false);
        layout.setFocusable(false);
        matrixKind.addActionListener(e -> { matrixPanel.revalidate(); matrixPanel.repaint(); });
        layout.addActionListener(e -> graphPanel.repaint());

        eventsArea.setEditable(false);
        eventsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JPanel right = new JPanel(new BorderLayout());
        right.add(new JScrollPane(matrixPanel), BorderLayout.CENTER);
        JScrollPane ev = new JScrollPane(eventsArea);
        ev.setBorder(BorderFactory.createTitledBorder("Latest link changes"));
        right.add(ev, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, graphPanel, right);
        split.setResizeWeight(0.5);
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(split, BorderLayout.CENTER);
        setSize(1000, 600);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        timer = new Timer(150, e -> refresh());
        timer.start();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { timer.stop(); }
        });
        refresh();
    }

    // ------------------------------------------------------------------ aggiornamento

    private static String key(int from, int to) { return from + ">" + to; }

    private void refresh() {
        ConnectivityGraph g = network.getGraph();
        if (g != shown) {
            long now = System.currentTimeMillis();
            Set<String> links = new HashSet<>();
            for (int i = 0; i < g.size(); i++)
                for (int j = 0; j < g.size(); j++)
                    if (g.reaches(i, j)) links.add(key(g.id(i), g.id(j)));
            for (String k : links) if (!previousLinks.contains(k)) flashes.put(k, new long[] { now + FLASH_MS, 1 });
            for (String k : previousLinks) if (!links.contains(k)) flashes.put(k, new long[] { now + FLASH_MS, 0 });
            for (ConnectivityGraph.Link l : g.links()) {
                String k = Math.min(g.id(l.a()), g.id(l.b())) + "-" + Math.max(g.id(l.a()), g.id(l.b()));
                boolean existed = previousLinks.contains(key(g.id(l.a()), g.id(l.b()))) || previousLinks.contains(key(g.id(l.b()), g.id(l.a())));
                if (!existed) newLinkFlash.put(k, now + FLASH_MS);
            }
            previousLinks = links;
            shown = g;
            StringBuilder sb = new StringBuilder();
            for (CommNetwork.LinkEvent e : network.getLatestLinkEvents(40))
                sb.append(String.format(Locale.ROOT, "t = %8.2f s   %3d → %-3d %s%n", e.time(), e.from(), e.to(), e.up() ? "up" : "down"));
            eventsArea.setText(sb.toString());
        }
        long now = System.currentTimeMillis();
        flashes.values().removeIf(f -> f[0] < now);
        newLinkFlash.values().removeIf(t -> t < now);
        CommNetwork.Stats s = network.getStats();
        int oneWay = 0;
        for (ConnectivityGraph.Link l : g.links()) if (!l.bidirectional()) oneWay++;
        int isolated = 0;
        for (int i = 0; i < g.size(); i++) if (g.isolated(i)) isolated++;
        stats.setText(String.format(Locale.ROOT, "t = %.2f s · %d robots · %d links (%d one-way) · %d groups + %d isolated · messages: %d sent, %d delivered, %d lost",
            clock.now(), g.size(), g.links().size(), oneWay, g.componentCount() - isolated, isolated, s.sent(), s.delivered(), s.lost()));
        matrixPanel.revalidate();
        matrixPanel.repaint();
        graphPanel.repaint();
    }

    private static Graphics2D smooth(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    // ------------------------------------------------------------------ grafo

    private class GraphPanel extends JPanel {
        GraphPanel() {
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(480, 480));
        }

        private Point2D[] layoutNodes(ConnectivityGraph g) {
            int n = g.size();
            Point2D[] p = new Point2D[n];
            double w = getWidth(), h = getHeight(), m = 30;
            if (layout.getSelectedIndex() == 1 || n == 0) {
                double r = Math.max(10, Math.min(w, h) / 2 - m);
                for (int i = 0; i < n; i++) {
                    double a = 2 * Math.PI * i / Math.max(1, n) - Math.PI / 2;
                    p[i] = new Point2D.Double(w / 2 + r * Math.cos(a), h / 2 + r * Math.sin(a));
                }
                return p;
            }
            double s = Math.min((w - 2 * m) / arenaW, (h - 2 * m) / arenaH);
            double ox = (w - arenaW * s) / 2, oy = (h - arenaH * s) / 2;
            Map<Integer, Position> pos = new HashMap<>();
            for (RobotUnit r : robots) pos.put(r.getIDnum(), r.getPosition());
            for (int i = 0; i < n; i++) {
                Position q = pos.get(g.id(i));
                p[i] = new Point2D.Double(ox + q.x * Const.cellSize * s, oy + q.y * Const.cellSize * s);
            }
            return p;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = smooth(g0);
            ConnectivityGraph gr = shown;
            Point2D[] p = layoutNodes(gr);
            if (layout.getSelectedIndex() == 0) {   // contorno dell'arena
                double w = getWidth(), h = getHeight(), m = 30;
                double s = Math.min((w - 2 * m) / arenaW, (h - 2 * m) / arenaH);
                g.setColor(new Color(220, 220, 220));
                g.draw(new Rectangle2D.Double((w - arenaW * s) / 2, (h - arenaH * s) / 2, arenaW * s, arenaH * s));
            }
            double nodeR = 11;
            for (ConnectivityGraph.Link l : gr.links()) {
                g.setColor(ArenaView.componentColor(gr.component(l.a())));
                Point2D a = p[l.a()], b = p[l.b()];
                if (l.bidirectional()) {
                    g.setStroke(new BasicStroke(2f));
                    g.draw(new Line2D.Double(a, b));
                } else {
                    Point2D from = l.aToB() ? a : b, to = l.aToB() ? b : a;
                    g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 6, 4 }, 0f));
                    g.draw(new Line2D.Double(from, to));
                    arrow(g, from, to, nodeR + 1, 9);
                }
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i < gr.size(); i++) {
                Ellipse2D e = new Ellipse2D.Double(p[i].getX() - nodeR, p[i].getY() - nodeR, 2 * nodeR, 2 * nodeR);
                g.setColor(gr.isolated(i) ? new Color(150, 150, 150) : ArenaView.componentColor(gr.component(i)));
                g.fill(e);
                g.setColor(new Color(40, 40, 40));
                g.setStroke(new BasicStroke(1.2f));
                g.draw(e);
                String t = String.valueOf(gr.id(i));
                g.setColor(Color.WHITE);
                g.drawString(t, (float) (p[i].getX() - fm.stringWidth(t) / 2.0), (float) (p[i].getY() + fm.getAscent() / 2.0 - 1));
            }
            g.dispose();
        }

        private void arrow(Graphics2D g, Point2D from, Point2D to, double back, double size) {
            double dx = to.getX() - from.getX(), dy = to.getY() - from.getY(), d = Math.hypot(dx, dy);
            if (d < back + size) return;
            double ux = dx / d, uy = dy / d, tx = to.getX() - ux * back, ty = to.getY() - uy * back;
            Path2D a = new Path2D.Double();
            a.moveTo(tx, ty);
            a.lineTo(tx - ux * size - uy * size * 0.5, ty - uy * size + ux * size * 0.5);
            a.lineTo(tx - ux * size + uy * size * 0.5, ty - uy * size - ux * size * 0.5);
            a.closePath();
            g.fill(a);
        }
    }

    // ------------------------------------------------------------------ matrice

    private class MatrixPanel extends JPanel {
        static final int CELL = 24, HEAD = 34;

        MatrixPanel() { setBackground(Color.WHITE); }

        private boolean incidence() { return matrixKind.getSelectedIndex() == 1; }

        /** Height of the column header: link names ("10-14") are written vertically. */
        private int top() { return incidence() ? 52 : HEAD; }

        @Override
        public Dimension getPreferredSize() {
            ConnectivityGraph g = shown;
            int cols = incidence() ? g.links().size() : g.size();
            return new Dimension(HEAD + 10 + Math.max(1, cols) * CELL, top() + 10 + Math.max(1, g.size()) * CELL);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = smooth(g0);
            ConnectivityGraph gr = shown;
            int n = gr.size();
            long now = System.currentTimeMillis();
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            FontMetrics fm = g.getFontMetrics();
            List<ConnectivityGraph.Link> links = gr.links();
            int cols = incidence() ? links.size() : n;
            int[][] m = incidence() ? gr.incidence() : gr.adjacency();
            final int TOP = top();

            // intestazioni
            g.setColor(new Color(70, 70, 70));
            for (int i = 0; i < n; i++) {
                String t = String.valueOf(gr.id(i));
                g.drawString(t, HEAD - 6 - fm.stringWidth(t), TOP + i * CELL + CELL / 2 + fm.getAscent() / 2 - 2);
            }
            for (int c = 0; c < cols; c++) {
                String t = incidence() ? gr.id(links.get(c).a()) + "-" + gr.id(links.get(c).b()) : String.valueOf(gr.id(c));
                AffineTransform saved = g.getTransform();
                g.translate(HEAD + c * CELL + CELL / 2 + fm.getAscent() / 2 - 2, TOP - 6);
                if (incidence()) g.rotate(-Math.PI / 2);
                g.drawString(t, incidence() ? 0 : -fm.stringWidth(t) / 2 - fm.getAscent() / 2 + 2, 0);
                g.setTransform(saved);
            }

            for (int i = 0; i < n; i++) {
                for (int c = 0; c < cols; c++) {
                    int x = HEAD + c * CELL, y = TOP + i * CELL;
                    Color bg = Color.WHITE;
                    if (!incidence() && i == c) bg = new Color(235, 235, 235);
                    else if (m[i][c] != 0) {
                        Color comp = ArenaView.componentColor(gr.component(i));
                        bg = new Color(comp.getRed(), comp.getGreen(), comp.getBlue(), 70);
                    }
                    g.setColor(bg);
                    g.fillRect(x, y, CELL, CELL);
                    // lampeggio delle celle cambiate
                    long[] f = null;
                    if (!incidence()) f = flashes.get(key(gr.id(i), gr.id(c)));
                    else {
                        ConnectivityGraph.Link l = links.get(c);
                        Long until = newLinkFlash.get(Math.min(gr.id(l.a()), gr.id(l.b())) + "-" + Math.max(gr.id(l.a()), gr.id(l.b())));
                        if (until != null && m[i][c] != 0) f = new long[] { until, 1 };
                    }
                    if (f != null) {
                        float a = Math.max(0f, Math.min(1f, (f[0] - now) / (float) FLASH_MS));
                        Color fc = f[1] == 1 ? UP : DOWN;
                        g.setColor(new Color(fc.getRed(), fc.getGreen(), fc.getBlue(), (int) (200 * a)));
                        g.fillRect(x, y, CELL, CELL);
                    }
                    g.setColor(new Color(215, 215, 215));
                    g.drawRect(x, y, CELL, CELL);
                    if (m[i][c] != 0) {
                        String t = m[i][c] > 0 ? (incidence() && !links.get(c).bidirectional() ? "+1" : "1") : "-1";
                        g.setColor(Color.BLACK);
                        g.drawString(t, x + (CELL - fm.stringWidth(t)) / 2, y + CELL / 2 + fm.getAscent() / 2 - 2);
                    }
                }
            }
            if (incidence() && links.isEmpty()) {
                g.setColor(Color.GRAY);
                g.drawString("no links", HEAD, TOP + 14);
            }
            g.dispose();
        }
    }

}
