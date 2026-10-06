package jusePack.gui.view;

import java.util.Locale;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;

import jusePack.gui.floor.FloorStyle;
import jusePack.units.RobotUnit;
import jusePack.units.UnitObject;
import jusePack.utils.Const;
import jusePack.utils.Position;

/**
 * Pannello laterale con le opzioni di visualizzazione: vista (zoom), livelli,
 * pavimento, informazioni sull'arena e sul robot selezionato.
 */
@SuppressWarnings("serial")
public class ViewSidePanel extends JPanel {

    private static final int WIDTH = 230;
    private static final String[] FLOORS = { "Scenario", "Checkerboard", "Grid", "Plain", "Tiles", "Voronoi", "Track", "Image…" };

    private final ArenaView view;
    private final JLabel zoomLabel = new JLabel();
    private final JLabel robotInfo = new JLabel();
    private final JLabel commInfo = new JLabel();
    private final jusePack.comm.CommNetwork network;
    private final JComboBox<String> floorBox = new JComboBox<>(FLOORS);
    private final FloorStyle scenarioFloor;
    private final Consumer<FloorStyle> floorChanger;
    private int lastFloorIndex = 0;
    private final ViewOptions options;
    private javax.swing.JSlider dist;

    /**
     * @param floorChanger chiamato con il nuovo stile quando l'utente cambia pavimento
     *                     (ridisegna lo sfondo; è solo grafica, la simulazione non cambia)
     */
    public ViewSidePanel(ArenaView view, ViewOptions options, FloorStyle scenarioFloor,
                         Consumer<FloorStyle> floorChanger, String arenaInfo) {
        this(view, options, scenarioFloor, floorChanger, arenaInfo, null, null);
    }

