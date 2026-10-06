package jusePack.structures;

import java.util.ArrayList;
import java.util.List;

import jusePack.ArenaObjects.Obstacle;
import jusePack.gui.floor.FloorStyle;
import jusePack.units.RobotDummy;
import jusePack.utils.Const;

public class ScenarioManager {

	private List<Obstacle> obstacles;
	private List<RobotDummy> robots;
	private boolean unitsManagerSet = false;
	private FloorStyle floor = FloorStyle.checkerboard();
	private final jusePack.comm.CommSettings commSettings = new jusePack.comm.CommSettings();
	private double arenaWidthCells = Const.gridCellCount;
	private double arenaHeightCells = Const.gridCellCount;

	public ScenarioManager(){
		robots = new ArrayList<>();
		obstacles = new ArrayList<>();
	}

	public void addRobot(RobotDummy robot){ robots.add(robot); }

	public void addObstacle(Obstacle obstacle){ obstacles.add(obstacle);}

	/** Aspetto del pavimento (solo grafico). Predefinito: scacchiera originale. */
	public void setFloor(FloorStyle floorStyle){ this.floor = floorStyle != null ? floorStyle : FloorStyle.checkerboard(); }

	public FloorStyle getFloor(){ return floor; }

	/**
	 * Radio channel between robots, e.g. {@code getCommSettings().setRange(8).setLineOfSight(true)}.
	 * Defaults: range 6 cells, no line of sight, no losses, delivery at the next physics step.
	 */
	public jusePack.comm.CommSettings getCommSettings(){ return commSettings; }

	/**
	 * Dimensioni dell'arena in celle (predefinito 40 x 40). Da chiamare nel costruttore
	 * dello scenario, prima di avviare la simulazione.
	 */
	public void setArenaSize(double widthCells, double heightCells){
		if (!(widthCells > 0 && heightCells > 0)) throw new IllegalArgumentException("invalid arena size");
		arenaWidthCells = widthCells;
		arenaHeightCells = heightCells;
	}

	public double getArenaWidth(){ return arenaWidthCells; }

	public double getArenaHeight(){ return arenaHeightCells; }

	/** Larghezza dell'arena in pixel di mondo (celle x Const.cellSize). */
	public double getArenaWidthPx(){ return arenaWidthCells * Const.cellSize; }

	/** Altezza dell'arena in pixel di mondo (celle x Const.cellSize). */
	public double getArenaHeightPx(){ return arenaHeightCells * Const.cellSize; }

	public List<RobotDummy> getRobots(){ return robots; }

	public List<Obstacle> getObstacles(){ return obstacles; }

	public int getNumRobots(){ return robots.size();}

	public int getNumObstacles(){ return obstacles.size();}

	public void removeRobot(int index){ robots.remove(index);}

	public void setUnitsManagerSet(boolean set){ this.unitsManagerSet = set; }

	public void enableRobots(){
		if (unitsManagerSet){
			// i guasti simulati sono gestiti dal motore in tempo simulato
			for (RobotDummy r : robots) r.start();
		}
		else { System.err.println("ERROR: unable to start robots - unitsManager not set");}
	}

	public void disableRobots(){
		for(int i = 0; i < robots.size(); i++){
			robots.get(i).setActive(false);
		}
	}

	public void disableRobot(int index){
		robots.get(index).setActive(false);
	}

}
