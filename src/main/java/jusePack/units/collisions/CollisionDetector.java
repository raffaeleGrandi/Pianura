package jusePack.units.collisions;

import java.util.Locale;

import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import jusePack.ArenaObjects.Obstacle;
import jusePack.ArenaObjects.ObjectID;
import jusePack.units.UnitObject;
import jusePack.utils.Const;
import jusePack.utils.Position;

/**
 * Rilevamento collisioni basato su ray casting geometrico.
 *
 * Per ogni robot vengono lanciati 18 raggi (uno per sensore) dal centro
 * del robot nella direzione assoluta del sensore. Per ogni raggio si cerca
 * l'intersezione più vicina con:
 *   - gli ostacoli statici dello scenario e i bordi dell'arena (forme costruite una volta);
 *   - i corpi degli altri robot (cerchi ricostruiti a ogni passo da SimEngine).
 * Il valore del sensore è la distanza in pixel dalla superficie del robot.
 *
 * Il detector è privo di stato mutabile condiviso: può essere invocato
 * per robot diversi senza sincronizzazione.
 */
public class CollisionDetector {

    /**
     * Forme statiche: tutte (muri + ostacoli), solo ostacoli (parallela a {@code obstacles}),
     * i quattro muri e la griglia che indicizza gli ostacoli per posizione.
     */
    private record Shapes(List<CollidableShape> all, List<CollidableShape> perObstacle,
                          List<CollidableShape> walls, ShapeGrid grid) {}

    /** Lato di una casella della griglia degli ostacoli, in pixel di mondo (4 celle). */
    static final double GRID_BUCKET_PX = 4 * Const.cellSize;

    /** Solo per i test: ignora la griglia e prova tutte le forme (il risultato deve essere identico). */
    static volatile boolean bruteForceForTests = false;

    /** Indici (crescenti) degli ostacoli il cui ingombro tocca il rettangolo dato. */
    private static int[] near(Shapes sh, double ax, double ay, double bx, double by) {
        if (bruteForceForTests) {
            int[] all = new int[sh.perObstacle().size()];
            int m = 0;
            for (int i = 0; i < all.length; i++) if (sh.perObstacle().get(i) != null) all[m++] = i;
            return java.util.Arrays.copyOf(all, m);
        }
        return sh.grid().query(ax, ay, bx, by);
    }

    /** Sostituita in blocco da {@link #rebuild()}: chi legge vede sempre un insieme coerente. */
    private volatile Shapes shapes;
    private final List<Obstacle> obstacles;
    private final double robotRadiusPx;
    private final double bumpRangePx;
    private final double cellSizePx;
    private final double arenaWidthPx, arenaHeightPx;

    /** Arena standard (Const.arenaWidth x Const.arenaHeight). */
    public CollisionDetector(List<Obstacle> obstacles) {
        this(obstacles, Const.arenaWidth, Const.arenaHeight);
    }

    /**
     * @param arenaWidthPx  larghezza dell'arena in pixel di mondo (celle x Const.cellSize)
     * @param arenaHeightPx altezza dell'arena in pixel di mondo
     */
    public CollisionDetector(List<Obstacle> obstacles, double arenaWidthPx, double arenaHeightPx) {
        this.arenaWidthPx  = arenaWidthPx;
        this.arenaHeightPx = arenaHeightPx;
        this.robotRadiusPx = Const.unitRadius;
        this.bumpRangePx   = Const.sensorsCheckingRange0;
        this.cellSizePx    = Const.cellSize;
        this.obstacles     = List.copyOf(obstacles);
        this.shapes        = buildShapes(this.obstacles);
    }

    /**
     * Ricalcola le forme dopo che ostacoli sono stati spostati o ruotati
     * (le posizioni sono lette dagli stessi oggetti Obstacle passati al costruttore).
     */
    public void rebuild() {
        shapes = buildShapes(obstacles);
    }

    public List<Obstacle> getObstacles() { return obstacles; }

