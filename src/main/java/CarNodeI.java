import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public interface CarNodeI {
    double getEnginePower();
    double getWheelTurn();
    boolean isBreakPedal();
    boolean isUseNitro();
    int getStateCount();
    int getMaxDist();
    double getMaxWorth();
    double getMaxRealWorth();
}
