package io.github.raffaelegrandi.pianura.units;

import java.awt.geom.Point2D;

import io.github.raffaelegrandi.pianura.ArenaObjects.ArenaObjectABS;
import io.github.raffaelegrandi.pianura.ArenaObjects.ObjectID;
import io.github.raffaelegrandi.pianura.units.collisions.*;
import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.utils.Position;

public class UnitObject extends ArenaObjectABS {

	private CollisionHandler collHand;
	private SensorArray bumpSensors;
	private SensorArray irSensors;
	private final io.github.raffaelegrandi.pianura.units.sensors.FloorSensorArray floorSensors = new io.github.raffaelegrandi.pianura.units.sensors.FloorSensorArray();
	private double sensorRange;
	private Position oldPos;

	double wheelRadius = 0.25; // cells
	double wheelsDistance = 2; // cells

	public UnitObject(ObjectID unitIDref, double sensRange){
		super(unitIDref);
		bumpSensors = new SensorArray();
		irSensors = new SensorArray(255);
		collHand = new CollisionHandler(bumpSensors);
		sensorRange = sensRange < Const.sensorsCheckingRangeMin ? Const.sensorsCheckingRangeMin : sensRange;
		oldPos = this.getExactPos();
	}

	public ObjectID getObjID(){ return this.objectID;}

	public int getIDnum(){return objectID.getIDnum();}

	public SensorArray getBumpSensors(){ return bumpSensors; }

	public SensorArray getIrSensors(){ return irSensors; }

	public double getSensorRange(){ return sensorRange; }

	/** Sensori ottici rivolti verso il pavimento. */
	public io.github.raffaelegrandi.pianura.units.sensors.FloorSensorArray getFloorSensors(){ return floorSensors; }

	public void setPos(Position newPos){ objectID.setPos(newPos);}

	public void setLoc(Point2D newLoc){ objectID.setLoc(new Point2D.Double(newLoc.getX(),newLoc.getY())); }

	public Position getExactPos(){return objectID.getPos();}

	public Position getOldPos(){ return oldPos;}

	public double getExactAngle(){return objectID.getAngle();}

	/***********************************************************************************************************/

	public boolean hasValidDisplacement(){
		Position currPos = this.getExactPos();
		if ((currPos.distance(oldPos) * Const.cellSize) >= 1) {
			oldPos = currPos;
			return true;
		}
		else return false;
	}

	public void resetToOldPosition(){setPos(oldPos);}

	/***********************************************************************************************************/

	public boolean detectCollisions(int dir){
		boolean collisionDetect = false;
		if (dir != 0){
			collisionDetect = collHand.detectCollisions(dir);
		}
		return collisionDetect;
	}

}
