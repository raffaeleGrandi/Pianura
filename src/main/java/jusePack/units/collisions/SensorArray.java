package jusePack.units.collisions;

import java.awt.geom.Point2D;
import java.util.Arrays;

import jusePack.utils.Const;

/**
 * Array di sensori radiali di un robot.
 *
 * Thread-safety: i valori vengono scritti dal thread di simulazione
 * (SimEngine) e letti dal thread del robot. Per evitare che il robot
 * legga uno stato a metà aggiornamento (es. tutti i valori appena resettati),
 * i valori sono contenuti in uno snapshot immutabile pubblicato con un
 * singolo assegnamento volatile: chi legge vede sempre una lettura completa
 * e coerente dell'ultimo ciclo.
 */
public class SensorArray {

	private record Snapshot(int[] values, Point2D[] points, int activeCount) {}

	private final int arrayLength = Const.sensorCount;
	private final double[] sensorAngleArray;
	private final int initSensValue;
	private volatile Snapshot snapshot;

	public SensorArray(int initVal){
		initSensValue = initVal;
		sensorAngleArray = new double[arrayLength];
		double sensorStepAngle = 360.0 / arrayLength; // 20 gradi
		for (int i = 0; i < arrayLength; i++) {
			sensorAngleArray[i] = Math.toRadians(sensorStepAngle * i);
		}
		resetSensors();
	}

	public SensorArray(){ this(0); }

	/***********************************************************************************************/

	/** Valore "a riposo" del sensore (0 per i bump, 255 per gli IR). */
	public int getInitValue(){ return initSensValue; }

	/**
	 * Pubblica atomicamente un nuovo set di letture.
	 * Gli array vengono copiati: il chiamante può riusarli.
	 * Un sensore è considerato attivo se il suo valore differisce dal valore di riposo.
	 */
	public void publish(int[] values, Point2D[] points){
		int[] v = Arrays.copyOf(values, arrayLength);
		Point2D[] p = Arrays.copyOf(points, arrayLength);
		int count = 0;
		for (int x : v) if (x != initSensValue) count++;
		snapshot = new Snapshot(v, p, count);
	}

	public void resetSensors(){
		int[] v = new int[arrayLength];
		Arrays.fill(v, initSensValue);
		snapshot = new Snapshot(v, new Point2D[arrayLength], 0);
	}

	/* Modifiche puntuali (copy-on-write sullo snapshot). Il simulatore usa publish();
	 * questi metodi restano per test e per codice esistente. */

	public synchronized void setSensorValue(int sensNum, int sensValue){
		Snapshot s = snapshot;
		int[] v = s.values().clone();
		v[sensNum] = sensValue;
		publish(v, s.points());
	}

	public synchronized void resetSensorValue(int sensNum){ setSensorValue(sensNum, initSensValue); }

	public synchronized void setCheckingPoint(int sensNum, Point2D cPoint){
		Snapshot s = snapshot;
		Point2D[] p = s.points().clone();
		p[sensNum] = cPoint;
		publish(s.values(), p);
	}

	/***********************************************************************************************/

	public int getSensorValue(int sensNum){ return snapshot.values()[sensNum]; }

	/** Restituisce una copia delle letture correnti. */
	public int[] getSensors(){ return snapshot.values().clone(); }

	public double getSensorAngle(int sensNum){ return sensorAngleArray[sensNum]; }

	public Point2D getCheckPoint(int sensNum){ return snapshot.points()[sensNum]; }

	public int length(){ return arrayLength; }

	public int collisionNum(){ return snapshot.activeCount(); }

	public boolean isFull(){ return collisionNum() == length(); }

	public boolean isEmpty(){ return collisionNum() == 0; }

	public String toString(){
		Snapshot s = snapshot;
		StringBuilder sb = new StringBuilder("\n");
		for (int i = 0; i < arrayLength; i++){
			sb.append("sensor# ").append(i)
			  .append(", value: ").append(s.values()[i])
			  .append(", relativeCP: ").append(s.points()[i]).append('\n');
		}
		return sb.toString();
	}

}
