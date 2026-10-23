package testRobots8;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import io.github.raffaelegrandi.pianura.comm.Message;
import io.github.raffaelegrandi.pianura.units.RobotController;
import io.github.raffaelegrandi.pianura.units.RobotUnit;

/**
 * Example controller: wanders around avoiding obstacles and, once per second, broadcasts a
 * beacon with its id. Neighbours are discovered only from the beacons heard (no oracle):
 * a neighbour is forgotten if it has not been heard for 2.5 s.
 */
public class BeaconWanderer implements RobotController {

	private static final int[] FRONT = { 0, 1, 2, 16, 17 };
	private static final int NEAR = 18;

	private final Random rnd;
	private final Map<Integer, Double> lastHeard = new HashMap<>();   // id del vicino → ultimo beacon ricevuto
	private double nextBeacon;
	private int turning = 0;
	private double turnDir = 1;

	public BeaconWanderer(long seed) {
		rnd = new Random(seed);
		nextBeacon = rnd.nextDouble();            // i beacon non partono tutti nello stesso istante
	}

	/** Neighbours currently known from their beacons. */
	public synchronized int knownNeighbours() { return lastHeard.size(); }

	@Override
	public synchronized void control(RobotUnit r, double t) {
		for (Message m : r.receive()) lastHeard.put(m.from(), t);
		lastHeard.values().removeIf(heard -> t - heard > 2.5);

		if (t >= nextBeacon) {
			r.broadcast(Map.of("beacon", r.getIDnum(), "neighbours", lastHeard.size()));
			nextBeacon = t + 1.0;
		}

		int[] ir = r.getAllSens();
		int front = Integer.MAX_VALUE;
		for (int i : FRONT) front = Math.min(front, ir[i]);
		if (turning > 0) {
			turning--;
			r.setRobotSpeed(0, 2.0 * turnDir);
		} else if (front < NEAR || r.hasCollision()) {
			int left = ir[13] + ir[14] + ir[15] + ir[16] + ir[17], right = ir[1] + ir[2] + ir[3] + ir[4] + ir[5];
			turnDir = left > right ? -1 : 1;
			turning = 4 + rnd.nextInt(7);
			r.setRobotSpeed(0, 2.0 * turnDir);
		} else {
			r.setRobotSpeed(1.2, (rnd.nextDouble() - 0.5) * 0.6);
		}
	}

}
