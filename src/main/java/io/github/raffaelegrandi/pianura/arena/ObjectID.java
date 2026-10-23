package io.github.raffaelegrandi.pianura.arena;
	
import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Point2D;

import io.github.raffaelegrandi.pianura.utils.Position;

public class ObjectID {
	
	private int objIDnum;
	private ArenaObjectType objType;
	private Position objPos;
	private volatile double width, height;   // in celle, solo per gli ostacoli
	private Color objColor;
	
	
	public ObjectID(ArenaObjectType objectType, Color objectColor, Position objectPosition, Dimension objectDimension){
		objType = objectType;
		objColor = objectColor;
		objPos = objectPosition;
		if (objectDimension != null) { width = objectDimension.width; height = objectDimension.height; }
	}

	/** Ostacolo con dimensioni non intere (in celle). */
	public ObjectID(ArenaObjectType objectType, Color objectColor, Position objectPosition, double widthCells, double heightCells){
		this(objectType, objectColor, objectPosition, (Dimension) null);
		width = widthCells;
		height = heightCells;
	}
	
	public synchronized int getIDnum(){return objIDnum;}
	
	public void setIDnum(int objIDnumber){objIDnum = objIDnumber;}
	
	public synchronized Position getPos(){return objPos;}
	
	public synchronized void setPos(Position newPos){objPos = newPos;}

	public synchronized void setLoc(Point2D newLoc){objPos.x = newLoc.getX(); objPos.y = newLoc.getY(); }
	
	public synchronized double getAngle(){return objPos.theta;} //radians
	
	public synchronized void setAngle(double newAngle){ objPos.theta = newAngle%(2*Math.PI); }
	
	public ArenaObjectType getType(){return objType;}
	
	public Color getColor(){return objColor;}
	
	/** Dimensioni arrotondate all'intero (compatibilità): per i valori esatti usare getWidth()/getHeight(). */
	public Dimension getDim(){ return new Dimension((int) Math.round(width), (int) Math.round(height)); }

	/** Larghezza in celle (asse x locale dell'ostacolo). */
	public double getWidth(){ return width; }

	/** Altezza in celle (asse y locale dell'ostacolo). */
	public double getHeight(){ return height; }

	/** Nuove dimensioni in celle (es. dal ridimensionamento con il mouse). */
	public synchronized void setSize(double widthCells, double heightCells){ width = widthCells; height = heightCells; }
	
}//end class