    /**
     * True if no obstacle (or arena wall) lies on the segment between the two points (pixels).
     * Robots do not block the line of sight.
     */
    public boolean lineOfSight(double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay, d = Math.hypot(dx, dy);
        if (d < 1e-9) return true;
        dx /= d;
        dy /= d;
        Shapes sh = shapes;
        for (CollidableShape s : sh.walls()) {
            double hit = s.rayIntersect(ax, ay, dx, dy, d);
            if (hit >= 0 && hit < d - 1e-6) return false;
        }
        for (int i : near(sh, Math.min(ax, bx), Math.min(ay, by), Math.max(ax, bx), Math.max(ay, by))) {
            double hit = sh.perObstacle().get(i).rayIntersect(ax, ay, dx, dy, d);
            if (hit >= 0 && hit < d - 1e-6) return false;
        }
        return true;
    }

    // ---------------------------------------------------------
    //  Forme statiche
    // ---------------------------------------------------------

    private Shapes buildShapes(List<Obstacle> obstacles) {
        List<CollidableShape> result = new ArrayList<>();
        List<CollidableShape> perObstacle = new ArrayList<>();
        List<CollidableShape> walls = new ArrayList<>();

        // muri dell'arena: fuori dal pavimento, con la faccia interna esattamente sul bordo
        double aw = arenaWidthPx, ah = arenaHeightPx, t = WALL_THICKNESS_PX;
        result.add(new RectShape(-t / 2,     ah / 2,     t,          ah + 2 * t, 0));  // sinistro
        result.add(new RectShape(aw + t / 2, ah / 2,     t,          ah + 2 * t, 0));  // destro
        result.add(new RectShape(aw / 2,     -t / 2,     aw + 2 * t, t,          0));  // superiore
        result.add(new RectShape(aw / 2,     ah + t / 2, aw + 2 * t, t,          0));  // inferiore
        walls.addAll(result);

        for (Obstacle obs : obstacles) {
            ObjectID id = obs.getID();
            double px    = id.getPos().x * cellSizePx;
            double py    = id.getPos().y * cellSizePx;
            double w     = id.getWidth()  * cellSizePx;
            double h     = id.getHeight() * cellSizePx;
            double angle = id.getAngle();

            CollidableShape shape = switch (id.getType()) {
                case OBSTACLE_RECT -> new RectShape(px, py, w, h, angle);
                case OBSTACLE_OVAL -> new OvalShape(px, py, w, h, angle);
                default -> null;
            };
            perObstacle.add(shape);
            if (shape != null) result.add(shape);
        }
        return new Shapes(List.copyOf(result), java.util.Collections.unmodifiableList(perObstacle),
            List.copyOf(walls), new ShapeGrid(perObstacle, GRID_BUCKET_PX));
    }

    // ---------------------------------------------------------
    //  Forme dinamiche (robot)
    // ---------------------------------------------------------

    /**
     * Costruisce lo snapshot dei corpi dei robot nella posizione corrente.
     * Da chiamare una volta per tick, prima di aggiornare i sensori:
     * così tutti i robot "vedono" lo stesso istante, indipendentemente
     * dall'ordine in cui vengono processati.
     */
    public List<CircleShape> snapshotRobots(List<UnitObject> units) {
        List<CircleShape> list = new ArrayList<>(units.size());
        for (UnitObject u : units) {
            Position p = u.getExactPos();
            list.add(new CircleShape(p.x * cellSizePx, p.y * cellSizePx, robotRadiusPx, u));
        }
        return list;
    }

    // ---------------------------------------------------------
    //  Aggiornamento sensori
    // ---------------------------------------------------------

    /** Solo ostacoli statici (compatibilità). */
    public void testUnitForCollisions(UnitObject testUnit) {
        testUnitForCollisions(testUnit, List.of());
    }

