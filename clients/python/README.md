# Driving JUSE robots from outside (UDP + JSON)

JUSE robots can be driven by any program that speaks UDP and JSON — Python, C, MATLAB,
another JVM. This folder contains a ready-made Python client (`juse_client.py`, standard
library only) and an example (`wander.py`).

## Quick start

1. Run the example scenario `testRobots7.MainClassTest07`. It opens paused, with two remote
   robots waiting on UDP port 5555.
2. Start the client: `python3 wander.py 1 2`
3. Press **Run** (or Space) in JUSE.

## On the JUSE side

```java
RemoteServer server = RemoteServer.start(5555, RemoteServer.Mode.LOCKSTEP);
addRobot(server.createRobot(1, new Position(5, 5, 0), 2, 0.1));   // id, pose, IR range, control period (s)
...
Simulator sim = new Simulator(new Point(30, 30), "JUSE", scenario);
sim.attach(server);        // server messages in the JUSE log, closed by Stop
sim.startSimPaused();
```

By default the server listens on `127.0.0.1` only. To accept clients from other machines use
`RemoteServer.start(new InetSocketAddress(5555), mode, timeoutMs)`.

## Modes

| Mode | What happens every control period | Use it for |
|---|---|---|
| `LOCKSTEP` | JUSE sends the sensors and **waits** for the answer with the same `seq`. The whole simulation waits, so runs are reproducible at any speed. If no answer arrives within the timeout (1 s by default) the robot keeps its last command and a warning is logged. | algorithm development, experiments, debugging |
| `REALTIME` | JUSE sends the sensors and applies the **latest** command received, without waiting — like a real robot on a radio link. | realistic timing, slow or unreliable clients |

A robot whose client has not said `hello` yet stands still, and the simulation is not blocked.
Connect the client before pressing Run, otherwise it misses the first control periods.

## Messages (protocol version 2)

One JSON object per UDP datagram. Lengths are in **cells**, time in **seconds**, angles in
**radians**. The y axis grows downwards and positive angles turn **clockwise** on screen.

### Client → JUSE

| Message | Meaning |
|---|---|
| `{"type":"hello","robot":1}` | Start driving robot 1. Answered by `welcome`. |
| `{"type":"cmd","robot":1,"seq":7,"v":1.0,"w":0.3}` | Linear speed `v` (cells/s), angular speed `w` (rad/s). `seq` echoes the sensor message being answered (required in lockstep mode). |
| `{"type":"cmd","robot":1,"seq":7,"left":500,"right":500}` | Wheel speeds, same units as `RobotUnit.setMotorSpeed`. |
| `{"type":"cmd","robot":1,"seq":7,"v":1.0,"broadcast":{...},"send":[{"to":3,"data":"hi"}]}` | Optional radio messages, see [Radio messages](#radio-messages). A `cmd` with messages but no speeds keeps the previous speeds. |
| `{"type":"bye","robot":1}` | Stop driving robot 1 (it keeps its last command). |

### JUSE → client

| Message | Meaning |
|---|---|
| `{"type":"welcome","protocol":2,"robot":1,"mode":"lockstep","period":0.1,"units":{...}}` | Connection accepted. |
| `{"type":"sensors", ...}` | One reading per control period, see below. |
| `{"type":"stopped","robot":1}` | The simulation was stopped or the robot was disabled. |
| `{"type":"error","message":"..."}` | Malformed JSON, unknown robot, invalid command, ... |

`sensors` fields:

| Field | Content |
|---|---|
| `seq`, `t` | Sequence number (echo it in `cmd`) and simulated time |
| `pose` | `{"x","y","theta"}`: true pose of the robot |
| `ir` | 18 IR sensors, index 0 = front then clockwise every 20°: distance in px from the body, 255 = nothing in range |
| `bump` | 18 bump sensors, 0 = no contact |
| `contact` | `true` while the body is pushing against an obstacle or robot |
| `floor` | Floor (line-following) sensors, 0 = black … 255 = white; index 0 = left, 1 = right |
| `wheels` | `{"left","right"}`: distance travelled by each wheel (cells, signed; wheels keep turning when the body is blocked) |
| `active` | `false` after a simulated fault |
| `inbox` | Radio messages received since the previous reading: `[{"from":2,"to":"all","t":0.6,"data":...}]` (`to` is `"all"` for a broadcast, else the receiver id; `t` is the sending time) |
| `inbox_dropped` | Present only if the inbox did not fit in one datagram: number of oldest messages dropped |

## Radio messages

Remote robots talk to each other, and to robots written in Java, through JUSE's simulated
radio network: a message reaches only the robots within the sender's range (and in line of
sight, if the scenario enables it), with the scenario's losses and latency. There is no
"list of neighbours": a robot learns who is around from the messages it receives.

- `"broadcast": data` sends `data` to every robot in range.
- `"send": [{"to": 3, "data": ...}, ...]` sends to single robots (delivered only if in range);
  `"to": "all"` is a broadcast, handy to send more than one per command.
- `data` is any JSON value. Numbers arrive as floating point, as in JSON.
- Messages leave at the control instant of the `cmd` that carries them, exactly once; they can
  be read at the receiver's next control period (at the earliest one physics step later).

The radio range is set in the scenario (`getCommSettings().setRange(cells)`, 6 cells by
default) or per robot (`robot.setCommRange(cells)`). The **Connectivity window** in JUSE shows
who hears whom while the simulation runs.

## Python client

```python
from juse_client import JuseRobot

with JuseRobot(robot_id=1) as robot:
    while True:
        s = robot.sensors()          # blocks until the next reading
        robot.drive(1.0, 0.0)        # answer every reading exactly once (drive or wheels)
```

Radio messages:

```python
        for m in s.inbox:                    # Message(sender, to, t, data); to is None for a broadcast
            print(f"from {m.sender}: {m.data}")
        robot.broadcast({"x": s.x, "y": s.y})
        robot.send(2, "only for robot 2")
        robot.drive(1.0, 0.0)                # the messages leave with this command
```

`wander.py` sends a beacon every second and prints which robots it can hear.

Protocol version 1 clients are refused by version 2 servers (and vice versa): update
`juse_client.py` together with JUSE.
