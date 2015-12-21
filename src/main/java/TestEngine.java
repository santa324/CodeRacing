import model.*;

import java.util.Arrays;

public class TestEngine {
    public static final int MAX_PREDICTION_TICKS = 1000;
    public final int predictionTicks;

    private final CarProphet carProphet;

    public TestEngine(int predictionTicks) {
        if (predictionTicks < 0) predictionTicks = 0;
        if (predictionTicks > MAX_PREDICTION_TICKS) predictionTicks = MAX_PREDICTION_TICKS;

        this.predictionTicks = predictionTicks;
        this.carProphet = new CarProphet(predictionTicks);
    }

    public void checkPredictions(Car car, GameData data) {
        carProphet.checkPredictions(car, data);
    }

    public void updatePredictions(Car car, GameData data) {
        carProphet.updatePredictions(car, data);
    }

    public class CarProphet extends Prophet<Car, CarPrediction> {
        protected CarProphet(int predictionTicks) {
            super(predictionTicks);
        }

        @Override
        public void checkPredictions(Car car, GameData data) {
//            for (Car c : data.world.getCars()) {
//                if (c.getId() != car.getId() && GameUtil.collideBox(car, c)) {
//                    return;
//                }
//            }

            if (prediction != null && prevProjectle != null && data.world.getProjectiles().length > prevProjectle.length)
                return;

            if (prediction != null && data.world.getTick() <= (prediction.startTick + prediction.predictionTicks) && prediction.isPredictionBroken(data)) {
                return;
            }

            if (car.getRemainingOiledTicks() > data.getPrevState(car).remainingOiledTicks) {
                int kk = 0;
            }

            super.checkPredictions(car, data);
        }

        private Projectile[] prevProjectle;

        @Override
        public void updatePredictions(Car car, GameData data) {
//            if (data.world.getTick() < data.game.getInitialFreezeDurationTicks()) {
//                this.prediction = null;
//                return;
//            }

            if (prediction != null &&  data.world.getTick() > (prediction.startTick + prediction.predictionTicks))
                prediction = null;

//            for (Car c : data.world.getCars()) {
//                if (c.getId() != car.getId() && GameUtil.collideBox(car, c)) {
//                    this.prediction = null;
//                    return;
//                }
//            }

            if (prediction != null && prevProjectle != null && data.world.getProjectiles().length > prevProjectle.length)
                prediction = null;

            prevProjectle = data.world.getProjectiles();

            if (prediction != null && prediction.isPredictionBroken(data)) {
                this.prediction = null;
            }

            if (car.getRemainingOiledTicks() > data.getPrevState(car).remainingOiledTicks)
                this.prediction = null;

            if (prediction != null) {
                prediction.updatePrediction(car, data);
            }

            super.updatePredictions(car, data);
        }

        @Override
        protected CarPrediction makePrediction(Car car, GameData data) {
            return new CarPrediction(predictionTicks, car, data);
        }
    }

    public static class CarPrediction extends Prediction<Car> {
        private final double angularSpeedFactor;

        private final WorldEngine.State<UnitState.CarState>[] predictStates;

        final WorldEngine<UnitState.CarState, CarEngine<UnitState.CarState>, WorldEngine.State<UnitState.CarState>> engine;
        private int collisionTick = -1;

