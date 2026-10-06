package jusePack.gui.view;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import jusePack.ArenaObjects.ArenaObjectType;
import jusePack.ArenaObjects.Obstacle;
import jusePack.units.RobotDummy;
import jusePack.units.UnitObject;
import jusePack.units.collisions.CollisionDetector;
import jusePack.utils.Const;
import jusePack.utils.Position;

/** Trascinamento di ostacoli e robot nella vista, con eventi del mouse sintetici (senza schermo). */
class SceneEditingTest {

    private static final double C = Const.cellSize;

    private static class Fixture {
        final List<Obstacle> obstacles = new ArrayList<>();
        final List<RobotDummy> robots = new ArrayList<>();
        final CollisionDetector detector;
        final ArenaView view;
        boolean editable = true;
        int changes, finishedChanges;
        CollisionDetector.Validation last;

        Fixture() {
            obstacles.add(new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.BLUE, new Position(10, 10, 0), new Dimension(4, 2)));
            obstacles.add(new Obstacle(ArenaObjectType.OBSTACLE_OVAL, Color.WHITE, new Position(30, 30, 0), new Dimension(4, 4)));
            robots.add(new RobotDummy(1, new Position(20, 20, 0), 2) { @Override public void executeLoop() {} });
            detector = new CollisionDetector(obstacles);
            view = new ArenaView(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), 1, Const.arenaWidth, Const.arenaHeight,
                obstacles, robots, new ViewOptions());
            view.setSize(612, 612);
            view.fitToWindow();
            view.setSceneEditor(new ArenaView.SceneEditor() {
                @Override public boolean canEdit() { return editable; }
                @Override public CollisionDetector.Validation changed(boolean finished) {
                    changes++;
                    if (finished) finishedChanges++;
                    detector.rebuild();
                    List<UnitObject> b = new ArrayList<>();
                    for (RobotDummy r : robots) b.add(r.getUnitObject());
                    last = detector.validate(b);
                    return last;
                }
            });
        }

        Point2D screen(double xCells, double yCells) { return view.getTransform().toScreen(xCells * C, yCells * C); }

        void drag(Point2D from, Point2D to, int modifiers) {
            mouse(MouseEvent.MOUSE_PRESSED, from, modifiers | InputEvent.BUTTON1_DOWN_MASK, 1);
            Point2D mid = new Point2D.Double((from.getX() + to.getX()) / 2, (from.getY() + to.getY()) / 2);
            mouse(MouseEvent.MOUSE_DRAGGED, mid, modifiers | InputEvent.BUTTON1_DOWN_MASK, 0);
            mouse(MouseEvent.MOUSE_DRAGGED, to, modifiers | InputEvent.BUTTON1_DOWN_MASK, 0);
            mouse(MouseEvent.MOUSE_RELEASED, to, modifiers, 1);
        }

        void mouse(int id, Point2D p, int modifiers, int clicks) {
            view.dispatchEvent(new MouseEvent(view, id, System.currentTimeMillis(), modifiers,
                (int) Math.round(p.getX()), (int) Math.round(p.getY()), clicks, false,
                id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1));
        }
    }

    @Test
    void draggingAnObstacleMovesItWhenEditable() {
        Fixture f = new Fixture();
        f.drag(f.screen(10, 10), f.screen(16, 12), 0);
        Position p = f.obstacles.get(0).getID().getPos();
        assertEquals(16, p.x, 0.15);
        assertEquals(12, p.y, 0.15);
        assertTrue(f.changes >= 2, "validazione dal vivo durante il trascinamento");
        assertEquals(1, f.finishedChanges, "esito finale al rilascio");
        assertEquals(0, f.view.getSelectedObstacle());
    }

    @Test
    void ctrlSnapsToHalfCell() {
        Fixture f = new Fixture();
        f.drag(f.screen(10, 10), f.screen(14.13, 17.31), InputEvent.CTRL_DOWN_MASK);
        Position p = f.obstacles.get(0).getID().getPos();
        assertEquals(0, (p.x * 2) % 1, 1e-9, "x non agganciato: " + p.x);
        assertEquals(0, (p.y * 2) % 1, 1e-9, "y non agganciato: " + p.y);
    }

    @Test
    void draggingARobotMovesItKeepingHeading() {
        Fixture f = new Fixture();
        f.robots.get(0).getUnitObject().setPos(new Position(20, 20, 1.0));
        f.drag(f.screen(20, 20), f.screen(25, 22), 0);
        Position p = f.robots.get(0).getPosition();
        assertEquals(25, p.x, 0.15);
        assertEquals(22, p.y, 0.15);
        assertEquals(1.0, p.theta, 1e-9);
    }

    @Test
    void notEditableMeansDragPansTheView() {
        Fixture f = new Fixture();
        f.editable = false;
        double offBefore = f.view.getTransform().getOffsetX();
        f.drag(f.screen(10, 10), f.screen(16, 10), 0);
        assertEquals(10, f.obstacles.get(0).getID().getPos().x, 1e-9, "l'ostacolo non deve muoversi");
        assertNotEquals(offBefore, f.view.getTransform().getOffsetX(), "la vista doveva spostarsi");
        assertEquals(0, f.changes);
    }

    private static void click(Fixture f, Point2D p) {
        f.mouse(MouseEvent.MOUSE_PRESSED, p, InputEvent.BUTTON1_DOWN_MASK, 1);
        f.mouse(MouseEvent.MOUSE_RELEASED, p, 0, 1);
    }

    @Test
    void edgeHandleResizesKeepingOppositeSideFixed() {
        Fixture f = new Fixture();                       // rettangolo 4x2 centrato in (10,10): x da 8 a 12
        click(f, f.screen(10, 10));
        assertEquals(0, f.view.getSelectedObstacle());
        f.drag(f.screen(12, 10), f.screen(15, 10), 0);   // maniglia destra trascinata a x = 15
        var id = f.obstacles.get(0).getID();
        assertEquals(7, id.getWidth(), 0.1);            // da 8 a 15
        assertEquals(2, id.getHeight(), 1e-9);
        assertEquals(11.5, id.getPos().x, 0.05);        // lato sinistro fermo a x = 8
        assertEquals(10, id.getPos().y, 1e-9);
        assertEquals(1, f.finishedChanges);
    }

    @Test
    void cornerHandleOnRotatedObstacleWorksInLocalFrame() {
        Fixture f = new Fixture();
        var id = f.obstacles.get(0).getID();
        id.setPos(new Position(10, 10, Math.PI / 2));    // ruotato di 90°: asse x locale verso il basso
        f.detector.rebuild();
        click(f, f.screen(10, 10));
        // vertice locale (+2, +1) → nel mondo (10 - 1, 10 + 2) = (9, 12); lo porto a (8, 13)
        f.drag(f.screen(9, 12), f.screen(8, 13), 0);
        assertEquals(5, id.getWidth(), 0.1);             // asse x locale: da -2 (fermo) a +3
        assertEquals(3, id.getHeight(), 0.1);            // asse y locale: da -1 (fermo) a +2 (verso -x nel mondo)
        // il vertice opposto (locale -2,-1 → mondo 11, 8) non si è mosso
        double cx = id.getPos().x, cy = id.getPos().y, hw = id.getWidth() / 2, hh = id.getHeight() / 2;
        assertEquals(11, cx + hh, 0.1);
        assertEquals(8, cy - hw, 0.1);
    }

    @Test
    void resizeHasMinimumAndSnap() {
        Fixture f = new Fixture();
        var id = f.obstacles.get(0).getID();
        click(f, f.screen(10, 10));
        f.drag(f.screen(12, 10), f.screen(2, 10), 0);    // oltre il lato opposto
        assertEquals(0.5, id.getWidth(), 1e-9);
        f.drag(f.screen(8.5, 10), f.screen(11.37, 10), InputEvent.CTRL_DOWN_MASK);
        assertEquals(0, (id.getWidth() * 2) % 1, 1e-9, "larghezza non agganciata: " + id.getWidth());
    }

    @Test
    void movingOntoAnotherObstacleIsFlaggedAndMovingAwayClearsIt() {
        Fixture f = new Fixture();
        f.drag(f.screen(10, 10), f.screen(30, 30), 0);          // sopra l'ovale
        assertFalse(f.last.isOk());
        assertTrue(f.last.badObstacles().containsAll(List.of(0, 1)));
        f.drag(f.screen(30, 30), f.screen(8, 30), 0);            // via
        assertTrue(f.last.isOk(), f.last.messages().toString());
    }

}
