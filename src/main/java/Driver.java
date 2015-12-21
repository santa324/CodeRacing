import model.*;

import java.util.*;

public class Driver {
    public static final int MIN_STATE_COUNT = 5000;

    public Driver teammateDriver;

    private ShiftedCarNode bestCarNode;
    private TileType[][] bestTileTypes;
    private Map<Long, OilSlick> bestOilSlicks = new HashMap<>();
    private boolean bestByBack;

    public CarNodeI findBestPath(Car self, GameData gameData, int[][] path, int nextWpIndex) {
        final int toUnfreezeTicks = gameData.game.getInitialFreezeDurationTicks() - gameData.world.getTick();
        CarTracker.CarPathEngine carEngine = new CarTracker.CarPathEngine(self, gameData, path, (short)nextWpIndex) {
            @Override
            public void perStepPre() {
                super.perStepPre();

                if (getState().tick < toUnfreezeTicks) {
                    this.Fx = 0;
                    this.Fy = 0;
                }
            }
        };

        CarTracker.WEngine engine = new CarTracker.WEngine(new CarTracker.CarPathEngine[] {carEngine}, gameData, bestOilSlicks.values().toArray(new OilSlick[bestOilSlicks.size()]), getTeammateBestPath());
        engine.setState(new CarTracker.WState(new CarTracker.CState[] {carEngine.getState()}, gameData, gameData.world.getProjectiles()));
        CarTracker.WState startState = engine.getState().copy();

        Comparator<CarNodeI> nodesComparator = new CarNodeQualityComparator();

        if (bestCarNode != null) {
            if (refreshSurroundings(gameData) || !bestCarNode.cut(startState.copy())) {
                bestCarNode = null;
            }
        }


//        VisualClient.getVisualClient().beginPost();
//        VisualClient.getVisualClient().printFog(gameData.world.getTilesXY(), gameData.tileTypes, gameData);
//        VisualClient.getVisualClient().printWayPoints(self, gameData.world, gameData.game);


//        engine.setResolveBorderCollisions(true);
//        CarTracker.CarNode.TreeStrategy treeStrategy = new CarTracker.CarNode.DistTreeStrategy();
        engine.setResolveBorderCollisions(false);
        CarTracker.CarNode.TreeStrategy treeStrategy = new CarTracker.CarNode.StateTreeStrategy();


        int sCount = getTimeLimitedStateCount(gameData.world, 12000);
//        System.out.println(gameData.world.getTick() + ": " + sCount);

        boolean allowNitro = toUnfreezeTicks <= 0 && bestCarNode != null && bestCarNode.getMaxDist() > 7;
        CarTracker.CarNode bNode = buildBestPath(450, Math.max(MIN_STATE_COUNT, sCount), treeStrategy, startState.copy(), gameData, nodesComparator, engine, 0.7, false, allowNitro);
        if (bestCarNode == null || bestCarNode.nodesComparator.compare(bestCarNode, bNode) < 0) {
            bestCarNode = new ShiftedCarNode(bNode, startState.copy(), nodesComparator);
            bestByBack = false;
        }

        if (bestCarNode.getMaxDist() < 2 || bestByBack) {
            nodesComparator = new Comparator<CarNodeI>() {
                @Override
                public int compare(CarNodeI n1, CarNodeI n2) {
                    return Double.compare(n1.getMaxRealWorth(), n2.getMaxRealWorth());
                }
            };

            CarTracker.CarNode bNode1 = buildBestPath(300, Math.max(MIN_STATE_COUNT, (int) (sCount * 0.9)), treeStrategy, startState.copy(), gameData, nodesComparator, engine, 0.5, true, false);
            if (nodesComparator.compare(bestCarNode, bNode1) < 0) {
                bestCarNode = new ShiftedCarNode(bNode1, startState.copy(), nodesComparator);
                bestByBack = true;
            }

            if (sCount > MIN_STATE_COUNT) {
                engine.setResolveBorderCollisions(true);
                bNode1 = buildBestPath(200, (int) (sCount * 0.5), treeStrategy, startState.copy(), gameData, nodesComparator, engine, 2.0, true, false);
                if (nodesComparator.compare(bestCarNode, bNode1) < 0) {
                    bestCarNode = new ShiftedCarNode(bNode1, startState.copy(), nodesComparator);
                    bestByBack = true;
                }
            }
        } else {
            if (sCount > MIN_STATE_COUNT) {
                bNode = buildBestPath(450, sCount, treeStrategy, startState.copy(), gameData, nodesComparator, engine, 2.0, false, allowNitro);
                if (nodesComparator.compare(bestCarNode, bNode) < 0) {
                    bestCarNode = new ShiftedCarNode(bNode, startState.copy(), nodesComparator);
                    bestByBack = false;
                }

                engine.setResolveBorderCollisions(true);
                treeStrategy = new CarTracker.CarNode.DistTreeStrategy();
                CarTracker.CarNode bNode1 = buildBestPath(300, (int) (sCount * 0.75), treeStrategy, startState.copy(), gameData, nodesComparator, engine, 0.7, false, allowNitro);
                if (nodesComparator.compare(bestCarNode, bNode1) < 0) {
                    bestCarNode = new ShiftedCarNode(bNode1, startState.copy(), nodesComparator);
                    bestByBack = false;
                }
            }

//            VisualClient.getVisualClient().printTracks(bNode1, startState.getCarState(0), null, nodesComparator);
        }

//        if (CarType.BUGGY.equals(self.getType())) {
//            VisualClient.getVisualClient().printTracks(bestCarNode.node, startState.getCarState(0), null, nodesComparator);
//        }
//        VisualClient.getVisualClient().endPost();

        this.bestTileTypes = gameData.tileTypes;
        return bestCarNode;
    }

