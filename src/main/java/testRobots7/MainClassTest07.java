package testRobots7;

import java.awt.Point;

import io.github.raffaelegrandi.pianura.Simulator;
import io.github.raffaelegrandi.pianura.remote.RemoteServer;

/**
 * Remote control example: start this, then run the Python client
 * {@code python3 clients/python/wander.py 1 2} and press Run (or Space).
 */
public class MainClassTest07 {

	public static void main(String[] args) throws Exception {
		RemoteServer server = RemoteServer.start(RemoteServer.DEFAULT_PORT, RemoteServer.Mode.LOCKSTEP);
		Simulator sim = new Simulator(new Point(30, 30), "JUSE - remote control", new ScenarioTest07(server));
		sim.attach(server);
		sim.printLogMsg(false, "Waiting for clients: python3 clients/python/wander.py 1 2  - then press Run");
		sim.startSimPaused();
	}

}