    /**
     * Calcola i sensori del robot considerando ostacoli statici e altri robot.
     * @param robots snapshot dei corpi robot (il robot stesso viene escluso)
     */
    public void testUnitForCollisions(UnitObject testUnit, List<CircleShape> robots) {
        SensorArray bumpSensors = testUnit.getBumpSensors();
        SensorArray irSensors   = testUnit.getIrSensors();
        int n = Const.sensorCount;

        int[] bumpVal = new int[n];
        int[] irVal   = new int[n];
        java.util.Arrays.fill(bumpVal, bumpSensors.getInitValue());
        java.util.Arrays.fill(irVal,   irSensors.getInitValue());
        Point2D[] bumpPts = new Point2D[n];
        Point2D[] irPts   = new Point2D[n];

        double maxRayDist = robotRadiusPx + cellSizePx * testUnit.getSensorRange();

        Position pos   = testUnit.getExactPos();
        double cx      = pos.x * cellSizePx;
        double cy      = pos.y * cellSizePx;
        double heading = pos.theta;

        // solo gli ostacoli e i robot che i raggi possono raggiungere
        Shapes sh = shapes;
        int[] nearIdx = near(sh, cx - maxRayDist, cy - maxRayDist, cx + maxRayDist, cy + maxRayDist);
        List<CollidableShape> candidates = new ArrayList<>(sh.walls().size() + nearIdx.length + 4);
        candidates.addAll(sh.walls());
        for (int k : nearIdx) candidates.add(sh.perObstacle().get(k));
        double reach = maxRayDist + robotRadiusPx;
        for (CircleShape s : robots) {
            if (s.getOwner() == testUnit) continue;
            double ddx = s.getCx() - cx, ddy = s.getCy() - cy;
            if (bruteForceForTests || ddx * ddx + ddy * ddy <= reach * reach) candidates.add(s);
        }

        for (int i = 0; i < n; i++) {
            double a  = heading + irSensors.getSensorAngle(i);
            double dx = Math.cos(a);
            double dy = Math.sin(a);

            double d = findNearestHit(candidates, cx, cy, dx, dy, maxRayDist);
            if (d < 0) continue;  // niente nel campo: restano i valori di riposo

            int surfaceDist = (int) Math.max(0, d - robotRadiusPx);
            Point2D hit = new Point2D.Double((cx + d * dx) / cellSizePx, (cy + d * dy) / cellSizePx);

            irVal[i] = surfaceDist;
            irPts[i] = hit;

            if (surfaceDist < bumpRangePx) {
                // >= 1: il valore 0 coincide con "a riposo" per i bump
                bumpVal[i] = Math.max(1, surfaceDist);
                bumpPts[i] = hit;
                if (Const.debugEnabled)
                    System.out.println("CD>unit #" + testUnit.getIDnum() + " bump #" + i + " dist:" + surfaceDist + "px");
            }
        }

        // Pubblicazione atomica: il thread del robot non vede mai stati intermedi
        irSensors.publish(irVal, irPts);
        bumpSensors.publish(bumpVal, bumpPts);
    }

    private static double findNearestHit(List<CollidableShape> candidates,
                                         double ox, double oy, double dx, double dy, double maxDist) {
        double nearest = -1;
        for (CollidableShape s : candidates) {
            double d = s.rayIntersect(ox, oy, dx, dy, maxDist);
            if (d >= 0 && (nearest < 0 || d < nearest)) nearest = d;
        }
        return nearest;
    }

    // ---------------------------------------------------------
    //  Validazione dello scenario
    // ---------------------------------------------------------

    /**
     * Esito della validazione: i messaggi e gli elementi coinvolti
     * (indici degli ostacoli nell'ordine dello scenario, robot), per evidenziarli nella vista.
     */
    public record Validation(List<String> messages, java.util.Set<Integer> badObstacles, java.util.Set<UnitObject> badRobots) {
        public boolean isOk() { return messages.isEmpty(); }
    }

