package io.github.raffaelegrandi.pianura.arena;

import java.awt.Color;
import java.awt.Dimension;

import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * Ostacolo statico (rettangolo ruotato o ovale), posizione = centro, in celle.
 *
 * Bordo: per default è una tonalità più scura del colore di riempimento, così
 * anche un ostacolo chiaro resta visibile su un pavimento chiaro. Per un colore
 * diverso usare {@link #withBorder(Color)}; per non avere bordo, passare lo
 * stesso colore dell'ostacolo. Il bordo è disegnato all'interno della forma:
 * l'ostacolo visibile coincide con quello usato per le collisioni.
 *
 * <pre>
 *   addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.WHITE,
 *       new Position(20, 35, 0), new Dimension(5, 8)).withBorder(Color.BLACK, 2));
 * </pre>
 */
public class Obstacle extends ArenaObjectABS {

	/** Spessore predefinito del bordo in pixel. */
	public static final double DEFAULT_BORDER_WIDTH = 1.5;

	private Color borderColor;          // null = automatico (più scuro del riempimento)
	private double borderWidth = DEFAULT_BORDER_WIDTH;

	public Obstacle(ArenaObjectType obstType, Color color, Position obstPos, Dimension obstDim ){
		super(new ObjectID(obstType, color, obstPos, obstDim));
	}

	/** Ostacolo con dimensioni non intere: larghezza e altezza in celle. */
	public Obstacle(ArenaObjectType obstType, Color color, Position obstPos, double widthCells, double heightCells){
		super(new ObjectID(obstType, color, obstPos, widthCells, heightCells));
	}

	public Obstacle(ArenaObjectType obstType, Color color, Position obstPos, Dimension obstDim, Color borderColor){
		this(obstType, color, obstPos, obstDim);
		this.borderColor = borderColor;
	}

	/** Imposta il colore del bordo (stesso colore dell'ostacolo = nessun bordo visibile). */
	public Obstacle withBorder(Color color){
		this.borderColor = color;
		return this;
	}

	/** Imposta colore e spessore del bordo in pixel. */
	public Obstacle withBorder(Color color, double widthPx){
		this.borderColor = color;
		this.borderWidth = Math.max(0, widthPx);
		return this;
	}

	/** Colore effettivo del bordo (quello impostato, o quello automatico). */
	public Color getBorderColor(){
		return borderColor != null ? borderColor : autoBorder(objectID.getColor());
	}

	public double getBorderWidth(){ return borderWidth; }

	/** Bordo automatico: il colore di riempimento scurito; per colori molto scuri, schiarito. */
	public static Color autoBorder(Color fill){
		if (fill == null) return Color.DARK_GRAY;
		double lum = 0.299 * fill.getRed() + 0.587 * fill.getGreen() + 0.114 * fill.getBlue();
		if (lum < 60) return new Color(
			Math.min(255, fill.getRed() + 90), Math.min(255, fill.getGreen() + 90), Math.min(255, fill.getBlue() + 90));
		return new Color((int) (fill.getRed() * 0.55), (int) (fill.getGreen() * 0.55), (int) (fill.getBlue() * 0.55));
	}

}//end class
