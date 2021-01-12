package bugplayer;
import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;

    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;
        System.out.println("I'm a " + rc.getType() + " and I just got created!");

        while (true) {
            // Try/catch blocks stop unhandled exceptions, which cause your robot to freeze
            try {
                // Here, we've separated the controls into a different method for each RobotType.
                // You may rewrite this into your own control structure if you wish.
                switch (rc.getType()) {
                    case ENLIGHTENMENT_CENTER: runEnlightenmentCenter(); break;
                    case POLITICIAN:           runPolitician();          break;
                    case SLANDERER:            runSlanderer();           break;
                    case MUCKRAKER:            runMuckraker();           break;
                    default: return;  // we're not creating these here
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();
            }
        }
    }

    static void runSlanderer() throws GameActionException {
    }

    static void runPolitician() throws GameActionException {
    }


    /** Enlightenment centers spam politicians */
    static boolean spawnedRobot = false;
    static void runEnlightenmentCenter() throws GameActionException {

        // spawn one robot as explorer, then done!
        if (!spawnedRobot) {
            RobotType toBuild = RobotType.MUCKRAKER;
            int influence = 1;
            for (Direction dir : Direction.values()) {
                if (rc.canBuildRobot(toBuild, dir, influence)) {
                    rc.buildRobot(toBuild, dir, influence);
                }
            }
            spawnedRobot = true;
        }

        // TODO: Set direction for robot to explore
        communicateGameState(GameState.EXPLORE, Direction.EAST);
    }

    static int parentECId = -1;
    static int parentECFlag = 0;
    static MapLocation target = null;
    static boolean firstTurn = true;
    static void runMuckraker() throws GameActionException {
        if (parentECId == -1) {
            for (RobotInfo robot : rc.senseNearbyRobots(-1, rc.getTeam())) {
                if (robot.type == RobotType.ENLIGHTENMENT_CENTER) {
                    parentECId = robot.ID;
                }
            }
        }

        if (rc.canGetFlag(parentECId)) {
            parentECFlag = rc.getFlag(parentECId);
        }

        // Set target depending on the flag from EC when in first turn
        if (firstTurn && decodeECType(parentECFlag) == GameState.EXPLORE) {
            Direction exploreDirection = Direction.values()[decodeECExtra(parentECFlag)];
            target = rc.getLocation().translate(exploreDirection.dx * 64, exploreDirection.dy * 64);
        }

        // Will use basic bug pathing to target
        if (decodeECType(parentECFlag) == GameState.EXPLORE) {
            basicBug(target);
        }
    }


    /////////////////////////////////////////////////////////////////////////////
    // Evan EC Communication functions and decoders

    static enum GameState {
        EXPLORE, ATTACK, DEFEND;
    }
    static enum BotMessageType {
        PASSABILITY, EC, ENEMY;
    }

    static final int NBITS = 10;
    static final int TYPEBITSHIFT = 7;
    static final int BITMASK = (1 << NBITS) - 1;
    static final int DIRBITSHIFT = 4;

    static void communicateTilePassibility(Direction dir, int p) throws GameActionException{
        int message = (BotMessageType.PASSABILITY.ordinal() << TYPEBITSHIFT) + (dir.ordinal() << DIRBITSHIFT) + p;
        rc.setFlag(message);
    }

    static void communicateGameState(GameState state, Direction dir) throws GameActionException {
        int message = BITMASK & (state.ordinal() << TYPEBITSHIFT) + dir.ordinal();
        rc.setFlag(message);
    }

    static GameState decodeECType(int flag) throws GameActionException {
        return GameState.values()[flag >> TYPEBITSHIFT];
    }

    static int decodeECExtra(int flag) throws GameActionException{
        return ((1 << (TYPEBITSHIFT)) - 1) & flag;
    }


    ////////////////////////////////////////////////////////////////////////////
    // COMMUNICATION

    static MapLocation getLocationFromFlag(int flag) {
        int y = flag & BITMASK;
        int x = (flag >> NBITS) & BITMASK;
        // int extraInformation = flag >> (2*NBITS);

        MapLocation currentLocation = rc.getLocation();
        int offsetX128 = currentLocation.x >> NBITS;
        int offsetY128 = currentLocation.y >> NBITS;
        MapLocation actualLocation = new MapLocation((offsetX128 << NBITS) + x, (offsetY128 << NBITS) + y);

        // You can probably code this in a neater way, but it works
        MapLocation alternative = actualLocation.translate(-(1 << NBITS), 0);
        if (rc.getLocation().distanceSquaredTo(alternative) < rc.getLocation().distanceSquaredTo(actualLocation)) {
            actualLocation = alternative;
        }
        alternative = actualLocation.translate(1 << NBITS, 0);
        if (rc.getLocation().distanceSquaredTo(alternative) < rc.getLocation().distanceSquaredTo(actualLocation)) {
            actualLocation = alternative;
        }
        alternative = actualLocation.translate(0, -(1 << NBITS));
        if (rc.getLocation().distanceSquaredTo(alternative) < rc.getLocation().distanceSquaredTo(actualLocation)) {
            actualLocation = alternative;
        }
        alternative = actualLocation.translate(0, 1 << NBITS);
        if (rc.getLocation().distanceSquaredTo(alternative) < rc.getLocation().distanceSquaredTo(actualLocation)) {
            actualLocation = alternative;
        }
        return actualLocation;
    }


    ////////////////////////////////////////////////////////////////////////////
    // BASIC BUG - just follow the obstacle while it's in the way
    //             not the best bug, but works for "simple" obstacles
    //             for better bugs, think about Bug 2!

    static final double passabilityThreshold = 0.0;
    static Direction bugDirection = null;

    static void basicBug(MapLocation target) throws GameActionException {
        Direction d = rc.getLocation().directionTo(target);
        if (rc.getLocation().equals(target)) {
            // do something else, now that you're there
            // here we'll just explode
            if (rc.canEmpower(1)) {
                rc.empower(1);
            }
        } else if (rc.isReady()) {
            if (rc.canMove(d) && rc.sensePassability(rc.getLocation().add(d)) >= passabilityThreshold) {
                rc.move(d);
                bugDirection = null;
            } else {
                if (bugDirection == null) {
                    bugDirection = d;
                }
                for (int i = 0; i < 8; ++i) {
                    if (rc.canMove(bugDirection) && rc.sensePassability(rc.getLocation().add(bugDirection)) >= passabilityThreshold) {
                        rc.setIndicatorDot(rc.getLocation().add(bugDirection), 0, 255, 255);
                        rc.move(bugDirection);
                        bugDirection = bugDirection.rotateLeft();
                        break;
                    }
                    rc.setIndicatorDot(rc.getLocation().add(bugDirection), 255, 0, 0);
                    bugDirection = bugDirection.rotateRight();
                }
            }
        }
    }
}
