package io.github.raffaelegrandi.pianura.units;

import java.awt.Color;
import io.github.raffaelegrandi.pianura.arena.ObjectID;
import io.github.raffaelegrandi.pianura.arena.ArenaObjectType;
import io.github.raffaelegrandi.pianura.sim.SimClock;
import io.github.raffaelegrandi.pianura.sim.SimulationStoppedException;
import io.github.raffaelegrandi.pianura.units.collisions.SensorArray;
import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.utils.Position;

public abstract class RobotUnit implements Runnable{

	private Thread thread;
	private volatile SimClock clock;
	private volatile io.github.raffaelegrandi.pianura.comm.CommNetwork network;
	private final java.util.concurrent.ConcurrentLinkedQueue<io.github.raffaelegrandi.pianura.comm.Message> inbox = new java.util.concurrent.ConcurrentLinkedQueue<>();
	private long messageSeq = 0;                       // solo thread del robot
	private volatile double commRange = Double.NaN;    // NaN = raggio predefinito dello scenario

	private ObjectID unitID;
	private UnitObject unitObj;
	protected volatile boolean activeStatus = true;
	// Scritti dal thread del robot, letti dal thread di simulazione
	private volatile double vK = 0;
	private volatile double wK = 0;
	private volatile boolean collisionStatus = false;
	// strada percorsa da ciascuna ruota (celle, con segno): scritta dal motore, letta dalla vista
	private volatile double leftWheelTravel = 0, rightWheelTravel = 0;

	public RobotUnit(int unitIDnum, Position unitPos, double sensRange, Color unitColor){
		unitObj = new UnitObject(new ObjectID(ArenaObjectType.UNIT, unitColor, unitPos, Const.unitDim), sensRange);
		unitID = unitObj.getID();
		unitID.setIDnum(unitIDnum);
	}

	/***********************************************************************************************************/

	/**
	 * Attiva/disattiva il robot. Disattivato, il robot resta fermo come ostacolo e il
	 * suo thread termina alla prossima sleepSim().
	 */
	public void setActive(boolean status){
		activeStatus = status;
		if (!status && clock != null) clock.deregister(this);   // sveglia il thread se è in sleepSim
	}

	public boolean hasCollision(){ return collisionStatus; }

	/***********************************************************************************************************/

	public UnitObject getUnitObject(){ return unitObj; }

	public int getIDnum(){ return unitID.getIDnum(); }

	public Position getPosition(){ return unitObj.getExactPos(); }

	/***********************************************************************************************************/

	public int[] getAllSens(){ return unitObj.getIrSensors().getSensors();}

	public int getOneSen(int index){ return unitObj.getIrSensors().getSensorValue(index);}

	/***********************************************************************************************************/
	/*  Sensori ottici di pavimento (line-following)                                                           */
	/***********************************************************************************************************/

	/** Numero di sensori ottici (predefinito 2: indice 0 = sinistra, 1 = destra). */
	public int getFloorSensorCount(){ return unitObj.getFloorSensors().count(); }

	/** Luminosità del pavimento sotto il sensore i: 0 = nero, 255 = bianco. */
	public int getFloorGray(int i){ return unitObj.getFloorSensors().getGray(i); }

	/** Luminosità sotto tutti i sensori ottici. */
	public int[] getFloorGrays(){ return unitObj.getFloorSensors().getGrays(); }

	/** Colore del pavimento sotto il sensore i (media sull'area sensibile). */
	public Color getFloorColor(int i){ return unitObj.getFloorSensors().getColor(i); }

	/**
	 * Configura i sensori ottici: distanza dal centro come frazione del raggio del robot,
	 * raggio dell'area sensibile in pixel di mondo, angoli in gradi rispetto alla direzione
	 * di marcia (negativo = sinistra, positivo = destra). Esempio: {@code configureFloorSensors(0.97, 2, -20, 20)}.
	 */
	public void configureFloorSensors(double radiusFraction, double footprintPx, double... anglesDeg){
		unitObj.getFloorSensors().setLayout(new io.github.raffaelegrandi.pianura.units.sensors.FloorSensorArray.Layout(radiusFraction, footprintPx, anglesDeg));
	}

