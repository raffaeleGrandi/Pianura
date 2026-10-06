package jusePack.sim;

/**
 * Lanciata dentro il thread di un robot quando la simulazione viene fermata
 * mentre il robot è in attesa ({@code sleepSim}). Serve a far terminare in modo
 * pulito i cicli dei robot, anche quelli scritti come {@code while(true)}.
 * Viene intercettata da RobotUnit: il codice utente non deve gestirla.
 */
public class SimulationStoppedException extends RuntimeException {
    public SimulationStoppedException() {
        super("simulation stopped", null, false, false);
    }
}
