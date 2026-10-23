package io.github.raffaelegrandi.pianura.gui.panels;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

import io.github.raffaelegrandi.pianura.arena.ArenaObjectABS;
import io.github.raffaelegrandi.pianura.arena.ObjectID;
import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.gui.floor.FloorStyle;
import io.github.raffaelegrandi.pianura.units.collisions.OvalShape;
import io.github.raffaelegrandi.pianura.units.collisions.RectShape;
import io.github.raffaelegrandi.pianura.utils.Const;

/**
 * Sfondo statico dell'arena: pavimento + ostacoli, in pixel di mondo (celle x Const.cellSize).
 * Viene disegnato una volta in un'immagine; la vista (ArenaView) la scala e la sposta.
 * I muri dell'arena non sono qui: li disegna la vista, fuori dal pavimento.
 */
@SuppressWarnings("serial")
public class ArenaFloorPanel extends JPanel{

	private Graphics pgc;
	private List<ArenaObjectABS> obstVect;
	private final FloorStyle floor;
	private final int widthPx, heightPx;

	public ArenaFloorPanel(List<ArenaObjectABS> obstacles, FloorStyle floorStyle, int widthPx, int heightPx){
		obstVect = obstacles;
		floor = floorStyle != null ? floorStyle : FloorStyle.checkerboard();
		this.widthPx = widthPx;
		this.heightPx = heightPx;
		setDoubleBuffered(true);
		setSize(widthPx, heightPx);
	}

	/** Arena standard. */
	public ArenaFloorPanel(List<ArenaObjectABS> obstacles, FloorStyle floorStyle){
		this(obstacles, floorStyle, Const.arenaWidth, Const.arenaHeight);
	}

	public ArenaFloorPanel(List<ArenaObjectABS> obstacles){ this(obstacles, FloorStyle.checkerboard()); }

	/** Disegna pavimento e ostacoli in una nuova immagine delle dimensioni indicate (pixel di mondo). */
	public static BufferedImage renderBackground(List<? extends Obstacle> obstacles, FloorStyle floor, int widthPx, int heightPx){
		ArenaFloorPanel p = new ArenaFloorPanel(new ArrayList<ArenaObjectABS>(obstacles), floor, widthPx, heightPx);
		BufferedImage img = new BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		p.paintComponent(g);
		g.dispose();
		return img;
	}

	public void paintComponent(Graphics g){
		pgc = g;
		Graphics2D g2 = (Graphics2D) g.create();
		floor.paint(g2, widthPx, heightPx, Const.cellSize);
		g2.dispose();
		drawArenaObst();
	}

	/*****************************************************************************************************/

	private void drawArenaObst(){
		for (ArenaObjectABS arenaObj : obstVect) drawObstacle((Obstacle) arenaObj);
	}

	private void drawObstacle(Obstacle obst){
		Graphics2D g2 = (Graphics2D) pgc.create();
		paintObstacle(g2, obst, outlineOf(obst));
		g2.dispose();
	}

	/**
	 * Disegna un ostacolo usando lo stesso contorno delle collisioni
	 * (RectShape/OvalShape.outline()): grafica e fisica non possono divergere.
	 * Il bordo è tracciato all'interno della forma (clip + tratto di spessore doppio).
	 * Usato anche dalla vista, che disegna gli ostacoli come vettori a ogni frame.
	 */
	public static void paintObstacle(Graphics2D g, Obstacle obst, Shape shape){
		if (shape == null) return;
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g2.setColor(obst.getID().getColor());
		g2.fill(shape);
		Color border = obst.getBorderColor();
		if (obst.getBorderWidth() > 0 && !border.equals(obst.getID().getColor())) {
			g2.clip(shape);
			g2.setColor(border);
			g2.setStroke(new BasicStroke((float) (2 * obst.getBorderWidth()), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
			g2.draw(shape);
		}
		g2.dispose();
	}

	/**
	 * Solo pavimento, renderizzato a {@code scale} volte la risoluzione del mondo
	 * (es. 2 = due pixel di immagine per pixel di mondo): resta nitido quando la vista ingrandisce.
	 */
	public static BufferedImage renderFloor(FloorStyle floor, int widthPx, int heightPx, double scale){
		int w = (int) Math.round(widthPx * scale), h = (int) Math.round(heightPx * scale);
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		(floor != null ? floor : FloorStyle.checkerboard()).paint(g, w, h, Const.cellSize * scale);
		g.dispose();
		return img;
	}

	/** Contorno in pixel dell'ostacolo, identico a quello usato da CollisionDetector. */
	public static Shape outlineOf(Obstacle obst){
		ObjectID id = obst.getID();
		double cell = Const.cellSize;
		double cx = cell * id.getPos().x, cy = cell * id.getPos().y;
		double w  = cell * id.getWidth(), h = cell * id.getHeight();
		return switch (id.getType()) {
			case OBSTACLE_RECT -> new RectShape(cx, cy, w, h, id.getAngle()).outline();
			case OBSTACLE_OVAL -> new OvalShape(cx, cy, w, h, id.getAngle()).outline();
			default -> null;
		};
	}

}
