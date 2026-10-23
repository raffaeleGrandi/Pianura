package io.github.raffaelegrandi.pianura.units;

/**
 * Logica di controllo di un robot, separata dal robot stesso.
 *
 * Il simulatore chiama {@link #control(RobotUnit, double)} a intervalli regolari di
 * tempo simulato: il controllore legge i sensori dal robot e imposta i motori.
 * È l'alternativa "a callback" allo stile sequenziale di RobotDummy.executeLoop():
 *
 * <pre>
 *   addRobot(new ControllerRobot(1, new Position(5, 5, 0), 2, 0.1,
 *       (robot, t) -&gt; robot.forward(500)));
 * </pre>
 *
 * Lo stesso contratto vale per i controllori remoti (es. un programma esterno
 * collegato via rete): chi implementa questa interfaccia può inoltrare i sensori
 * e ricevere i comandi da un altro processo.
 */
@FunctionalInterface
public interface RobotController {

    /**
     * Un ciclo di controllo.
     * @param robot   il robot controllato (sensori in lettura, motori in scrittura)
     * @param simTime tempo simulato corrente in secondi
     */
    void control(RobotUnit robot, double simTime);

    /** Chiamato una volta prima del primo ciclo. */
    default void onStart(RobotUnit robot) {}

    /** Chiamato quando il robot si ferma (fine simulazione, guasto, errore). */
    default void onStop(RobotUnit robot) {}

}
