package testRobots6;

import jusePack.gui.floor.FloorStyle;
import jusePack.structures.ScenarioManager;
import jusePack.units.ControllerRobot;
import jusePack.utils.Position;

/**
 * Banco di prova per il line-following: pista sul pavimento e un robot posato sulla linea.
 *
 * Il robot ha due sensori ottici sotto il muso (indice 0 = sinistra, 1 = destra) che leggono
 * la luminosità del pavimento: 0 = nero (linea), 255 = bianco. L'algoritmo è da scrivere:
 * il controllore qui sotto è volutamente vuoto e il robot resta fermo.
 */
public class ScenarioTest06 extends ScenarioManager {

	public ScenarioTest06(){
		setArenaSize(40, 30);
		setFloor(FloorStyle.track());

		// il lato superiore della pista è a 4 celle dal bordo: il robot parte lì, rivolto verso destra
		addRobot(new ControllerRobot(1, new Position(16, 4, 0), 2, 0.05, (robot, t) -> {
			int left  = robot.getFloorGray(0);   // 0 = nero, 255 = bianco
			int right = robot.getFloorGray(1);
			// TODO: algoritmo di line-following, es. un controllo proporzionale sull'errore (left - right)
			//       robot.setMotorSpeed(sinistra, destra);
			robot.setMotorSpeed(0, 0);
		}));
	}

}