        public CarPrediction(int predictTicks, Car car, GameData data) {
            super(data.world.getTick(), predictTicks);
            this.angularSpeedFactor = data.game.getCarAngularSpeedFactor();

            this.predictStates = new WorldEngine.State[predictTicks+1];

            int[][] path = GameUtil.findPath(car.getNextWaypointIndex(), data);
            final int toUnfreezeTicks = data.game.getInitialFreezeDurationTicks() - data.world.getTick();
            CarTracker.CarPathEngine cEngine = new CarTracker.CarPathEngine(car, data, path, (short)car.getNextWaypointIndex()) {
                @Override
                public void perStepPre() {
                    super.perStepPre();

                    if (getState().tick < toUnfreezeTicks) {
                        this.Fx = 0;
                        this.Fy = 0;
                    }
                }
            };
            UnitState.CarState cState = cEngine.createState(car, data.getPrevState(car), data.game);

            engine = new WorldEngine<>(new CarEngine[] {cEngine}, data, data.world.getOilSlicks(), new WorldEngine.State[] {});
            engine.setResolveBorderCollisions(true);
            engine.setState(new WorldEngine.State<>(new UnitState.CarState[] {cState}, data, data.world.getProjectiles()));
            this.predictStates[0] = engine.getState().copy();

            logState(data.world.getTick(), 0, this.predictStates[0].getCarState(0), car);

            cEngine.setEnginePowerAim(data.move.getEnginePower());
            cEngine.setWheelTurnAim(data.move.getWheelTurn());
            cEngine.setBreakPedal(data.move.isBrake());
            cEngine.setUseNitro(data.move.isUseNitro());

            for (int t=1;t<predictStates.length;t++) {
                engine.perStep();
                if (!cEngine.getState().collision) {
                    predictStates[t] = engine.getState().copy();
                } else {
                    if (collisionTick < 0) {
                        collisionTick = t;
                        System.out.println("Collision on "+(startTick + collisionTick));
                    }
                    break;
                }
            }
            if (collisionTick > 0)
                Arrays.fill(predictStates, collisionTick, predictStates.length, null);
        }

        public boolean isPredictionBroken(GameData data) {
            int tick = data.world.getTick() - startTick;
            WorldEngine.State<UnitState.CarState> currentState = predictStates[tick];
            if (currentState == null)
                return true;

            if (tick > 0) {
                WorldEngine.State<UnitState.CarState> prevState = predictStates[tick - 1];
                UnitState.CarState cPrevState = prevState.getCarState(0);
                UnitState.CarState cCurrentState = currentState.getCarState(0);

                if (cCurrentState.projectileCount != cPrevState.projectileCount || cCurrentState.nitroChargeCount != cPrevState.nitroChargeCount ||
                        cCurrentState.oilCanisterCount != cPrevState.oilCanisterCount/* || cCurrentState.durability != cPrevState.durability*/ ||
                        currentState.score != prevState.score) {
                    return true;
                }
            }
            return false;
        }

        protected void updatePrediction(Car car, GameData data) {
            int tick = data.world.getTick() - startTick;
            WorldEngine.State<UnitState.CarState> wState = predictStates[tick];
            CarEngine<UnitState.CarState> cEngine = engine.getCarEngine(0);
            UnitState.CarState carState = wState.getCarState(0);
            if (data.move.getEnginePower() != cEngine.getEnginePowerAim() || data.move.getWheelTurn() != cEngine.getWheelTurnAim() || data.move.isBrake() != cEngine.isBreakPedal()) {
                double minEP = carState.enginePower - cEngine.enginePowerPerTick;
                double maxEP = carState.enginePower + cEngine.enginePowerPerTick;
                double minWT = carState.wheelTurn - cEngine.wheelTurnPerTick;
                double maxWT = carState.wheelTurn + cEngine.wheelTurnPerTick;
                double realEP = Math.min(maxEP, Math.max(data.move.getEnginePower(), minEP));
                double realWT = Math.min(maxWT, Math.max(data.move.getWheelTurn(), minWT));
                double predictEP = Math.min(maxEP, Math.max(cEngine.getEnginePowerAim(), minEP));
                double predictWT = Math.min(maxWT, Math.max(cEngine.getWheelTurnAim(), minWT));

                if (realEP != predictEP || realWT != predictWT || data.move.isBrake() != cEngine.isBreakPedal()) {
                    engine.setState(wState);
                    cEngine.setEnginePowerAim(data.move.getEnginePower());
                    cEngine.setWheelTurnAim(data.move.getWheelTurn());
                    cEngine.setBreakPedal(data.move.isBrake());
                    cEngine.setUseNitro(data.move.isUseNitro());

                    collisionTick = -1;
                    Arrays.fill(predictStates, tick+1, predictStates.length, null);
                    for (int t=(tick+1);t<predictStates.length;t++) {
                        engine.perStep();
                        if (!cEngine.getState().collision) {
                            predictStates[t] = engine.getState().copy();
                        } else {
                            if (collisionTick < 0) {
                                collisionTick = t;
                                System.out.println("New Collision on "+(startTick + collisionTick));
                            }
                            break;
                        }
                    }
                }
            }
        }

