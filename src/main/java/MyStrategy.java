import model.*;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class MyStrategy implements Strategy {
    private static final TestEngine testEngine = new TestEngine(100);

    private static final EnumMap<CarType, Driver> myDrivers = new EnumMap<CarType, Driver>(CarType.class) {{
        Driver buggyDriver = new Driver();
        Driver jeepDriver = new Driver();
        buggyDriver.teammateDriver = jeepDriver;
        jeepDriver.teammateDriver = buggyDriver;

        put(CarType.BUGGY, buggyDriver);
        put(CarType.JEEP, jeepDriver);
    }};

    private Map<Long, UnitState.CarState> prevStates;
    private int startEmergencyTick;

    @Override
    public void move(Car self, World world, Game game, Move move) {
        try {
            ResourcesController.startMove(world, 0.8, 0.8, 0.8);

            myMove(self, world, game, move);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ResourcesController.endMove();
        }
    }


    private void myMove(Car self, World world, Game game, Move move) {
        //todo некорректное сравнение состояния бонусов
        //todo не сбрасывать лучший путь если изменения его не затрагивают, сбрасывать частично если частично затрагивают
        //todo другая схема поиска пути при обнулении лучшего пути (возможно рандомизированная)
        //todo дорабатывать лучший путь каждый тик а не заного искать лучший (с принудительным ветвлением в текущем тике)

        if (self.isFinishedTrack())
            return;

        if (prevStates == null) {
            prevStates = new HashMap<>();
            for (Car car : world.getCars()) {
                prevStates.put(car.getId(), new UnitState.CarState(car, car.getAngularSpeed(), game));
            }
        }

        GameData gameData = new GameData(self, world, game, move, prevStates);
//        testEngine.checkPredictions(self, gameData);

//        if (world.getTick() == 230)
//            move.setThrowProjectile(true);
//
//        if (world.getTick() < 270) {
//            move.setEnginePower(1.0);
//            move.setWheelTurn(0.2);
//        } else if (world.getTick() < 600) {
//            move.setEnginePower(1.0);
//            move.setWheelTurn(1.0);
//        }


//        if (world.getTick() < 600) {
//            if (world.getTick() < 350) {
//                move.setEnginePower(1.0);
//                move.setWheelTurn(1.0);
//            } else if (world.getTick() < 400) {
//                move.setEnginePower(-1.0);
//                move.setWheelTurn(1.0);
//                move.setBrake(true);
//            } else if (world.getTick() < 470) {
//                move.setEnginePower(-1.0);
//                move.setWheelTurn(-1.0);
//                move.setBrake(false);
//            } else if (world.getTick() < 510) {
//                move.setEnginePower(1.0);
//                move.setWheelTurn(1.0);
//                move.setBrake(true);
//            } else {
//                move.setEnginePower(1.0);
//                move.setWheelTurn(1.0);
//                move.setBrake(false);
//            }
//        } else {

            int[][] path = GameUtil.findPath(self.getNextWaypointIndex(), gameData);

            Driver driver = myDrivers.get(self.getType());
            CarNodeI bestNode = driver.findBestPath(self, gameData, path, self.getNextWaypointIndex());

            if (bestNode.getMaxDist() > 1) {
                move.setWheelTurn(bestNode.getWheelTurn());
                move.setEnginePower(bestNode.getEnginePower());
                move.setBrake(bestNode.isBreakPedal());
                move.setUseNitro(bestNode.isUseNitro());

                startEmergencyTick = -1;
            } else {
                startEmergencyTick++;
                if (startEmergencyTick < 50) {
                    move.setWheelTurn(0.0);
                    move.setEnginePower(0.0);
                    move.setBrake(true);
                } else {
                    move.setBrake(false);
                    int smt = startEmergencyTick - 40;
                    if (smt % 400 < 100) {
                        move.setWheelTurn(1.0);
                        move.setEnginePower(-1.0);
                    } else if (smt % 400 < 200) {
                        move.setWheelTurn(-1.0);
                        move.setEnginePower(-1.0);
                    } else if (smt % 400 < 300) {
                        move.setWheelTurn(1.0);
                        move.setEnginePower(1.0);
                    } else {
                        move.setWheelTurn(-1.0);
                        move.setEnginePower(1.0);
                    }
                }
            }

            int sCount = Driver.getTimeLimitedStateCount(gameData.world, 8000);
            if (world.getTick() > (game.getInitialFreezeDurationTicks() + 50) && sCount > Driver.MIN_STATE_COUNT) {
                double invTSize = 1.0 / game.getTrackTileSize();
                int selfX = (int)(self.getX() * invTSize);
                int selfY = (int)(self.getY() * invTSize);

                Projectile[] normalProjectles = world.getProjectiles();
                OilSlick[] normalSlicks = world.getOilSlicks();

                OilSlick[] possibleSlicks = normalSlicks;
                if (self.getOilCanisterCount() > 0 && self.getRemainingOilCooldownTicks() <= 0) {
                    double mySlickX = self.getX() - (game.getOilSlickRadius() + game.getOilSlickInitialRange() + self.getWidth() / 2) * Math.cos(self.getAngle());
                    double mySlickY = self.getY() - (game.getOilSlickRadius() + game.getOilSlickInitialRange() + self.getWidth() / 2) * Math.sin(self.getAngle());
                    OilSlick mySlick = new OilSlick(899, 0, mySlickX, mySlickY, 0, 0, 0, 0, game.getOilSlickRadius(), game.getOilSlickLifetime());

                    possibleSlicks = new OilSlick[normalSlicks.length + 1];
                    System.arraycopy(normalSlicks, 0, possibleSlicks, 0, normalSlicks.length);
                    possibleSlicks[normalSlicks.length] = mySlick;
                }

                Projectile[] possibleProjectles = normalProjectles;
                if (self.getProjectileCount() > 0 && self.getRemainingProjectileCooldownTicks() <= 0) {
                    if (CarType.JEEP.equals(self.getType())) {
                        Util.Vector2 tireV = new Util.Vector2(self.getAngle()).mul(game.getTireInitialSpeed());
                        Projectile myTire = new Projectile(799, game.getTireMass(), self.getX(), self.getY(), tireV.getX(), tireV.getY(), 0, 0, game.getTireRadius(), self.getId(), self.getPlayerId(), ProjectileType.TIRE);

                        possibleProjectles = new Projectile[normalProjectles.length + 1];
                        System.arraycopy(normalProjectles, 0, possibleProjectles, 0, normalProjectles.length);
                        possibleProjectles[normalProjectles.length] = myTire;
                    } else {
                        Util.Vector2 washerV = new Util.Vector2(self.getAngle()).mul(game.getWasherInitialSpeed());
                        Projectile myWasher = new Projectile(997, game.getWasherMass(), self.getX(), self.getY(), washerV.getX(), washerV.getY(), 0, 0, game.getWasherRadius(), self.getId(), self.getPlayerId(), ProjectileType.WASHER);

                        Util.Vector2 washerSideAngle = new Util.Vector2(game.getSideWasherAngle());
                        Util.Vector2 washerV1 = new Util.Vector2(self.getAngle()).rotate(washerSideAngle).mul(game.getWasherInitialSpeed());
                        Projectile myWasher1 = new Projectile(998, game.getWasherMass(), self.getX(), self.getY(), washerV1.getX(), washerV1.getY(), 0, 0, game.getWasherRadius(), self.getId(), self.getPlayerId(), ProjectileType.WASHER);

                        Util.Vector2 washerV2 = new Util.Vector2(self.getAngle()).rotateBack(washerSideAngle).mul(game.getWasherInitialSpeed());
                        Projectile myWasher2 = new Projectile(999, game.getWasherMass(), self.getX(), self.getY(), washerV2.getX(), washerV2.getY(), 0, 0, game.getWasherRadius(), self.getId(), self.getPlayerId(), ProjectileType.WASHER);

                        possibleProjectles = new Projectile[normalProjectles.length + 3];
                        System.arraycopy(normalProjectles, 0, possibleProjectles, 0, normalProjectles.length);
                        possibleProjectles[normalProjectles.length] = myWasher;
                        possibleProjectles[normalProjectles.length + 1] = myWasher1;
                        possibleProjectles[normalProjectles.length + 2] = myWasher2;
                    }
                }

                for (Car car : world.getCars()) {
                    if (car.isTeammate() || car.isFinishedTrack())
                        continue;

                    {
                        int maxLen = 150;
                        int maxSCount = 1500;
                        CarTracker.CarNode normalTrack = null;

                        int[] toCarPath = GameUtil.findPath((int) (car.getX() * invTSize), (int) (car.getY() * invTSize), gameData);

                        if (self.getOilCanisterCount() > 0 && self.getRemainingOilCooldownTicks() <= 0) {
                            final double maxCarSpeed = 35;
                            int maxDist = (int)Math.ceil(maxLen*maxCarSpeed*invTSize);
                            if (toCarPath[GameUtil.getTIndex(selfX, selfY, world.getWidth())] <= maxDist) {
                                normalTrack = buildTrack(car, path, gameData, normalSlicks, normalProjectles, maxSCount, maxLen, true);

                                CarTracker.CarNode possibleTrack = buildTrack(car, path, gameData, possibleSlicks, world.getProjectiles(), maxSCount, maxLen, true);
                                double worthLost = normalTrack.getMaxWorth() - possibleTrack.getMaxWorth();
                                if (worthLost > 1_300_000) {
                                    move.setSpillOil(true);
                                    break;
                                }
                            }
                        }

                        if (CarType.JEEP.equals(self.getType()) && self.getProjectileCount() > 0 && self.getRemainingProjectileCooldownTicks() <= 0) {
                            int maxDist = (int)Math.ceil(maxLen*game.getTireInitialSpeed()*invTSize);
                            double distToSelf = Math.sqrt((self.getX() - car.getX())*(self.getX() - car.getX()) + (self.getY() - car.getY())*(self.getY() - car.getY()));
                            if (toCarPath[GameUtil.getTIndex(selfX, selfY, world.getWidth())] <= maxDist && distToSelf > 200) {
                                if (normalTrack == null)
                                    normalTrack = buildTrack(car, path, gameData, normalSlicks, normalProjectles, maxSCount, maxLen, true);

                                CarTracker.CarNode possibleTrack = buildTrack(car, path, gameData, normalSlicks, possibleProjectles, maxSCount, maxLen, true);
                                double worthLost = normalTrack.getMaxWorth() - possibleTrack.getMaxWorth();
                                if (worthLost > 2_500_000) {
                                    move.setThrowProjectile(true);
                                    break;
                                }
                            }
                        }
                    }

                    if (CarType.BUGGY.equals(self.getType()) && self.getProjectileCount() > 0 && self.getRemainingProjectileCooldownTicks() <= 0){
                        int maxLen = 50;
                        int maxSCount = 800;

                        int minWasherHit = 3;
                        if (self.getProjectileCount() > 2)
                            minWasherHit = 2;
                        double minWorthLost = (minWasherHit - 1) * CarTracker.CarNode.DAMAGE_WORTH * game.getWasherDamage();

                        double maxDist = maxLen*game.getWasherInitialSpeed() + 150;
                        if ((self.getX() - car.getX()) < maxDist && (self.getY() - car.getY()) < maxDist) {
                            CarTracker.CarNode normalTrack = buildTrack(car, path, gameData, normalSlicks, normalProjectles, maxSCount, maxLen, false);
                            CarTracker.CarNode possibleTrack = buildTrack(car, path, gameData, normalSlicks, possibleProjectles, maxSCount, maxLen, false);
                            double worthLost = normalTrack.getMaxWorth() - possibleTrack.getMaxWorth();
                            if (worthLost > minWorthLost)  {
                                move.setThrowProjectile(true);
                                break;
                            }
                        }
                    }
                }

                if (move.isThrowProjectile()) {
                    for (Car car : world.getCars()) {
                        if (!car.isTeammate() || car.isFinishedTrack())
                            continue;

                        CarTracker.CarPathEngine carEngine = new CarTracker.CarPathEngine(car, gameData, path, (short) car.getNextWaypointIndex());
                        CarTracker.WEngine engine = new CarTracker.WEngine(new CarTracker.CarPathEngine[]{carEngine}, gameData, normalSlicks, driver.getTeammateBestPath());
                        engine.setResolveBorderCollisions(false);
                        CarTracker.WState startState = new CarTracker.WState(new CarTracker.CState[]{carEngine.getState()}, gameData, possibleProjectles);
                        CarTracker.CarNode posibleTeammateTrack = Driver.buildBestPath(400, sCount, new CarTracker.CarNode.DistTreeStrategy(), startState, gameData, new Driver.CarNodeQualityComparator(), engine, 2.0, false, true);
                        double teammateWorthLost = bestNode.getMaxWorth() - posibleTeammateTrack.getMaxWorth();
                        if (teammateWorthLost > 1_000_000) {
                            move.setThrowProjectile(false);
                            break;
                        }
                    }
                }
            }
//        }


        Map<Long, UnitState.CarState> newPrevStates = new HashMap<>();
        for (Car car : world.getCars()) {
            UnitState.CarState pState = prevStates.get(car.getId());
            if (pState == null)
                pState = new UnitState.CarState(car, car.getAngularSpeed(), game);
            newPrevStates.put(car.getId(), new CarEngine<>(car, gameData).createState(self, pState, game));
        }
        this.prevStates = newPrevStates;
//        testEngine.updatePredictions(self, gameData);
    }


    private CarTracker.CarNode buildTrack(Car car, int[][] path, GameData data, OilSlick[] oilSlicks, Projectile[] projectiles, int sCount, int len, boolean resolveCollisions) {
        CarTracker.CarPathEngine carEngine = new CarTracker.CarPathEngine(car, data, path, (short)car.getNextWaypointIndex());
        CarTracker.WEngine engine = new CarTracker.WEngine(new CarTracker.CarPathEngine[] {carEngine}, data, oilSlicks, new CarTracker.WState[0]);
        engine.setState(new CarTracker.WState(new CarTracker.CState[] {carEngine.getState()}, data, projectiles));

        engine.setResolveBorderCollisions(resolveCollisions);
        CarTracker.CarNode.TreeStrategy treeStrategy = resolveCollisions ? new CarTracker.CarNode.DistTreeStrategy() : new CarTracker.CarNode.StateTreeStrategy();

        CarTracker carTracker = new CarTracker(treeStrategy, engine, len, 1.0, false, false);
        CarTracker.CarNode root = carTracker.buildRoot(data.game, false, false);

        for (int i=0;i<(sCount/20);i++) {
            if (root.getStateCount() > sCount || root.branchCount > (sCount/10))
                break;

            carTracker.track(root, 0);
        }

        return root;
    }
}
