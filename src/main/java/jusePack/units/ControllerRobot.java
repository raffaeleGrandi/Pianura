package jusePack.units;

import java.awt.Color;

import jusePack.utils.Const;
import jusePack.utils.Position;

/**
 * Robot guidato da un {@link RobotController} chiamato ogni {@code controlPeriod}
 * secondi di tempo simulato.
 */
public class ControllerRobot extends RobotDummy {

	private final RobotController controller;
	private final double controlPeriod;

	public ControllerRobot(int id, Position pos, double sensRange, double controlPeriod,
	                       RobotController controller, Color color, int faultTime) {
		super(id, pos, sensRange, faultTime, color);
		if (!(controlPeriod > 0)) throw new IllegalArgumentException("controlPeriod must be > 0");
		this.controller = controller;
		this.controlPeriod = controlPeriod;
	}

	public ControllerRobot(int id, Position pos, double sensRange, double controlPeriod, RobotController controller) {
		this(id, pos, sensRange, controlPeriod, controller, Const.unitColor, -1);
	}

	public RobotController getController(){ return controller; }

	public double getControlPeriod(){ return controlPeriod; }

	@Override
	public void executeLoop() {
		controller.onStart(this);
		try {
			while (isActive()) {
				controller.control(this, getSimTime());
				sleepSim(controlPeriod);
			}
		} finally {
			controller.onStop(this);
		}
	}

}