    public ShiftedCarNode getBestCarNode() {
        return bestCarNode;
    }

    public CarTracker.WState[] getTeammateBestPath() {
        if (teammateDriver.bestCarNode != null)
            return getBestTrack(teammateDriver.bestCarNode);

        return new CarTracker.WState[] {};
    }

    public static CarTracker.WState[] getBestTrack(ShiftedCarNode bestNode) {
        List<CarTracker.WState> bestTrack = new ArrayList<>(300);
        bestTrack.add(bestNode.prevState);

        CarTracker.CarNode node = bestNode.node;
        while (node != null) {
            for (int i=0;i<node.trunk.length;i++) {
                bestTrack.add(node.trunk.get(i));
            }

            if (node.children == null || node.children.size() == 0)
                break;

            node = Collections.max(node.children, bestNode.nodesComparator);
        }
        return bestTrack.toArray(new CarTracker.WState[bestTrack.size()]);
    }

    /**
     * @return true - если окружение изменилось
     */
    private boolean refreshSurroundings(GameData gameData) {
        boolean changed = false;

        Boolean nodeResult = nodeSurroundingChanged(bestCarNode.node, gameData);
        if (nodeResult != null) {
            if (nodeResult)
                return true;

            bestCarNode.node.update(false);
        }


        Map<Long, OilSlick> newSlicks = new HashMap<>();
        for (OilSlick slick : gameData.world.getOilSlicks()) {
            newSlicks.put(slick.getId(), slick);
            OilSlick oldSlick = this.bestOilSlicks.remove(slick.getId());
            if (oldSlick == null || (oldSlick.getRemainingLifetime() - 1) != slick.getRemainingLifetime()) {
                changed = true;
                break;
            }
        }

        if (!changed) {
            for (OilSlick slick : this.bestOilSlicks.values()) {
                if (slick.getRemainingLifetime() > 1) {
                    changed = true;
                    break;
                }
            }
        }

        this.bestOilSlicks = newSlicks;

        return changed;
    }

    private Boolean nodeSurroundingChanged(CarTracker.CarNode node, GameData gameData) {
        for (int i = 0; i < node.trunk.length; i++) {
            CarTracker.WState state = node.trunk.get(i);
            if (isStateSurroundingChanged(state, gameData))
                return true;
        }

        if (node.children != null) {
            boolean needUpdate = false;
            Iterator<CarTracker.CarNode> childrenIterator = node.children.iterator();
            while (childrenIterator.hasNext()) {
                CarTracker.CarNode child = childrenIterator.next();
                Boolean childResult = nodeSurroundingChanged(child, gameData);
                if (childResult != null) {
                    if (childResult) {
                        if (node instanceof CarTracker.CarNodeOiled)
                            return true;

                        childrenIterator.remove();
                    }

                    needUpdate = true;
                }
            }

            if (needUpdate) {
                if (node.children.size() == 0)
                    node.children = null;

                node.update(false);
                return false;
            }
        }

        return null;
    }

    private boolean isStateSurroundingChanged(CarTracker.WState state, GameData gameData) {
        CarTracker.CState cState = state.getCarState(0);

        //todo
        int tX = (int)(cState.x * gameData.tSizeInv);
        int tY = (int)(cState.y * gameData.tSizeInv);
        if (!gameData.tileTypes[tX][tY].equals(bestTileTypes[tX][tY]))
            return true;

        return false;
    }