    /** Ostacoli (compenetrazioni, fuori arena) e robot (sovrapposti a ostacoli, muri o altri robot). */
    public Validation validate(List<UnitObject> units) {
        List<String> msgs = new ArrayList<>();
        java.util.Set<Integer> badObs = new java.util.TreeSet<>();
        java.util.Set<UnitObject> badRob = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        checkObstacles(msgs, badObs);
        checkPlacement(units, msgs, badRob);
        return new Validation(List.copyOf(msgs), java.util.Collections.unmodifiableSet(badObs), java.util.Collections.unmodifiableSet(badRob));
    }

    /**
     * Controlla che nessun robot parta sovrapposto a un ostacolo, ai bordi
     * o a un altro robot. Restituisce un messaggio per ogni problema trovato.
     */
    public List<String> validatePlacement(List<UnitObject> units) {
        List<String> msgs = new ArrayList<>();
        checkPlacement(units, msgs, new java.util.HashSet<>());
        return msgs;
    }

    /**
     * Controlla che gli ostacoli non si compenetrino tra loro e non escano dall'arena.
     * È un avviso, non un errore: due ostacoli sovrapposti possono essere voluti
     * (es. un muro a L fatto con due rettangoli), ma due oggetti fisici distinti
     * non possono occupare lo stesso spazio.
     * Le sovrapposizioni sotto {@code MIN_OVERLAP_PX2} (contatto, arrotondamenti) sono ignorate.
     */
    public List<String> validateObstacles() {
        List<String> msgs = new ArrayList<>();
        checkObstacles(msgs, new java.util.TreeSet<>());
        return msgs;
    }

    private void checkPlacement(List<UnitObject> units, List<String> problems, java.util.Set<UnitObject> bad) {
        Shapes sh = shapes;
        int probes = 72; // un raggio ogni 5 gradi
        for (UnitObject u : units) {
            Position p = u.getExactPos();
            double cx = p.x * cellSizePx, cy = p.y * cellSizePx;
            List<CollidableShape> all = new ArrayList<>(sh.walls());
            for (int k : near(sh, cx - robotRadiusPx, cy - robotRadiusPx, cx + robotRadiusPx, cy + robotRadiusPx))
                all.add(sh.perObstacle().get(k));
            for (int k = 0; k < probes; k++) {
                double a = 2 * Math.PI * k / probes;
                double dx = Math.cos(a), dy = Math.sin(a);
                boolean hit = false;
                for (CollidableShape s : all) {
                    if (s.rayIntersect(cx, cy, dx, dy, robotRadiusPx) >= 0) { hit = true; break; }
                }
                if (hit) {
                    problems.add("Robot #" + u.getIDnum() + " at " + fmt(p) + " overlaps an obstacle or the arena border");
                    bad.add(u);
                    break;
                }
            }
        }
        for (int i = 0; i < units.size(); i++) {
            for (int j = i + 1; j < units.size(); j++) {
                Position a = units.get(i).getExactPos(), b = units.get(j).getExactPos();
                double dist = Math.hypot(a.x - b.x, a.y - b.y) * cellSizePx;
                if (dist < 2 * robotRadiusPx + bumpRangePx) {
                    problems.add("Robot #" + units.get(i).getIDnum() + " and #" + units.get(j).getIDnum()
                        + " start in contact or overlapping (center distance " + String.format(Locale.ROOT, "%.1f", dist / cellSizePx) + " cells)");
                    bad.add(units.get(i));
                    bad.add(units.get(j));
                }
            }
        }
    }

