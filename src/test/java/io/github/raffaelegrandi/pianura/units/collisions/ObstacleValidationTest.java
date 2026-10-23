package io.github.raffaelegrandi.pianura.units.collisions;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Dimension;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.raffaelegrandi.pianura.ArenaObjects.ArenaObjectType;
import io.github.raffaelegrandi.pianura.ArenaObjects.Obstacle;
import io.github.raffaelegrandi.pianura.utils.Position;

class ObstacleValidationTest {

    private static Obstacle rect(double x, double y, int w, int h, double ang) {
        return new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.WHITE, new Position(x, y, ang), new Dimension(w, h));
    }

    private static Obstacle oval(double x, double y, int w, int h) {
        return new Obstacle(ArenaObjectType.OBSTACLE_OVAL, Color.WHITE, new Position(x, y, 0), new Dimension(w, h));
    }

    private static List<String> check(Obstacle... obs) {
        return new CollisionDetector(List.of(obs)).validateObstacles();
    }

    @Test
    void separatedObstaclesAreFine() {
        assertTrue(check(rect(10, 10, 4, 4, 0), rect(20, 10, 4, 4, 0), oval(30, 30, 4, 4)).isEmpty());
    }

    @Test
    void touchingEdgesAreNotOverlap() {
        // due quadrati 4x4 affiancati: lati coincidenti a x = 12
        assertTrue(check(rect(10, 10, 4, 4, 0), rect(14, 10, 4, 4, 0)).isEmpty());
    }

    @Test
    void overlappingRectanglesAreReportedWithArea() {
        List<String> p = check(rect(10, 10, 4, 4, 0), rect(12, 10, 4, 4, 0));  // sovrapposizione 2x4 = 8 celle²
        assertEquals(1, p.size(), p.toString());
        assertTrue(p.get(0).contains("#1") && p.get(0).contains("#2"));
        assertTrue(p.get(0).contains("8.00"), p.get(0));
    }

    @Test
    void rotatedRectangleOverlapUsesSameRotationAsCollisions() {
        // rettangolo lungo 10x1 ruotato di +45° (orario sullo schermo): il suo estremo
        // finisce in basso a destra del centro, dove c'è un piccolo ovale
        double d = 5 / Math.sqrt(2) - 0.5;
        assertEquals(1, check(rect(20, 20, 10, 1, Math.PI / 4), oval(20 + d, 20 + d, 1, 1)).size());
        // nella direzione opposta (antioraria) non deve esserci nulla
        assertTrue(check(rect(20, 20, 10, 1, Math.PI / 4), oval(20 + d, 20 - d, 1, 1)).isEmpty());
    }

    @Test
    void ovalOverlap() {
        assertEquals(1, check(oval(10, 10, 4, 4), oval(13, 10, 4, 4)).size());
        assertTrue(check(oval(10, 10, 4, 4), oval(14.1, 10, 4, 4)).isEmpty());
    }

    @Test
    void obstacleOutsideArenaIsReported() {
        List<String> p = check(rect(1, 20, 4, 4, 0));   // da x = -1 a 3: 1x4 celle fuori
        assertEquals(1, p.size(), p.toString());
        assertTrue(p.get(0).contains("extends outside the arena"));
        assertTrue(p.get(0).contains("4.00"), p.get(0));
    }

    @Test
    void validationUsesConfiguredArenaSize() {
        Obstacle far = rect(70, 45, 4, 4, 0);   // dentro un'arena 80x50, fuori da una 40x40
        assertTrue(new CollisionDetector(List.of(far), 80 * 15, 50 * 15).validateObstacles().isEmpty());
        assertEquals(1, new CollisionDetector(List.of(far)).validateObstacles().size());
    }

    @Test
    void scenarioTest04OverlapsAreFound() {
        List<String> p = new CollisionDetector(new testRobots4.ScenarioTest04().getObstacles()).validateObstacles();
        p.forEach(System.out::println);
        assertTrue(p.stream().anyMatch(s -> s.contains("white rectangle") && s.contains("red rectangle")
            && s.contains("overlap")), p.toString());
    }

}