    public static int getTimeLimitedStateCount(World world, int baseCount) {
        int tCars = 0;
        int tActiveCars = 0;
        for (Car car : world.getCars()) {
            if (car.isTeammate()) {
                tCars++;
                if (!car.isFinishedTrack()) {
                    tActiveCars++;
                }
            }
        }
        double carScale = (1.0 * tCars) / tActiveCars;
        double scale = Math.min(ResourcesController.getGameRealTimeScale(world), ResourcesController.getGameCPUTimeScale(world));
        scale *= carScale;

        scale = 2.0/(1.0 + Math.exp(-5*scale)) - 1.0;

        return (int)(baseCount * scale);
    }

    public static CarTracker.CarNode buildBestPath(int maxLen, int sCount, CarTracker.CarNode.TreeStrategy treeStrategy, CarTracker.WState startState, GameData data, Comparator<CarNodeI> nodesComparator, CarTracker.WEngine engine, double minBreakSpeed, boolean allowBack, boolean allowNitro) {
        engine.setState(startState);
        CarTracker carTracker = new CarTracker(treeStrategy, engine, maxLen, minBreakSpeed, allowBack, allowNitro);
        CarTracker.CarNode root = carTracker.buildRoot(data.game, allowBack, allowNitro);

        int bCount = sCount / 20;

        int count = 0;
        for (int i=0;i<(2*sCount/100);i++) {
            if (root.getStateCount() > sCount || root.branchCount > bCount)
                break;

            if (ResourcesController.isMoveTimeThresholdExceeded()) {
                break;
            }
            if (sCount > MIN_STATE_COUNT && (count% 10 == 0) && ResourcesController.isMemoryThresholdExceeded()) {
                break;
            }

            count++;
            carTracker.track(root, 0);
        }

//        VisualClient.getVisualClient().printTracks(root, startState.getCarState(0), null, nodesComparator);

        CarTracker.CarNode bNode = null;
        for (CarTracker.CarNode child : root.children) {
            if (bNode == null || nodesComparator.compare(bNode, child) <= 0) {
                bNode = child;
            }
        }

        return bNode;
    }

    public static class CarNodeQualityComparator implements Comparator<CarNodeI> {
        @Override
        public int compare(CarNodeI n1, CarNodeI n2) {
            return Double.compare(n1.getMaxWorth(), n2.getMaxWorth());
        }
    }

    public static class ShiftedCarNode implements CarNodeI {
        private CarTracker.CarNode node;
        private int tickPassedTrunk;
        private CarTracker.WState prevState;
        private Comparator<CarNodeI> nodesComparator;

        public ShiftedCarNode(CarTracker.CarNode node, CarTracker.WState prevState, Comparator<CarNodeI> nodesComparator) {
            this.node = node;
            this.prevState = prevState;
            this.nodesComparator = nodesComparator;
        }

        public boolean cut(CarTracker.WState currentState) {
            while (node.trunk.length == 0) {
                if (node.children == null)
                    return false;

                node = Collections.max(node.children, nodesComparator);
                tickPassedTrunk = 0;
            }

            CarTracker.WState pState = node.trunk.get(0);
            if (pState.nearEquals(currentState, 1.0E-8)) {
                if (node.trunk.length == 1) {
                    if (node.children == null)
                        return false;

                    node = Collections.max(node.children, nodesComparator);
                    tickPassedTrunk = 0;
                } else {
                    node.trunk = new Tracker.ArrayView<>(node.trunk, 1);
                    node.prevState = pState;
                    node.update(true);
                    tickPassedTrunk++;
                }

                prevState = pState;
                return true;
            }

            return false;
        }

        @Override
        public double getEnginePower() {
            return node.getEnginePower();
        }

        @Override
        public double getWheelTurn() {
            return node.getWheelTurn();
        }

        @Override
        public boolean isBreakPedal() {
            return node.breakStopTick > tickPassedTrunk && node.isBreakPedal();
        }

        @Override
        public boolean isUseNitro() {
            return node.isUseNitro();
        }

        @Override
        public int getStateCount() {
            return node.getStateCount() - tickPassedTrunk;
        }

        @Override
        public int getMaxDist() {
            return node.getMaxDist();
        }

        @Override
        public double getMaxWorth() {
            return node.getMaxWorth();
        }

        @Override
        public double getMaxRealWorth() {
            return node.getMaxRealWorth();
        }
    }
}
