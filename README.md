# Pianura

**A lightweight 2D simulator for differential-drive mobile robots, written in Java.**

Pianura is a simple, self-contained simulator for prototyping and testing mobile-robot
algorithms: navigation, obstacle avoidance, line following, multi-robot coordination and
swarms. You write a robot in a few lines of plain Java, or drive it from any language over
UDP, and watch it move in a 2D arena.

It is meant for students, teachers and researchers who want something that starts with a
single command and can be read and modified in an afternoon. If you need full 3D physics
or ROS 2 integration, a heavier simulator such as Gazebo is a better fit.

Pianura is the evolution of **JUSE** (Java UniBot Simulation Environment), a simulator
written in 2008 and used for years at the University of Bologna; see [Origins](#origins).

---

## Features

- **Differential-drive kinematics**: exact integration on the instantaneous centre of
  curvature, Runge-Kutta for straight motion
- **Sensors**: 18 infrared range sensors and 18 bump sensors per robot, computed by
  geometric ray casting; optical floor sensors for line following
- **Obstacles**: rectangles and ovals, rotated at any angle, with optional borders;
  overlaps and obstacles outside the arena are reported when a scenario loads
- **Simulated clock**: fixed time step, pause, single step and speed control, so runs
  are reproducible
- **Two ways to program a robot**: a sequential control loop, or a controller called
  at a fixed period
- **Radio between robots**: range, losses and latency, plus a live connectivity window
  (graph, adjacency and incidence matrices)
- **Remote control over UDP + JSON**, with a ready-made Python client; lockstep mode for
  reproducible experiments, real-time mode for realistic timing
- **Arenas of any size**: zoom, pan and fly over large maps with a minimap
- **Floors**: checkerboard, grid, random tiles, Voronoi tiles, a line-following track,
  or any image
- **Scene editing**: before starting, drag, resize and rotate obstacles and robots
  with the mouse
- No dependencies beyond the JDK

---

## Requirements

- A JDK, version 21 or later (25 LTS recommended), e.g. [Eclipse Temurin](https://adoptium.net/)
- Nothing else: the Gradle wrapper is included

---

## Quick start

```bash
git clone https://github.com/raffaeleGrandi/pianura.git
cd pianura
./gradlew run
```

This opens example scenario 04: nine robots avoiding rectangular and oval obstacles.
Scenarios open paused: arrange the scene if you like, then press **Run** (or Space).

To run another example, pass its number:

```bash
./gradlew run -Pexample=09
```

| Example | What it shows |
|---------|---------------|
| 03 | Two robots in the original JUSE arena |
| 04 | Nine robots and mixed obstacles (the default demo); some obstacles overlap on purpose, to show the scenario warnings |
| 05 | A large 80×50 arena with rooms and corridors: zoom, pan and the side panel |
| 06 | Line following: a track on the floor and a robot with two optical sensors, waiting for your controller |
| 07 | Two robots driven from outside over UDP (see [`clients/python`](clients/python)) |
| 08 | Fourteen robots exchanging radio beacons, with the connectivity window |
| 09 | A 300×200 map to fly over, explored by a swarm of 60 robots |

---

## Programming a robot

### As a control loop

Extend `RobotDummy` and implement `executeLoop()`. It runs in its own virtual thread and
holds the whole control logic. Use `sleepSim()` instead of `Thread.sleep()`: it waits in
*simulated* time, so pause, single step and speed control work.

```java
import io.github.raffaelegrandi.pianura.units.RobotDummy;
import io.github.raffaelegrandi.pianura.utils.Position;

public class MyRobot extends RobotDummy {

    public MyRobot(int id, Position start, double sensorRange) {
        super(id, start, sensorRange);
    }

    @Override
    public void executeLoop() {
        forward(500);                         // both wheels forward
        while (isActive()) {
            int[] ir = getAllSens();          // 18 IR readings: 0 = contact, 255 = clear
            if (ir[0] < 30) {                 // obstacle ahead (sensor 0 = front)
                rotateBy(45, 50);             // turn 45° on the spot
                forward(500);
            }
            sleepSim(0.2);                    // 0.2 s of simulated time
        }
    }
}
```

### As a periodic controller

Implement `RobotController` and give it to a `ControllerRobot`. The simulator calls
`control()` every control period (here 0.1 s) with the current simulated time. In a
scenario constructor (see below):

```java
addRobot(new ControllerRobot(1, new Position(5, 5, 0), 2, 0.1,
        (robot, t) -> {
            boolean blocked = robot.getOneSen(0) < 30;
            robot.setRobotSpeed(blocked ? 0 : 1.0, blocked ? 1.5 : 0);   // cells/s, rad/s
        }));
```

Main methods available on a robot:

| Method | Description |
|--------|-------------|
| `forward(speed)` / `backward(speed)` | Both wheels at the same speed |
| `setMotorSpeed(left, right)` | Wheel speeds (differential drive) |
| `setRobotSpeed(v, w)` | Linear (cells/s) and angular (rad/s) speed |
| `rotateBy(angleDeg, stepMs)` | Turn on the spot, blocking |
| `getAllSens()` / `getOneSen(i)` | IR readings, 0 (contact) to 255 (nothing in range) |
| `hasCollision()` | Whether the robot is touching an obstacle |
| `getFloorGrays()` | Floor sensor readings, 0 = black, 255 = white |
| `broadcast(data)` / `send(id, data)` / `receive()` | Radio messages |
| `getPosition()` | Pose (x, y in cells, theta in radians) |
| `sleepSim(seconds)` / `getSimTime()` | Simulated time |

---

## Building a scenario

Extend `ScenarioManager` and add robots and obstacles. Positions are in cells, angles in
radians.

```java
import io.github.raffaelegrandi.pianura.arena.ArenaObjectType;
import io.github.raffaelegrandi.pianura.arena.Obstacle;
import io.github.raffaelegrandi.pianura.structures.ScenarioManager;
import io.github.raffaelegrandi.pianura.utils.Position;
import java.awt.Color;

public class MyScenario extends ScenarioManager {

    public MyScenario() {
        setArenaSize(40, 30);                                     // cells

        addRobot(new MyRobot(1, new Position(10, 10, 0), 2));
        addRobot(new MyRobot(2, new Position(20, 20, Math.toRadians(90)), 2));

        addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_RECT, Color.GRAY,
                new Position(15, 15, Math.toRadians(45)), 6, 3));    // width, height in cells
        addObstacle(new Obstacle(ArenaObjectType.OBSTACLE_OVAL, Color.WHITE,
                new Position(25, 10, 0), 4, 4));
    }
}
```

Then start it from a `main`:

```java
import io.github.raffaelegrandi.pianura.Simulator;
import java.awt.Point;

public class Main {
    public static void main(String[] args) {
        Simulator sim = new Simulator(new Point(50, 50), "My simulation", new MyScenario());
        sim.startSimPaused();      // opens paused: press Run or Space
    }
}
```

---

## Remote control

Robots can be driven by any program that speaks UDP and JSON: Python, C, MATLAB, another
JVM. [`clients/python`](clients/python) contains a Python client (standard library only),
an example and the description of the protocol.

---

## Sensor layout

The 18 IR and bump sensors are spaced 20° apart, starting from the robot's heading
(sensor 0 = front) and going clockwise:

```
              [0]               <- front (0°)
         [17]      [1]          <- ±20°
     [16]              [2]      <- ±40°
  [15]                    [3]   <- ±60°
[14]          (R)          [4]  <- ±80°
[13]                       [5]  <- ±100°
  [12]                    [6]   <- ±120°
     [11]              [7]      <- ±140°
         [10]      [8]          <- ±160°
              [9]               <- rear (180°)
```

---

## Project structure

```
src/main/java/io/github/raffaelegrandi/pianura/
├── Simulator.java     entry point: wires scenario, simulation engine and GUI
├── sim/               simulation engine and simulated clock
├── units/             robots and controllers
│   ├── collisions/    geometric ray casting, shapes, spatial grid
│   └── sensors/       floor sensors
├── arena/             obstacles and object identifiers
├── structures/        ScenarioManager
├── comm/              radio network between robots
├── remote/            UDP + JSON remote control
├── gui/               Swing user interface
│   ├── view/          arena view, minimap, side panel, connectivity window
│   └── floor/         floor styles
├── utils/             constants, Position, log
└── examples/          example scenarios 03–09
src/test/java/         unit tests (JUnit 5)
clients/python/        Python client for remote control
```

---

## Origins

Pianura grew out of **JUSE** (Java UniBot Simulation Environment), which Raffaele Grandi
started in April 2008 as a support tool for his M.Sc. thesis in Computer Science at the
University of Bologna, on coordinating swarms of mobile robots with Particle Swarm
Optimization. Its key design choice, modelling each robot as an independent agent with
separate layers from logic down to physics, made it easy to extend with new behaviours.

During his PhD (2010–2013) at the University of Bologna, JUSE became the simulated
counterpart of **UniBot**, a differential-drive robot being built in the lab: algorithms
were to be designed in the simulator and then moved to the real robot, driven over UDP.
UniBot was never completed in time, and JUSE remained the tool used to develop
mobile-robot algorithms in several theses and in Artificial Intelligence courses. It was
especially welcome among Computer Science students, who knew Java well, while MATLAB
belonged to the Control Engineering curriculum.

In 2026 the code was brought up to modern Java and largely rewritten: geometric ray
casting instead of pixel-based collisions, a simulated clock decoupled from the GUI,
floor sensors, radio, remote control over UDP and large maps. It had become a different
program, and it took a new name. The original JUSE, as it was in 2011, is preserved at
[github.com/raffaeleGrandi/JUSE](https://github.com/raffaeleGrandi/JUSE).

## Citing

If you use Pianura in academic work, please cite the paper that describes the original
JUSE and the UniBot project:

> R. Grandi, R. Falconi, C. Melchiorri, *"UniBot Remote Laboratory: A Scalable Web-Based
> Set-up for Education and Experimental Activities in Robotics"*, IFAC World Congress 2011,
> Milan, Italy. DOI: [10.3182/20110828-6-IT-1002.03103](https://doi.org/10.3182/20110828-6-IT-1002.03103)

---

## Contributing

Contributions are welcome. If you find a bug, have an idea or want to improve the
documentation, open an issue or a pull request.

## License

Pianura is released under the [MIT License](LICENSE).

---

*Pianura and the original JUSE were developed independently by Raffaele Grandi, their
sole author, starting in 2008. JUSE was later used in research and teaching at the
University of Bologna and in other students' theses, but its code was never developed by
or transferred to the University.*