    /**
     * @param network          radio network (null: no communication section)
     * @param openConnectivity opens the connectivity window
     */
    public ViewSidePanel(ArenaView view, ViewOptions options, FloorStyle scenarioFloor,
                         Consumer<FloorStyle> floorChanger, String arenaInfo,
                         jusePack.comm.CommNetwork network, Runnable openConnectivity) {
        this.network = network;
        this.view = view;
        this.options = options;
        this.scenarioFloor = scenarioFloor;
        this.floorChanger = floorChanger;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // --- Vista ---
        JPanel zoomButtons = new JPanel(new GridLayout(2, 2, 3, 3));
        zoomButtons.add(button("Zoom −", "Zoom out (mouse wheel)", () -> view.zoom(1 / 1.25)));
        zoomButtons.add(button("Zoom +", "Zoom in (mouse wheel)", () -> view.zoom(1.25)));
        zoomButtons.add(button("Fit", "Fit to window (double click)", view::fitToWindow));
        zoomButtons.add(button("1:1", "Actual size", view::actualSize));
        zoomButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        zoomButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE, zoomButtons.getPreferredSize().height));
        JPanel vista = section("View");
        vista.add(zoomButtons);
        vista.add(zoomLabel);
        vista.add(small("Drag to pan · wheel to zoom"));
        add(vista);

        // --- Livelli ---
        JPanel livelli = section("Layers");
        livelli.add(layer("IR sensor range", () -> options.sensorRange, v -> options.sensorRange = v));
        livelli.add(layer("Sensor rays", () -> options.sensorRays, v -> options.sensorRays = v));
        livelli.add(layer("Robot trails", () -> options.trails, v -> options.trails = v));
        livelli.add(layer("Floor sensors", () -> options.floorSensors, v -> options.floorSensors = v));
        livelli.add(layer("Robot IDs", () -> options.ids, v -> options.ids = v));
        // distanza dell'etichetta dal centro, in raggi del robot (0 = sopra il robot)
        JLabel distLabel = new JLabel();
        distLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        dist = new javax.swing.JSlider(0, 40, (int) Math.round(options.labelDistanceFromCenter * 10));
        dist.setFocusable(false);
        dist.setAlignmentX(Component.LEFT_ALIGNMENT);
        dist.setToolTipText("Label distance from the robot center, in robot radii (0 = on top of the robot)");
        Runnable showDist = () -> distLabel.setText(String.format(Locale.ROOT, "  Label distance: %.1f radii", dist.getValue() / 10.0));
        dist.addChangeListener(e -> { options.labelDistanceFromCenter = dist.getValue() / 10.0; showDist.run(); view.repaint(); });
        showDist.run();
        livelli.add(distLabel);
        livelli.add(dist);
        livelli.add(layer("Cell grid", () -> options.grid, v -> options.grid = v));
        add(livelli);

        // --- Pavimento ---
        JPanel pav = section("Floor");
        floorBox.setFocusable(false);
        floorBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        floorBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, floorBox.getPreferredSize().height));
        floorBox.addActionListener(e -> onFloorSelected());
        pav.add(floorBox);
        pav.add(small("Read by the floor sensors (line following); IR sensors ignore it"));
        add(pav);

        // --- Communication ---
        if (network != null) {
            JPanel comm = section("Communication");
            comm.add(layer("Radio links & messages", () -> options.commLinks, v -> options.commLinks = v));
            comm.add(layer("Radio range", () -> options.commRange, v -> options.commRange = v));
            commInfo.setAlignmentX(Component.LEFT_ALIGNMENT);
            commInfo.setFont(commInfo.getFont().deriveFont(Font.PLAIN, 11f));
            comm.add(commInfo);
            JButton open = button("Connectivity window…", "Graph and adjacency / incidence matrix, live", openConnectivity);
            open.setAlignmentX(Component.LEFT_ALIGNMENT);
            comm.add(open);
            add(comm);
        }

        // --- Arena ---
        JPanel arena = section("Arena");
        JLabel info = new JLabel(arenaInfo);
        info.setAlignmentX(Component.LEFT_ALIGNMENT);
        arena.add(info);
        add(arena);

        // --- Robot selezionato ---
        JPanel robot = section("Selected robot");
        robotInfo.setVerticalAlignment(JLabel.TOP);
        robotInfo.setAlignmentX(Component.LEFT_ALIGNMENT);
        zoomLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        robotInfo.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        robot.add(robotInfo);
        add(robot);

        add(Box.createVerticalGlue());
        refresh();
    }

    @Override
    public Dimension getPreferredSize() { return new Dimension(WIDTH, super.getPreferredSize().height); }

    @Override
    public Dimension getMinimumSize() { return new Dimension(WIDTH, 0); }

    /** Aggiorna zoom e dati del robot selezionato (chiamato periodicamente dalla GUI). */
    public void refresh() {
        // il cursore segue la distanza anche se cambiata da codice (getViewOptions())
        int want = (int) Math.round(options.labelDistanceFromCenter * 10);
        if (dist != null && !dist.getValueIsAdjusting() && dist.getValue() != want) dist.setValue(want);
        layerBoxes.forEach((box, value) -> { if (box.isSelected() != value.getAsBoolean()) box.setSelected(value.getAsBoolean()); });
        zoomLabel.setText(String.format(Locale.ROOT, "Zoom %.0f%%", view.getTransform().getScale() * 100));
        if (network != null) {
            jusePack.comm.ConnectivityGraph g = network.getGraph();
            jusePack.comm.CommNetwork.Stats s = network.getStats();
            commInfo.setText(String.format(Locale.ROOT, "<html>Range %.1f cells%s<br>%d links · %d components<br>messages: %d sent, %d lost</html>",
                network.getSettings().getRange(), network.getSettings().isLineOfSight() ? " · line of sight" : "",
                g.links().size(), g.componentCount(), s.sent(), s.lost()));
        }
        RobotUnit r = view.getSelected();
        if (r == null) {
            robotInfo.setText("<html><i>Click a robot<br>to select it</i></html>");
            return;
        }
        UnitObject u = r.getUnitObject();
        Position p = r.getPosition();
        int[] ir = u.getIrSensors().getSensors();
        int minIr = Integer.MAX_VALUE, minIdx = -1;
        for (int i = 0; i < ir.length; i++) if (ir[i] < minIr) { minIr = ir[i]; minIdx = i; }
        boolean contact = !u.getBumpSensors().isEmpty();
        double deg = Math.toDegrees(p.theta) % 360;
        if (deg < 0) deg += 360;
        int[] floor = r.getFloorGrays();
        StringBuilder fl = new StringBuilder();
        for (int i = 0; i < floor.length; i++) fl.append(i == 0 ? "" : " · ").append(floor[i]);
        robotInfo.setText(String.format(Locale.ROOT, "<html>Robot #%d — %s<br>x  = %7.2f cells<br>y  = %7.2f cells<br>θ  = %6.1f°<br>"
                + "IR min = %s<br>contact: %s<br>floor: %s</html>",
            r.getIDnum(), r.isActive() ? "active" : "stopped", p.x, p.y, deg,
            minIr >= u.getIrSensors().getInitValue() ? "—" : (minIr + " px (s" + minIdx + ")"),
            contact ? "<font color='#c00000'>yes</font>" : "no", fl));
    }

    private void onFloorSelected() {
        int idx = floorBox.getSelectedIndex();
        FloorStyle style = switch (idx) {
            case 0 -> scenarioFloor;
            case 1 -> FloorStyle.checkerboard();
            case 2 -> FloorStyle.grid(new Color(250, 250, 246), new Color(205, 210, 220), 1);
            case 3 -> FloorStyle.plain(new Color(232, 228, 218));
            case 4 -> FloorStyle.randomTiles();
            case 5 -> FloorStyle.voronoiTiles();
            case 6 -> FloorStyle.track();
            default -> chooseImage();
        };
        if (style == null) {               // scelta del file annullata
            floorBox.setSelectedIndex(lastFloorIndex);
            return;
        }
        lastFloorIndex = idx;
        floorChanger.accept(style);
    }

    private FloorStyle chooseImage() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Images (png, jpg, gif, bmp)", "png", "jpg", "jpeg", "gif", "bmp"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return null;
        File f = fc.getSelectedFile();
        return FloorStyle.image(f.getPath());
    }

    // ---- piccoli helper di layout ----

    private static JPanel section(String title) {
        // altezza massima = altezza preferita: le sezioni non si allungano, lo spazio libero va in fondo
        JPanel p = new JPanel() {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(title), BorderFactory.createEmptyBorder(2, 4, 4, 4)));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private static JButton button(String text, String tip, Runnable action) {
        JButton b = new JButton(text);
        b.setToolTipText(tip);
        b.setFocusable(false);
        b.setMargin(new java.awt.Insets(2, 2, 2, 2));
        b.addActionListener(e -> action.run());
        return b;
    }

    /** Caselle dei livelli con il valore corrente dell'opzione: refresh() le tiene allineate. */
    private final java.util.Map<JCheckBox, java.util.function.BooleanSupplier> layerBoxes = new java.util.LinkedHashMap<>();

    private JCheckBox layer(String text, java.util.function.BooleanSupplier value, Consumer<Boolean> onChange) {
        JCheckBox c = check(text, value.getAsBoolean(), onChange);
        layerBoxes.put(c, value);
        return c;
    }

    private static JCheckBox check(String text, boolean initial, Consumer<Boolean> onChange) {
        JCheckBox c = new JCheckBox(text, initial);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setFocusable(false);
        c.addActionListener(e -> onChange.accept(c.isSelected()));
        return c;
    }

    private static JLabel small(String text) {
        JLabel l = new JLabel("<html>" + text + "</html>");
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 10.5f));
        l.setForeground(Color.DARK_GRAY);
        return l;
    }

    /** Testo informativo sull'arena per il pannello. */
    public static String arenaInfo(double wCells, double hCells, int obstacles, int robots) {
        return String.format(Locale.ROOT, "<html>%s × %s cells (%d × %d px)<br>%d obstacles · %d robots</html>",
            fmt(wCells), fmt(hCells), Math.round(wCells * Const.cellSize), Math.round(hCells * Const.cellSize), obstacles, robots);
    }

    private static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v); }

}