    private void checkObstacles(List<String> problems, java.util.Set<Integer> bad) {
        Shapes sh = shapes;
        List<Area> areas = new ArrayList<>();
        for (CollidableShape s : sh.perObstacle()) areas.add(s == null ? null : new Area(s.outline()));

        double cell2 = cellSizePx * cellSizePx;
        for (int i = 0; i < areas.size(); i++) {
            if (areas.get(i) == null) continue;
            Rectangle2D bi = sh.grid().bounds(i);
            for (int j : near(sh, bi.getMinX(), bi.getMinY(), bi.getMaxX(), bi.getMaxY())) {   // solo i vicini, in ordine
                if (j <= i || areas.get(j) == null) continue;
                // scarto rapido: rettangoli di ingombro disgiunti
                if (!areas.get(i).getBounds2D().intersects(areas.get(j).getBounds2D())) continue;
                Area inter = new Area(areas.get(i));
                inter.intersect(areas.get(j));
                double a = areaOf(inter);
                if (a > MIN_OVERLAP_PX2) {
                    problems.add(describe(i) + " and " + describe(j)
                        + " overlap (shared area " + String.format(Locale.ROOT, "%.2f", a / cell2) + " cells²)");
                    bad.add(i);
                    bad.add(j);
                }
            }
        }

        Area arena = new Area(new Rectangle2D.Double(0, 0, arenaWidthPx, arenaHeightPx));
        for (int i = 0; i < areas.size(); i++) {
            if (areas.get(i) == null) continue;
            Rectangle2D b = areas.get(i).getBounds2D();
            if (b.getMinX() >= 0 && b.getMinY() >= 0 && b.getMaxX() <= arenaWidthPx && b.getMaxY() <= arenaHeightPx) continue;
            Area outside = new Area(areas.get(i));
            outside.subtract(arena);
            double a = areaOf(outside);
            if (a > MIN_OVERLAP_PX2) {
                problems.add(describe(i) + " extends outside the arena (" + String.format(Locale.ROOT, "%.2f", a / cell2) + " cells² beyond the border)");
                bad.add(i);
            }
        }
    }

    /** Spessore dei muri dell'arena in pixel di mondo (anche quello disegnato). */
    public static final double WALL_THICKNESS_PX = 6;

    public double getArenaWidthPx()  { return arenaWidthPx; }
    public double getArenaHeightPx() { return arenaHeightPx; }

    /** Sovrapposizione minima segnalata, in pixel² (sotto: contatto o arrotondamento). */
    private static final double MIN_OVERLAP_PX2 = 1.0;

    /** Area di una regione (somma delle aree dei sotto-percorsi, curve approssimate). */
    private static double areaOf(Area area) {
        if (area.isEmpty()) return 0;
        double total = 0, sub = 0;
        double[] c = new double[6];
        double startX = 0, startY = 0, lastX = 0, lastY = 0;
        for (PathIterator it = area.getPathIterator(null, 0.1); !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO -> { sub = 0; startX = lastX = c[0]; startY = lastY = c[1]; }
                case PathIterator.SEG_LINETO -> { sub += lastX * c[1] - c[0] * lastY; lastX = c[0]; lastY = c[1]; }
                case PathIterator.SEG_CLOSE  -> { sub += lastX * startY - startX * lastY; total += Math.abs(sub) / 2; sub = 0;
                                                  lastX = startX; lastY = startY; }
                default -> {}
            }
        }
        return total;
    }

    private String describe(int i) {
        ObjectID id = obstacles.get(i).getID();
        String kind = id.getType() == jusePack.ArenaObjects.ArenaObjectType.OBSTACLE_OVAL ? "oval" : "rectangle";
        return "Obstacle #" + (i + 1) + " (" + colorName(id.getColor()) + " " + kind + " at " + fmt(id.getPos()) + ")";
    }

    private static String colorName(java.awt.Color c) {
        if (c == null) return "";
        java.awt.Color[] known = { java.awt.Color.WHITE, java.awt.Color.BLACK, java.awt.Color.RED, java.awt.Color.GREEN,
            java.awt.Color.BLUE, java.awt.Color.YELLOW, java.awt.Color.ORANGE, java.awt.Color.GRAY,
            java.awt.Color.CYAN, java.awt.Color.MAGENTA, java.awt.Color.PINK };
        String[] names = { "white", "black", "red", "green", "blue", "yellow", "orange", "gray", "cyan", "magenta", "pink" };
        for (int k = 0; k < known.length; k++) if (known[k].equals(c)) return names[k];
        return String.format(Locale.ROOT, "#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String fmt(Position p) {
        return String.format(Locale.ROOT, "(%.1f, %.1f)", p.x, p.y);
    }

}
