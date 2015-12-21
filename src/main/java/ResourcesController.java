import model.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.HashSet;

/**
 * Created with IntelliJ IDEA.
 * User: asantalov
 * Date: 21.11.13
 * Time: 11:21
 */
public class ResourcesController {
    private static final long MEMORY_LIMIT = 256L*1024L*1024L;
    private static final long REAL_TIME_MOVE_LIMIT = 5*1000L;
    private static final long REAL_GAME_TIME_CAR_TICK_LIMIT = 30L;
    private static final long CPU_GAME_TIME_CAR_TICK_LIMIT = 15L;
    private static final long GAME_TIME_CAR_BASE = 5000L;

    private static long MEMORY_THRESHOLD;
    private static long REAL_TIME_MOVE_THRESHOLD;
    private static long REAL_GAME_TIME_THRESHOLD;
    private static long CPU_GAME_TIME_THRESHOLD;
    private static Long gameStartTime;
    private static long gameStartCPUTime;
    private static int moveCount;

    private static MemoryMXBean memoryMXBean;
    private static ThreadMXBean threadMXBean;

    private static long gameSumRealTime, gameSumCPUTime;
    private static long moveStartRealTime;
    private static long awMoveRealTime, awMovieCPUTime;

    public static void startMove(World world, double memoryThreshold, double moveTimeThreshold, double gameTimeThreshold) {
        if (gameStartTime == null) {
            gameStartTime = System.currentTimeMillis();
            moveCount = 0;
            gameSumRealTime = 0;
            gameSumCPUTime = 0;

            int carCount = 0;
            for (Car car : world.getCars()) {
                if (car.isTeammate())
                    carCount++;
            }

            MEMORY_THRESHOLD = (long)(MEMORY_LIMIT*memoryThreshold);
            REAL_TIME_MOVE_THRESHOLD  = (long)(REAL_TIME_MOVE_LIMIT*moveTimeThreshold);
            REAL_GAME_TIME_THRESHOLD = (long)((REAL_GAME_TIME_CAR_TICK_LIMIT * world.getTickCount() * carCount + GAME_TIME_CAR_BASE) * gameTimeThreshold);
            CPU_GAME_TIME_THRESHOLD = (long)((CPU_GAME_TIME_CAR_TICK_LIMIT * world.getTickCount() * carCount + GAME_TIME_CAR_BASE) * gameTimeThreshold);

            awMoveRealTime = REAL_GAME_TIME_THRESHOLD / world.getLastTickIndex();
            awMovieCPUTime = CPU_GAME_TIME_THRESHOLD / world.getLastTickIndex();

            memoryMXBean = ManagementFactory.getMemoryMXBean();
            threadMXBean = ManagementFactory.getThreadMXBean();

            gameStartCPUTime = getCurrentThreadCpuTime();
        }

        moveStartRealTime = System.currentTimeMillis();
    }

    public static void endMove() {
        gameSumRealTime = System.currentTimeMillis() - gameStartTime;
        gameSumCPUTime = (getCurrentThreadCpuTime() - gameStartCPUTime);
        moveCount++;

        awMoveRealTime = gameSumRealTime / moveCount;
        awMovieCPUTime = gameSumCPUTime / moveCount;
    }

    private static long getMoveRealTimeConsumed() {
        return System.currentTimeMillis() - moveStartRealTime;
    }

    private static long getCurrentThreadCpuTime() {
        return threadMXBean.getCurrentThreadCpuTime()/1000000l;
    }
    private static long getMemoryUsage() {
        return memoryMXBean.getHeapMemoryUsage().getUsed();
    }

    public static boolean isMemoryThresholdExceeded() {
        return getMemoryUsage() > MEMORY_THRESHOLD;
    }
    public static boolean isMoveTimeThresholdExceeded() {
        return getMoveRealTimeConsumed() > REAL_TIME_MOVE_THRESHOLD;
    }

//    public static double getMoveRealTimeScale(World world) {
//        double startAwRealMoveTime = (1.0*REAL_GAME_TIME_THRESHOLD) / world.getLastTickIndex();
//        return startAwRealMoveTime / awMoveRealTime;
//    }
//    public static double getMoveCPUTimeScale(World world) {
//        double startAwCPUMoveTime = (1.0*CPU_GAME_TIME_THRESHOLD) / world.getLastTickIndex();
//        return startAwCPUMoveTime / awMovieCPUTime;
//    }

    public static double getGameRealTimeScale(World world) {
        double startAwTimeForMove = (1.0*REAL_GAME_TIME_THRESHOLD) / world.getLastTickIndex();
        double awTimeForMove = (1.0*(REAL_GAME_TIME_THRESHOLD - gameSumRealTime)) / (world.getLastTickIndex() - world.getTick());
        return awTimeForMove / startAwTimeForMove;
    }
    public static double getGameCPUTimeScale(World world) {
        double startAwCPUTimeForMove = (1.0*CPU_GAME_TIME_THRESHOLD) / world.getLastTickIndex();
        double awCPUTimeForMove = (1.0*(CPU_GAME_TIME_THRESHOLD - gameSumCPUTime)) / (world.getLastTickIndex() - world.getTick());
        return awCPUTimeForMove / startAwCPUTimeForMove;
    }

    public static void gc() {
        memoryMXBean.gc();
    }
}
