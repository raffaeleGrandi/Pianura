package io.github.raffaelegrandi.pianura.gui.floor;

import java.awt.Color;
import java.awt.Graphics2D;

import io.github.raffaelegrandi.pianura.utils.Const;

/**
 * Aspetto del pavimento dell'arena. È puramente grafico: i sensori dei robot
 * lavorano sulla geometria degli ostacoli e non vedono il pavimento.
 *
 * Il pavimento viene disegnato a riquadri, quando servono (vedi {@link #paintRegion}),
 * e i riquadri sono conservati: anche un'arena molto grande non pesa sul frame rate.
 *
 * Uso in uno scenario:
 * <pre>
 *   setFloor(FloorStyle.checkerboard());                          // predefinito
 *   setFloor(FloorStyle.grid(Color.WHITE, Color.LIGHT_GRAY, 1));  // griglia a 1 cella
 *   setFloor(FloorStyle.plain(new Color(230, 230, 220)));
 *   setFloor(FloorStyle.randomTiles());                           // piastrelle
 *   setFloor(FloorStyle.voronoiTiles());                          // lastre di pietra
 *   setFloor(FloorStyle.image("floors/laboratorio.png", ImageFloor.Mode.FIT));
 * </pre>
 */
public interface FloorStyle {

    /**
     * Disegna il pavimento nell'area {@code [0, width] x [0, height]} (pixel).
     * @param cellSizePx dimensione di una cella in pixel (per i motivi legati alla griglia)
     */
    void paint(Graphics2D g, int width, int height, double cellSizePx);

    /**
     * Disegna solo la parte {@code [x0, x0+w) x [y0, y0+h)} di un pavimento grande
     * {@code fullW x fullH} pixel, con il pixel (x0, y0) nell'origine di {@code g}.
     *
     * I pixel devono essere gli stessi che {@link #paint} disegnerebbe nello stesso punto, qualunque
     * sia la regione: così il pavimento si può generare a riquadri senza giunture, anche quando
     * l'arena è troppo grande per un'unica immagine. Gli stili predefiniti disegnano solo la regione;
     * questa versione di ripiego disegna tutto il pavimento ritagliato alla regione (corretta, ma lenta
     * su arene molto grandi).
     */
    default void paintRegion(Graphics2D g, int x0, int y0, int w, int h, int fullW, int fullH, double cellSizePx) {
        Graphics2D c = (Graphics2D) g.create();
        c.clipRect(0, 0, w, h);
        c.translate(-x0, -y0);
        paint(c, fullW, fullH, cellSizePx);
        c.dispose();
    }

    // ---- fabbriche ----

    /** Scacchiera verde dell'originale JUSE, con il nome Pianura in basso a destra. */
    static FloorStyle checkerboard() {
        return new CheckerboardFloor(Const.arenaFloorColor1, Const.arenaFloorColor2, 1, true);
    }

    /** Scacchiera con colori e lato del quadrato (in celle) a scelta. */
    static FloorStyle checkerboard(Color c1, Color c2, double squareCells) {
        return new CheckerboardFloor(c1, c2, squareCells, false);
    }

    /** Sfondo uniforme con linee di griglia ogni {@code spacingCells} celle. */
    static FloorStyle grid(Color background, Color lines, double spacingCells) {
        return new GridFloor(background, lines, spacingCells);
    }

    /** Piastrelle quadrate in tonalità di grigio caldo, con grana e fughe (lato 3 celle). */
    static FloorStyle randomTiles() {
        return new RandomTilesFloor(new Color(180, 176, 168), 3, 17, 1L);
    }

    /** Piastrelle quadrate: colore medio, lato in celle, seme (stesso seme = stesso pavimento). */
    static FloorStyle randomTiles(Color base, double tileCells, long seed) {
        return new RandomTilesFloor(base, tileCells, 17, seed);
    }

    /** Lastre irregolari tipo pietra (Voronoi), dimensione media 3 celle. */
    static FloorStyle voronoiTiles() {
        return new VoronoiFloor(new Color(196, 186, 168), 3, 20, 1L);
    }

    /** Lastre irregolari: colore medio, dimensione media in celle, seme. */
    static FloorStyle voronoiTiles(Color base, double meanCellCells, long seed) {
        return new VoronoiFloor(base, meanCellCells, 20, seed);
    }

    /** Pista per il line-following: linea nera larga 2/3 di cella su fondo bianco, a 4 celle dal bordo. */
    static FloorStyle track() {
        return new TrackFloor(Color.WHITE, new Color(20, 20, 20), 2.0 / 3, 4);
    }

    /** Pista con colori, spessore della linea e margine dal bordo (in celle) a scelta. */
    static FloorStyle track(Color background, Color line, double lineCells, double marginCells) {
        return new TrackFloor(background, line, lineCells, marginCells);
    }

    /** Tinta unita. */
    static FloorStyle plain(Color color) {
        return (g, w, h, cell) -> { g.setColor(color); g.fillRect(0, 0, w, h); };
    }

    /**
     * Immagine da file (PNG, JPG, GIF, BMP). Se il file non si legge viene stampato
     * un avviso e si usa la scacchiera predefinita: lo scenario parte comunque.
     */
    static FloorStyle image(String path, ImageFloor.Mode mode) {
        return ImageFloor.load(path, mode);
    }

    /** Immagine da file, adattata all'arena mantenendo le proporzioni. */
    static FloorStyle image(String path) {
        return ImageFloor.load(path, ImageFloor.Mode.FIT);
    }

}