	/**********************************************************************************************************/

	public void setMotorSpeed(double wlk, double wrk){
		double lftSpeed = wlk/100;
		double rgtSpeed = wrk/100;
		vK = eq_Vk(lftSpeed, rgtSpeed);
		wK = eq_Wk(lftSpeed, rgtSpeed);
	}

	public void setRobotSpeed(double linSpeed, double angSpeed){
		vK = linSpeed;
		wK = angSpeed;
	}

	public boolean isActive(){ return activeStatus; }

	/** Velocità lineare comandata (celle/s). */
	public double getLinearSpeed(){ return vK; }

	/** Velocità angolare comandata (rad/s, positiva = oraria sullo schermo). */
	public double getAngularSpeed(){ return wK; }

	/**
	 * Strada percorsa dalla ruota sinistra (celle, con segno) dall'inizio della simulazione.
	 * Conta la rotazione della ruota: se il robot spinge contro un ostacolo la ruota gira
	 * comunque (slittamento). Base per l'odometria.
	 */
	public double getLeftWheelTravel(){ return leftWheelTravel; }

	/** Strada percorsa dalla ruota destra (celle, con segno). */
	public double getRightWheelTravel(){ return rightWheelTravel; }

	/***********************************************************************************************************/
	/*  Tempo simulato                                                                                         */
	/***********************************************************************************************************/

	/***********************************************************************************************************/
	/*  Communication between robots                                                                            */
	/***********************************************************************************************************/

	/** Connects the robot to the radio network (done by SimEngine). */
	public void attachNetwork(io.github.raffaelegrandi.pianura.comm.CommNetwork net){ this.network = net; }

	/**
	 * Sends a message to every robot in range (one hop). The payload must be JSON-like data:
	 * String, number, boolean, null, Map&lt;String,?&gt; or List&lt;?&gt; (numbers arrive as Double).
	 */
	public void broadcast(Object payload){ requireNetwork().submit(this, io.github.raffaelegrandi.pianura.comm.Message.BROADCAST, payload, messageSeq++); }

	/** Sends a message to robot {@code toId}: it arrives only if that robot is directly in range (one hop). */
	public void send(int toId, Object payload){
		if (toId < 0) throw new IllegalArgumentException("invalid receiver id " + toId);
		requireNetwork().submit(this, toId, payload, messageSeq++);
	}

	/** Messages received since the last call (oldest first); empty list if none. */
	public java.util.List<io.github.raffaelegrandi.pianura.comm.Message> receive(){
		java.util.List<io.github.raffaelegrandi.pianura.comm.Message> out = new java.util.ArrayList<>();
		for (io.github.raffaelegrandi.pianura.comm.Message m; (m = inbox.poll()) != null; ) out.add(m);
		return out;
	}

	/** True if there are messages waiting to be read with receive(). */
	public boolean hasMessages(){ return !inbox.isEmpty(); }

	/** Transmission range of this robot in cells; NaN restores the scenario default. */
	public void setCommRange(double cells){ commRange = cells; }

	/** Own transmission range in cells, or NaN if the scenario default is used. */
	public double getCommRange(){ return commRange; }

	/** Called by the network: puts a message in the inbox. Not meant for robot code. */
	public void deliverMessage(io.github.raffaelegrandi.pianura.comm.Message m){ inbox.add(m); }

	private io.github.raffaelegrandi.pianura.comm.CommNetwork requireNetwork(){
		io.github.raffaelegrandi.pianura.comm.CommNetwork n = network;
		if (n == null) throw new IllegalStateException("robot #" + getIDnum() + " is not part of a simulation: no radio network");
		return n;
	}

