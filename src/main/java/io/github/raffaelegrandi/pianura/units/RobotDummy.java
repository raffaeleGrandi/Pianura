package io.github.raffaelegrandi.pianura.units;

import java.awt.Color;

import io.github.raffaelegrandi.pianura.utils.Const;
import io.github.raffaelegrandi.pianura.utils.Position;

/**
 * Base per i robot definiti dall'utente: implementare {@link #executeLoop()}.
 *
 * Il guasto simulato ({@code faultTime}) è ora espresso in tempo SIMULATO:
 * al raggiungimento dell'istante il motore disattiva il robot, che resta
 * nell'arena come ostacolo fermo.
 */
public abstract class RobotDummy extends RobotUnit {

	/** Istante del guasto in secondi di tempo simulato, negativo = nessun guasto. */
	private final double faultTimeSec;

	/**
	 * @param faultTime istante del guasto simulato in millisecondi (tempo simulato), negativo = nessuno
	 */
	public RobotDummy(int unitIDnum, Position unitPos, double sensRange, int faultTime, Color unitColor){
		super(unitIDnum, unitPos, sensRange, unitColor);
		faultTimeSec = faultTime >= 0 ? faultTime / 1000.0 : -1;
	}

	public RobotDummy(int unitIDnum, Position unitPos, double sensRange){
		this(unitIDnum, unitPos, sensRange, -1, Const.unitColor);
	}

	/** Istante del guasto simulato in secondi, negativo se assente. */
	public double getFaultTime(){ return faultTimeSec; }

	public abstract void executeLoop();

}
