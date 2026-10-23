"""
Example: robots that wander around and steer away from obstacles, driven from Python.

    1. run the JUSE example scenario 07 (./gradlew run -Pexample=07): it starts paused
    2. python3 wander.py 1 2          (one thread per robot id)
    3. press Run (or Space) in JUSE

Each robot also sends a radio beacon every second and prints which robots it can hear
(neighbours are forgotten after 2.5 s of silence).

This is only a minimal reactive behaviour to show the protocol; write your own!
"""

import random
import sys
import threading

from juse_client import JuseRobot, RobotStopped

FRONT = (0, 1, 2, 16, 17)          # IR sensors around the heading (index 0 = front, clockwise)
LEFT_SIDE = (13, 14, 15, 16, 17)
RIGHT_SIDE = (1, 2, 3, 4, 5)
NEAR = 18                          # IR value (px from the body) under which an obstacle is "near"
BEACON_PERIOD = 1.0                # s between two radio beacons
FORGET_AFTER = 2.5                 # s of silence after which a neighbour is forgotten


def wander(robot_id: int, host: str = "127.0.0.1", port: int = 5555, seed: int = 0):
    rnd = random.Random(seed + robot_id)       # deterministic: same run, same result (lockstep mode)
    turning = 0                                # remaining control periods of an avoidance turn
    turn_dir = 1.0
    t = 0.0
    next_beacon = 0.0
    heard = {}                                 # neighbour id -> last time we heard it
    shown = set()
    with JuseRobot(robot_id, host, port) as robot:
        print(f"robot {robot_id}: connected, mode={robot.welcome['mode']}, period={robot.welcome.get('period')} s")
        try:
            while True:
                s = robot.sensors()
                t = s.t
                # radio: who is around?
                for m in s.inbox:
                    heard[m.sender] = s.t
                heard = {r: when for r, when in heard.items() if s.t - when <= FORGET_AFTER}
                if set(heard) != shown:
                    shown = set(heard)
                    print(f"robot {robot_id} at t={s.t:.1f} s hears: {sorted(shown) or 'nobody'}")
                if s.t >= next_beacon - 1e-9:
                    robot.broadcast({"beacon": robot_id, "x": round(s.x, 2), "y": round(s.y, 2)})
                    next_beacon += BEACON_PERIOD
                if turning > 0:
                    turning -= 1
                    robot.drive(0.0, 2.0 * turn_dir)
                elif min(s.ir[i] for i in FRONT) < NEAR or s.contact:
                    # turn towards the freer side
                    left = sum(s.ir[i] for i in LEFT_SIDE)
                    right = sum(s.ir[i] for i in RIGHT_SIDE)
                    turn_dir = -1.0 if left > right else 1.0     # negative = counter-clockwise = to the left
                    turning = rnd.randint(4, 10)
                    robot.drive(0.0, 2.0 * turn_dir)
                else:
                    robot.drive(1.2, rnd.uniform(-0.3, 0.3))
        except RobotStopped:
            print(f"robot {robot_id}: simulation stopped at t={t:.2f} s")


if __name__ == "__main__":
    ids = [int(a) for a in sys.argv[1:]] or [1]
    threads = [threading.Thread(target=wander, args=(i,), daemon=True) for i in ids]
    for t in threads:
        t.start()
    try:
        for t in threads:
            t.join()
    except KeyboardInterrupt:
        pass
