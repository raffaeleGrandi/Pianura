package jusePack.gui.panels;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.List;

import org.junit.jupiter.api.Test;

import jusePack.ArenaObjects.ArenaObjectABS;
import jusePack.ArenaObjects.ArenaObjectType;
import jusePack.ArenaObjects.Obstacle;
import jusePack.gui.floor.FloorStyle;
import jusePack.units.collisions.RectShape;
import jusePack.utils.Const;
import jusePack.utils.Position;

class ObstacleBorderTest {

    private static final Color FLOOR = new Color(30, 160, 60);

    /** Quadrato 4x4 celle centrato in (20,20): in pixel da 270 a 330. */
    private static Obstacle square(Color fill) {
        return new Obstacle(ArenaObjectType.OBSTACLE_RECT, fill, new Position(20, 20, 0), new Dimension(4, 4));
    }

    private static BufferedImage render(Obstacle o) {
        ArenaFloorPanel p = new ArenaFloorPanel(List.<ArenaObjectABS>of(o), FloorStyle.plain(FLOOR));
        return Const.getPanelImage(p);
    }

    private static Color px(BufferedImage img, int x, int y) { return new Color(img.getRGB(x, y)); }

    @Test
    void customBorderIsDrawnInsideTheShape() {
        BufferedImage img = render(square(Color.WHITE).withBorder(Color.BLACK, 2));
        assertEquals(Color.WHITE, px(img, 300, 300), "centro");
        assertEquals(Color.BLACK, px(img, 271, 300), "bordo interno sinistro");
        assertEquals(Color.BLACK, px(img, 300, 328), "bordo interno inferiore");
        assertEquals(FLOOR, px(img, 267, 300), "fuori dalla forma: il bordo non deve sporgere");
    }

    @Test
    void sameColorMeansNoBorder() {
        BufferedImage img = render(square(Color.WHITE).withBorder(Color.WHITE));
        assertEquals(Color.WHITE, px(img, 271, 300));
    }

    @Test
    void defaultBorderIsDarkerShadeOfFill() {
        Obstacle o = square(Color.WHITE);
        assertEquals(new Color(140, 140, 140), o.getBorderColor());
        // spessore predefinito 1.5 px: il pixel 270 è interamente nel bordo (il 271 è sfumato dall'antialiasing)
        assertEquals(o.getBorderColor(), px(render(o), 300, 270));
        // su colori molto scuri il bordo automatico è più chiaro, per restare visibile
        Color dark = Obstacle.autoBorder(Color.BLACK);
        assertTrue(dark.getRed() > 0);
    }

    @Test
    void drawingUsesSameOutlineAsCollisions() {
        Obstacle o = new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.BLUE, new Position(20, 20, 0.5), new Dimension(6, 2));
        RectShape collision = new RectShape(300, 300, 90, 30, 0.5);
        assertEquals(collision.outline().getBounds2D(), ArenaFloorPanel.outlineOf(o).getBounds2D());
    }

}
