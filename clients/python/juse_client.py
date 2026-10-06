"""
JUSE remote-control client (Python 3.8+, standard library only).

Drive a JUSE robot from Python over UDP:

    from juse_client import JuseRobot

    with JuseRobot(robot_id=1) as robot:          # default: 127.0.0.1:5555
        while True:
            s = robot.sensors()                   # blocks until the next control period
            if min(s.ir[i] for i in (0, 1, 17)) < 20:
                robot.drive(0.0, 1.5)             # obstacle ahead: turn in place
            else:
                robot.drive(1.0, 0.0)             # go straight (cells/s, rad/s)

Every call to sensors() must be followed by exactly one drive() or wheels() call:
in lockstep mode the simulation waits for that answer before going on.

Radio messages (protocol version 2) go through JUSE's simulated network, with the same range,
losses and latency as Java robots:

    for m in s.inbox:                             # received since the previous reading
        print(m.sender, m.data)
    robot.broadcast({"seen": s.t})                # to every robot in range
    robot.send(3, "hello 3")                      # to robot 3, if it is in range
    robot.drive(1.0, 0.0)                         # messages leave with this command

Message data must be JSON data (dict, list, str, number, bool, None); numbers arrive as floats.

Units: lengths in cells, time in seconds, angles in radians.
Screen convention: y grows downwards, positive angles turn clockwise on screen.
"""

import json
import socket
from dataclasses import dataclass, field
from typing import Any, List, Optional

PROTOCOL_VERSION = 2


class JuseError(Exception):
    """An error message sent back by JUSE (unknown robot, malformed command, ...)."""


class RobotStopped(Exception):
    """The simulation was stopped, or the robot was disabled (e.g. simulated fault)."""


@dataclass
class Message:
    sender: int                 # id of the robot that sent it
    to: Optional[int]           # receiver id, or None for a broadcast
    t: float                    # simulated time when it was sent (s)
    data: Any                   # the content, as sent (JSON data)


@dataclass
class Sensors:
    t: float                    # simulated time (s)
    seq: int                    # sequence number, echoed back by drive()/wheels()
    x: float                    # pose: position in cells ...
    y: float
    theta: float                # ... and heading in radians (clockwise on screen)
    ir: List[int]               # 18 IR sensors, 0 = touching, 255 = nothing in range (index 0 = front, clockwise)
    bump: List[int]             # 18 bump sensors, 0 = no contact
    contact: bool               # the body is pushing against something
    floor: List[int]            # floor sensors, 0 = black, 255 = white (0 = left, 1 = right)
    wheel_left: float           # distance travelled by each wheel (cells, signed)
    wheel_right: float
    active: bool
    inbox: List[Message] = field(default_factory=list)   # radio messages received since the previous reading
    raw: dict = field(repr=False, default_factory=dict)


class JuseRobot:
    """One remote robot. Use one instance (and one socket) per robot."""

    def __init__(self, robot_id: int, host: str = "127.0.0.1", port: int = 5555, timeout: float = 30.0):
        self.robot_id = robot_id
        self.server = (host, port)
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.settimeout(timeout)
        self.welcome: Optional[dict] = None
        self._last_seq: Optional[int] = None
        self._broadcast: List[Any] = []
        self._send_to: List[dict] = []

    # -- connection -------------------------------------------------------------------

    def connect(self) -> dict:
        """Say hello and wait for the welcome message (mode, control period, units)."""
        self._send({"type": "hello", "robot": self.robot_id})
        while True:
            msg = self._receive()
            if msg.get("type") == "welcome":
                if msg.get("protocol") != PROTOCOL_VERSION:
                    raise JuseError(f"protocol {msg.get('protocol')} not supported (client speaks {PROTOCOL_VERSION})")
                self.welcome = msg
                return msg

    def close(self):
        try:
            self._send({"type": "bye", "robot": self.robot_id})
        except OSError:
            pass
        self.sock.close()

    def __enter__(self):
        self.connect()
        return self

    def __exit__(self, *exc):
        self.close()
        return False

    # -- control loop -----------------------------------------------------------------

    def sensors(self) -> Sensors:
        """Wait for the next sensor reading of this robot."""
        while True:
            msg = self._receive()
            kind = msg.get("type")
            if kind == "sensors":
                self._last_seq = int(msg["seq"])
                pose, wheels = msg["pose"], msg["wheels"]
                return Sensors(t=msg["t"], seq=self._last_seq, x=pose["x"], y=pose["y"], theta=pose["theta"],
                               ir=[int(v) for v in msg["ir"]], bump=[int(v) for v in msg["bump"]],
                               contact=bool(msg["contact"]), floor=[int(v) for v in msg["floor"]],
                               wheel_left=wheels["left"], wheel_right=wheels["right"],
                               active=bool(msg["active"]),
                               inbox=[Message(sender=int(m["from"]), to=None if m["to"] == "all" else int(m["to"]),
                                              t=m["t"], data=m["data"]) for m in msg.get("inbox", [])],
                               raw=msg)
            if kind == "stopped":
                raise RobotStopped(f"robot {self.robot_id} stopped")

    def drive(self, v: float, w: float = 0.0):
        """Linear speed v (cells/s) and angular speed w (rad/s, positive = clockwise on screen)."""
        self._command({"v": float(v), "w": float(w)})

    def wheels(self, left: float, right: float):
        """Wheel speeds in the same units as RobotUnit.setMotorSpeed (e.g. 500, 500 = straight on)."""
        self._command({"left": float(left), "right": float(right)})

    def broadcast(self, data: Any):
        """Radio message to every robot in range; it leaves with the next drive()/wheels()."""
        json.dumps(data)                                  # fail now if it is not JSON data
        self._broadcast.append(data)

    def send(self, to: int, data: Any):
        """Radio message to robot `to` (delivered only if it is in range); leaves with the next drive()/wheels()."""
        json.dumps(data)
        self._send_to.append({"to": int(to), "data": data})

    # -- low level ----------------------------------------------------------------------

    def _command(self, motion: dict):
        msg = {"type": "cmd", "robot": self.robot_id, "seq": self._last_seq, **motion}
        # one broadcast goes in "broadcast"; any further one becomes a "send" item addressed to "all"
        if self._broadcast:
            msg["broadcast"] = self._broadcast[0]
        sends = [{"to": "all", "data": d} for d in self._broadcast[1:]] + self._send_to
        if sends:
            msg["send"] = sends
        self._broadcast, self._send_to = [], []
        self._send(msg)

    def _send(self, msg: dict):
        self.sock.sendto(json.dumps(msg).encode("utf-8"), self.server)

    def _receive(self) -> dict:
        data, _ = self.sock.recvfrom(65535)
        msg = json.loads(data.decode("utf-8"))
        if msg.get("type") == "error":
            raise JuseError(msg.get("message", "unknown error"))
        return msg