	/** Collega il robot all'orologio della simulazione (fatto da JuseManager/SimEngine). */
	public void attachClock(SimClock clock){ this.clock = clock; }

	/** Tempo simulato corrente in secondi (0 se il robot non è in una simulazione). */
	public double getSimTime(){ return clock != null ? clock.now() : 0; }

	/**
	 * Attende {@code seconds} di tempo SIMULATO. Da usare al posto di Thread.sleep()
	 * nel codice dei robot: così pausa, passo singolo e accelerazione funzionano,
	 * e la simulazione è riproducibile.
	 */
	public void sleepSim(double seconds){
		if (clock == null) {   // fuori da una simulazione (es. test): tempo reale
			try { Thread.sleep((long)(seconds * 1000)); }
			catch (InterruptedException e) { Thread.currentThread().interrupt(); }
			return;
		}
		clock.sleep(this, seconds);
		if (!activeStatus) throw new SimulationStoppedException();
	}

	/***********************************************************************************************************/

	public void start() {
		activeStatus = true;
		if (clock != null) clock.register(this, "Robot #" + getIDnum());
		thread = Thread.ofVirtual().name("robot-" + getIDnum()).start(this);
	}

	public void run(){
		try {
			while(activeStatus) executeLoop();
		} catch (SimulationStoppedException e) {
			// simulazione fermata o robot disattivato: uscita normale
		} catch (RuntimeException e) {
			System.err.println("Robot #" + getIDnum() + " terminated by an error: " + e);
			e.printStackTrace();
		} finally {
			activeStatus = false;
			if (clock != null) clock.deregister(this);
		}
	}

	public abstract void executeLoop();

	/**********************************	Moving Methods **********************************************************/

	/**
	 * Ruota sul posto di {@code angle} gradi (positivo = orario sullo schermo), a una velocità
	 * di Const.angleStep gradi ogni {@code tms} millisecondi. La rotazione avviene in tempo
	 * simulato tramite i motori, quindi rispetta fisica, pausa e accelerazione.
	 */
	public void rotateBy(int angle, int tms){
		if (angle == 0) return;
		double w = Math.toRadians(Const.angleStep) / (tms / 1000.0);   // rad/s
		double duration = Math.toRadians(Math.abs(angle)) / w;
		setRobotSpeed(0, Math.signum(angle) * w);
		sleepSim(duration);
		setRobotSpeed(0, 0);
		collisionStatus = false;
	}

	public void forward(double speed){ this.setMotorSpeed(Math.abs(speed), Math.abs(speed)); }

	public void backward(double speed){ this.setMotorSpeed(-Math.abs(speed), -Math.abs(speed)); }

	/***********************************************************************************************************/