        @Override
        protected boolean check(Car car, GameData data) {
            int t = data.world.getTick() - startTick;
            WorldEngine.State<UnitState.CarState> wState = predictStates[t];
            UnitState.CarState statePredict = wState.getCarState(0);
            logState(data.world.getTick(), t, statePredict, car);

            if (data.world.getProjectiles().length > 0) {
                Projectile projectile = data.world.getProjectiles()[0];
                WorldEngine.ProjectileState pState = wState.projectileStates[0];
                if (pState != null) {
                    String info = "PR: " + data.world.getTick() + ", " + t + ": pErrX: " + (pState.x - projectile.getX()) + "| pErrY: " + (pState.y - projectile.getY()) + "| pErrVa: " + (pState.Va*CarEngine.SUB_STEPS - projectile.getAngularSpeed());
                    System.out.println(info);
                }
            }

            return true;
        }

        private void logState(int tick, int t, UnitState.CarState statePredict, Car car) {
            double Vl = statePredict.ax * statePredict.Vx + statePredict.ay * statePredict.Vy;
            double Vaw = statePredict.wheelTurn * angularSpeedFactor * Vl;

            String info = tick + ", "+t + ": pErrX: "+(statePredict.x - car.getX()) + "| pErrY: "+(statePredict.y - car.getY()) +
                    ": pVErrX: "+(statePredict.Vx*CarEngine.SUB_STEPS - car.getSpeedX()) + "| pVErrY: "+(statePredict.Vy*CarEngine.SUB_STEPS - car.getSpeedY()) +
                    ": pErrA: "+(Math.acos(statePredict.ax) - car.getAngle()) + "| pVErrA: "+((statePredict.Va + Vaw)*CarEngine.SUB_STEPS - car.getAngularSpeed()) +
//                    ": pErrEp: "+(statePredict.enginePower - car.getEnginePower()) + "| pErrWt("+car.getWheelTurn()+"): "+(statePredict.wheelTurn - car.getWheelTurn());
                    ": pErrNitro: "+(statePredict.nitroChargeCount - car.getNitroChargeCount()) + "| pErrOil: "+(statePredict.oilCanisterCount - car.getOilCanisterCount()) + "| pErrOil: "+(statePredict.projectileCount - car.getProjectileCount());

            System.out.println(info);
        }
    }

    public static abstract class Prophet<W, P extends Prediction<W>> {
        public final int predictionTicks;
        protected P prediction;

        protected Prophet(int predictionTicks) {
            this.predictionTicks = predictionTicks;
        }

        public void checkPredictions(W car, GameData data) {
            //prediction exist and not expired
            if (prediction != null && data.world.getTick() <= (prediction.startTick + prediction.predictionTicks)) {
                if (!prediction.check(car, data)) {
                    throw new RuntimeException("Prediction ERROR!");
                }
            }
        }

        public void updatePredictions(W car, GameData data) {
            if (prediction == null || data.world.getTick() > (prediction.startTick + prediction.predictionTicks)) {
                P newPrediction = makePrediction(car, data);
                if (newPrediction != null) {
                    prediction = newPrediction;
                }
            }
        }

        protected abstract P makePrediction(W car, GameData data);
    }

    public static abstract class Prediction<W> {
        public final int startTick;
        public final int predictionTicks;

        protected Prediction(int startTick, int predictionTicks) {
            this.predictionTicks = predictionTicks;
            this.startTick = startTick;
        }

        protected abstract boolean check(W car, GameData data);
    }
}
