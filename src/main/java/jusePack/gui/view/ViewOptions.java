package jusePack.gui.view;

/** Livelli di visualizzazione attivabili dal pannello laterale. Letti dal thread di disegno. */
public final class ViewOptions {
    /** Cerchio del campo dei sensori IR (come nella versione originale). */
    public volatile boolean sensorRange = true;
    /** Raggi dei 18 sensori con il punto colpito. */
    public volatile boolean sensorRays = false;
    /** Percorso seguito dai robot. */
    public volatile boolean trails = false;
    /** Numero del robot (etichetta a fumetto). */
    public volatile boolean ids = true;
    /**
     * Distanza dell'etichetta dal centro del robot, in raggi del robot, in alto a sinistra a 45°:
     * 0 = sopra il centro, 1 = sul bordo, 2 = staccata come un fumetto.
     */
    public volatile double labelDistanceFromCenter = 2.0;
    /** Sensori ottici di pavimento, colorati con il valore letto. */
    public volatile boolean floorSensors = true;
    /** Radio links between robots (color = connected component, dashed + arrow = one way) and messages in flight. */
    public volatile boolean commLinks = false;
    /** Transmission range of each robot. */
    public volatile boolean commRange = false;
    /** The view scrolls when the pointer rests near its border (like strategy games). */
    public volatile boolean edgeScroll = true;
    /** Griglia delle celle sopra il pavimento. */
    public volatile boolean grid = false;
}