	/**
	 * Avanza il robot di un passo rispettando i contatti (risposta "a scivolamento").
	 *
	 * Il corpo del robot è un disco, quindi la rotazione non può mai causare
	 * compenetrazione ed è sempre consentita. Della traslazione del centro si
	 * elimina solo la componente diretta verso ciascun contatto attivo (bump
	 * sensor): il robot non entra nell'ostacolo ma può scivolarci lungo, e
	 * può sempre allontanarsene. I motori NON vengono azzerati: come su un
	 * robot reale, è la logica del robot a decidere come reagire al bump.
	 *
	 * La regola precedente (fermo totale salvo rotazione pura sul posto)
	 * causava un deadlock con le strategie che sterzano su una ruota:
	 * la sterzata ha sempre una componente di avanzamento e veniva bloccata.
	 */
	public void evaluateMovement(double timeSample){
		// percorso delle ruote: girano secondo i comandi anche se il corpo è bloccato da un contatto (slittamento)
		double v = vK, w = wK, half = unitObj.wheelsDistance / 2;
		leftWheelTravel  += (v + w * half) * timeSample;
		rightWheelTravel += (v - w * half) * timeSample;

		Position cur  = unitObj.getExactPos();
		Position next = computeNext(cur, timeSample);
		double dx = next.x - cur.x;
		double dy = next.y - cur.y;

		boolean blocked = false;
		SensorArray bump = unitObj.getBumpSensors();
		if (!bump.isEmpty()) {
			int[] contacts = bump.getSensors();
			// Due passate: con più contatti (es. angolo tra due pareti) una sola
			// proiezione può reintrodurre una componente verso il primo contatto.
			for (int pass = 0; pass < 2; pass++) {
				for (int i = 0; i < contacts.length; i++) {
					if (contacts[i] == bump.getInitValue()) continue;
					double a  = cur.theta + bump.getSensorAngle(i);
					double nx = Math.cos(a), ny = Math.sin(a);
					double p  = dx * nx + dy * ny;
					if (p > 0) { dx -= p * nx; dy -= p * ny; blocked = true; }
				}
			}
		}
		collisionStatus = blocked;
		if (Const.debugEnabled && blocked) System.out.println("RU" + getIDnum() + "> motion constrained by contact");
		unitObj.setPos(new Position(cur.x + dx, cur.y + dy, next.theta));
	}

	/***********************************************************************************************************/

	public boolean evaluateRotation(){
		return (vK == 0) && (wK != 0);
	}

	/***********************************************************************************************************/

	public void stopUnit(){
		System.out.println("Executing stopUnit");
		setMotorSpeed(0,0);
	}

	/** Muove il robot ignorando i contatti. */
	public void moveUnit(double timeSample){
		unitObj.setPos(computeNext(unitObj.getExactPos(), timeSample));
	}

	/** Posa successiva secondo la cinematica differenziale (vK, wK letti una sola volta). */
	private Position computeNext(Position currPos, double timeSample){
		double v = vK, w = wK;
		if (Math.abs(w) < 0.001) return computeRungeKutta(currPos, v, 0, timeSample);
		return computeExact(currPos, v, w, timeSample);
	}

	/***********************************************************************************************************/

	public int getDirection(){
		int dir = (int) Math.signum(vK);
		return dir;
	}

	public boolean detectCollisions(){
		int dir = this.getDirection();
		if(Const.debugEnabled)System.out.println("Direction of unit #"+getIDnum()+" = "+dir);
		return unitObj.detectCollisions(dir);
	}

	/************************************************************************************************/

	private double eq_Vk(double wlk, double wrk){
		double res = unitObj.wheelRadius*(wlk+wrk)/2;
		return res;
	}

	private double eq_Wk(double wlk, double wrk){
		double res = (unitObj.wheelRadius*(wlk-wrk)/unitObj.wheelsDistance);
		if (res == 0) res=0.00000000001;
		return res;
	}

	/************************************************************************************************/

	private Position computeRungeKutta(Position currPos, double vK, double wK, double timeSample){
		double nextTheta = (currPos.theta + wK*timeSample)%(2*Math.PI);
		double nextPosX = currPos.x + vK*timeSample*Math.cos(currPos.theta + wK*timeSample/2);
		double nextPosY = currPos.y + vK*timeSample*Math.sin(currPos.theta + wK*timeSample/2);
		Position newPos = new Position(nextPosX,nextPosY,nextTheta);
		return newPos;
	}

	/************************************************************************************************/

	private Position computeExact(Position currPos, double vK, double wK, double timeSample){
		double nextTheta = (currPos.theta + wK*timeSample)%(2*Math.PI);
		double nextPosX = currPos.x + (vK/wK)*(Math.sin(nextTheta) - Math.sin(currPos.theta));
		double nextPosY = currPos.y - (vK/wK)*(Math.cos(nextTheta) - Math.cos(currPos.theta));
		Position newPos = new Position(nextPosX,nextPosY,nextTheta);
		return newPos;
	}

}
