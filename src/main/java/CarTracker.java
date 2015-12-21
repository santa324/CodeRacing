import model.Car;
import model.Game;
import model.OilSlick;
import model.Projectile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CarTracker extends Tracker<CarTracker.WState, CarTracker.CarNode> {
    private final CarNode.TreeStrategy treeStrategy;

    private final WEngine engine;
    private final int carIndex;
    public final WState startState;
    private final int trackLen;
    private final boolean allowBack, allowNitro;
    private final double minBreakSpeed;

    public CarTracker(CarNode.TreeStrategy treeStrategy, WEngine engine, int trackLen, double minBreakSpeed, boolean allowBack, boolean allowNitro) {
        this.treeStrategy = treeStrategy;
        this.engine = engine;
        this.startState = engine.getState().copy();
        this.engine.setState(this.startState);
        this.carIndex = 0;
        this.trackLen = trackLen;
        this.allowBack = allowBack;
        this.allowNitro = allowNitro;
        this.minBreakSpeed = minBreakSpeed;
    }

    public CarNode buildRoot(Game game) {
        return buildRoot(game, allowBack, allowNitro);
    }

    public CarNode buildRoot(Game game, boolean allowBack, boolean allowNitro) {
        int stateCount = 0;
        List<CarNode> nodes = new ArrayList<>();

        CarNode rightNode = buildTrunkNode(1.0, 1.0, false, false, startState, trackLen);
        nodes.add(rightNode);
        stateCount += rightNode.stateCount;

        CarNode leftNode = buildTrunkNode(-1.0, 1.0, false, false, startState, trackLen);
        nodes.add(leftNode);
        stateCount += leftNode.stateCount;

        CarNode node = buildTrunkNode(0.0, 1.0, false, false, startState, trackLen);
        nodes.add(node);
        stateCount += node.stateCount;

        CState startCState = startState.getCarState(carIndex);
        if (startCState.wheelTurn < 0.99 && startCState.wheelTurn > -0.99 && Math.abs(startCState.wheelTurn) > 0.1) {
            CarNode normalNode = buildTrunkNode(startCState.wheelTurn, 1.0, false, false, startState, trackLen);
            nodes.add(normalNode);
            stateCount += normalNode.stateCount;
        }

        if (allowNitro && startCState.nitroChargeCount > 0 && startCState.lastNitroUseTickPass >= game.getUseNitroCooldownTicks()) {
            CarNode rightNodeNitro = buildTrunkNode(1.0, 1.0, false, true, startState, trackLen);
            nodes.add(rightNodeNitro);
            stateCount += rightNodeNitro.stateCount;

            CarNode leftNodeNitro = buildTrunkNode(-1.0, 1.0, false, true, startState, trackLen);
            nodes.add(leftNodeNitro);
            stateCount += leftNodeNitro.stateCount;

            CarNode nodeNitro = buildTrunkNode(0.0, 1.0, false, true, startState, trackLen);
            nodes.add(nodeNitro);
            stateCount += nodeNitro.stateCount;

            if (startCState.wheelTurn < 0.99 && startCState.wheelTurn > -0.99 && Math.abs(startCState.wheelTurn) > 0.1) {
                CarNode normalNodeNitro = buildTrunkNode(startCState.wheelTurn, 1.0, false, true, startState, trackLen);
                nodes.add(normalNodeNitro);
                stateCount += normalNodeNitro.stateCount;
            }
        }

        double Vl = Math.abs(startCState.ax * startCState.Vx + startCState.ay * startCState.Vy);
        if (/*allowBreak && */Vl > minBreakSpeed) {
            CarNode leftBreak = buildTrunkNode(1.0, 1.0, true, false, startState, trackLen);
            nodes.add(leftBreak);
            stateCount += leftBreak.stateCount;

            CarNode rightBreak = buildTrunkNode(-1.0, 1.0, true, false, startState, trackLen);
            nodes.add(rightBreak);
            stateCount += rightBreak.stateCount;
        }

        if (allowBack) {
            if (Vl > 0.3) {
                CarNode lineBreakBack = buildTrunkNode(0.0, -1.0, true, false, startState, trackLen);
                nodes.add(lineBreakBack);
                stateCount += lineBreakBack.stateCount;
            }
            CarNode leftBack = buildTrunkNode(1.0, -1.0, false, false, startState, trackLen);
            nodes.add(leftBack);
            stateCount += leftBack.stateCount;

            CarNode lineBack = buildTrunkNode(0.0, -1.0, false, false, startState, trackLen);
            nodes.add(lineBack);
            stateCount += lineBack.stateCount;

            CarNode rightBack = buildTrunkNode(-1.0, -1.0, false, false, startState, trackLen);
            nodes.add(rightBack);
            stateCount += rightBack.stateCount;
        }

        CarNode root = new CarNode(startState, 1.0, 0, false, Integer.MAX_VALUE, false);
        root.children = nodes;
        root.trunk = new ArrayView<>(new WState[0], 0);
        root.stateCount = stateCount;
        root.update(false);

        return root;
    }

    @Override
    protected CarNode buildNormalNode(CarNode parent, WState start, int trunkPoint) {
        return new CarNode(start, parent.getEnginePower(), parent.getWheelTurn(), parent.isBreakPedal(), parent.breakStopTick - trunkPoint, parent.useNitro);
    }

    @Override
    protected int selectTrunkPoint(CarNode node) {
        return treeStrategy.selectTrunkPoint(node);
    }

    @Override
    protected double getNodeValuation(CarNode node, CarNode parent) {
        return treeStrategy.getValuation(node, parent);
    }

    @Override
    protected boolean isTrunkTurn(CarNode node) {
        return treeStrategy.isTrunkTurn(node);
    }

    @Override
    protected CarNode[] buildTrunkChildren(CarNode parent, WState start, int rootLen) {
        int len = trackLen - rootLen;
        if (len <= 0)
            return null;

        CState cStart = start.getCarState(carIndex);

        CarNode[] result = new CarNode[10];
        int index = 0;
        if (parent.getWheelTurn() < 0.99 || parent.isBreakPedal()) {
            result[index++] = buildTrunkNode(1.0, 1.0, false, false, start, len);
        }
        if (parent.getWheelTurn() > -0.99 || parent.isBreakPedal()) {
            result[index++] = buildTrunkNode(-1.0, 1.0, false, false, start, len);
        }
        if (Math.abs(parent.getWheelTurn()) > 0.1 || parent.isBreakPedal()) {
            result[index++] = buildTrunkNode(0.0, 1.0, false, false, start, len);
        }

        if ((Math.abs(parent.getWheelTurn() - cStart.wheelTurn) > 0.1 || parent.isBreakPedal()) && Math.abs(cStart.wheelTurn) > 0.1 && cStart.wheelTurn < 0.99 && cStart.wheelTurn > -0.99) {
            result[index++] = buildTrunkNode(cStart.wheelTurn, 1.0, false, false, start, len);
        }


        double Vl = Math.abs(cStart.ax * cStart.Vx + cStart.ay * cStart.Vy);
        if (/*allowBreak && */!parent.isBreakPedal() && Vl > minBreakSpeed) {
            result[index++] = buildTrunkNode(1.0, 1.0, true, false, start, len);
//                result[index++] = buildTrunkNode(0.0, 1.0, true, start, len);
            result[index++] = buildTrunkNode(-1.0, 1.0, true, false, start, len);
        }

        if (allowBack/* && Vl < 1.0*/) {
            if (parent.getWheelTurn() < 0.9 || parent.getEnginePower() > -0.9)
                result[index++] = buildTrunkNode(1.0, -1.0, false, false, start, len);
            if (Math.abs(parent.getWheelTurn()) > 0.1 || parent.getEnginePower() > -0.9)
                result[index++] = buildTrunkNode(0.0, -1.0, false, false, start, len);
            if (parent.getWheelTurn() > -0.9 || parent.getEnginePower() > -0.9)
                result[index++] = buildTrunkNode(-1.0, -1.0, false, false, start, len);
        }

        return result;
    }

    public CarNode buildTrunkNode(double wt, double ep, boolean breakPedal, boolean useNitro, WState start, int len) {
        WState[] states = new WState[len];

        engine.setState(start.copy());
        CarPathEngine cEngine = engine.getCarEngine(carIndex);
        cEngine.setEnginePowerAim(ep);
        cEngine.setWheelTurnAim(wt);
        cEngine.setBreakPedal(breakPedal);
        cEngine.setUseNitro(useNitro);

        int smallLenTick = 0;
        CarNodeOiled oiledNode = null;
        int breakStopTick = Integer.MAX_VALUE;
        int index = 0;
        while (index < len) {
            CState cState = cEngine.getState();
            if (breakPedal) {
                double Vl = cState.ax * cState.Vx + cState.ay * cState.Vy;
                if (breakStopTick == Integer.MAX_VALUE && Vl < minBreakSpeed) {
                    cEngine.setBreakPedal(false);
                    breakStopTick = index;
                }
            }
            float prevLen = cState.trackLen;

            int prevROT = cState.remainingOiledTicks;
            engine.perStep();

            WState state = engine.getState().copy();
            states[index++] = state;

            if (cEngine.getState().collision)
                break;

            if (Math.abs(cEngine.getState().trackLen - prevLen) < 2.0) {
                smallLenTick++;
            } else {
                smallLenTick = 0;
            }
            if (smallLenTick > 40)
                break;


            int rot = cEngine.getState().remainingOiledTicks;
            if (index < len && rot > prevROT) {
                oiledNode = createOiledNode(state, wt, ep, breakPedal, useNitro, len - index, breakStopTick);
                break;
            }
        }

        CarNode node = new CarNode(start, ep, wt, breakPedal, breakStopTick, useNitro);
        node.trunk = new ArrayView<>(states, index);
        node.stateCount = node.trunk.length;
        node.branchCount = 1;

        if (oiledNode != null) {
            node.children = new ArrayList<>(1);
            node.children.add(oiledNode);
            node.stateCount += oiledNode.stateCount;
        }

        node.update(true);

        return node;
    }

    private static final double angularSpeedRandomFactor = 0.0023271056693257726D;
    private CarNodeOiled createOiledNode(WState start, double wt, double ep, boolean breakPedal, boolean useNitro, int len, int breakStopTick) {
        CState carState = start.getCarState(0);
        double rndVa = angularSpeedRandomFactor * Math.sqrt(carState.Vx*carState.Vx + carState.Vy*carState.Vy);

        CarNodeOiled nodeOiled = new CarNodeOiled(start, ep, wt, breakPedal, breakStopTick, useNitro);
        nodeOiled.children = new ArrayList<>(2);
        nodeOiled.trunk = new ArrayView<>(new WState[0], 0);

        WState c1StartState = start.copy();
        c1StartState.getCarState(0).Va += rndVa;
        nodeOiled.children.add(buildTrunkNode(wt, ep, breakPedal, useNitro, c1StartState, len));

        WState c2StartState = start.copy();
        c2StartState.getCarState(0).Va -= rndVa;
        nodeOiled.children.add(buildTrunkNode(wt, ep, breakPedal, useNitro, c2StartState, len));

        nodeOiled.update(true);

        return nodeOiled;
    }

    public static class CarNodeOiled extends CarNode {
        public CarNodeOiled(WState prevState, double ep, double wt, boolean breakPedal, int breakStopTick, boolean useNitro) {
            super(prevState, ep, wt, breakPedal, breakStopTick, useNitro);
        }

        @Override
        public void update(boolean updateTrunk) {
            this.stateCount = 0;
            this.branchCount = 0;
            this.maxWorth = 0;
            this.maxDist = Integer.MAX_VALUE;

            this.distWorth = new double[30];
            this.bestPathMaxDist = 0;
            this.eventWorth = 0;

            double scale = 1.0 / children.size();
            for (CarNode child : children) {
                this.branchCount += child.branchCount;
                this.stateCount += child.stateCount;

                //худший прогноз, чтобы не надеялся зря
                if (child.maxDist < this.maxDist)
                    this.maxDist = child.maxDist;

                this.maxWorth += child.maxWorth;

                this.eventWorth += scale * child.eventWorth;

                if (child.bestPathMaxDist > this.bestPathMaxDist)
                    this.bestPathMaxDist = child.bestPathMaxDist;

                for (int t=0;t<child.distWorth.length;t++) {
                    this.distWorth[t] += scale * child.distWorth[t];
                }
            }

            this.maxWorth /= this.children.size();
            this.maxWorth -= 400000;

            this.eventWorth -= 400000;
            this.distWorth = Arrays.copyOf(this.distWorth, this.bestPathMaxDist + 1);
            this.mDistWorth = this.distWorth[this.bestPathMaxDist];
        }
    }

//    public static class CarNodeOiled extends CarNode {
//        public CarNodeOiled(WState prevState, double ep, double wt, boolean breakPedal, int breakStopTick, boolean useNitro) {
//            super(prevState, ep, wt, breakPedal, breakStopTick, useNitro);
//        }
//
//        @Override
//        public void update(boolean updateTrunk) {
//            this.stateCount = 0;
//            this.branchCount = 0;
//            this.eventWorth = 0;
//            this.maxDist = 0;
//            this.distWorth = new double[30];
//
//            double scale = 1.0 / children.size();
//            for (CarNode child : children) {
//                this.branchCount += child.branchCount;
//                this.stateCount += child.stateCount;
//
//                this.eventWorth += scale * child.eventWorth;
//
//                if (child.maxDist > this.maxDist)
//                    this.maxDist = child.maxDist;
//
//                for (int t=0;t<child.distWorth.length;t++) {
//                    this.distWorth[t] += scale * child.distWorth[t];
//                }
//            }
//            this.eventWorth -= 400000;
//
//            this.distWorth = Arrays.copyOf(this.distWorth, this.maxDist + 1);
//            this.mDistWorth = this.distWorth[this.maxDist];
//        }
//    }

    public static class CarNode extends Tracker.Node<WState, CarNode> implements CarNodeI {
        public static final double K = 0.99;
        public static final double DAMAGE_WORTH = 20000000;

        protected WState prevState;
        protected final double ep, wt;
        protected final boolean breakPedal;
        protected final int breakStopTick;
        protected final boolean useNitro;

        protected int maxDist, bestPathMaxDist;
        protected int branchCount;
        protected double maxWorth;
        protected  double fullLen, trunkLen;
        protected double eventWorth, mDistWorth;
        protected double[] distWorth;

        public CarNode(WState prevState, double ep, double wt, boolean breakPedal, int breakStopTick, boolean useNitro) {
            this.prevState = prevState;
            this.ep = ep;
            this.wt = wt;
            this.breakPedal = breakPedal;
            this.breakStopTick = breakStopTick;
            this.useNitro = useNitro;
        }

        @Override
        public void update(boolean updateTrunk) {
            this.maxDist = 0;
            this.branchCount = 1;
            this.maxWorth = 0;
            this.trunkLen = 0;

            this.bestPathMaxDist = 0;
            this.distWorth = new double[30];
            this.eventWorth = 0;
            this.mDistWorth = 0;


            CState prevCState = prevState.getCarState(0);
            int lastTrunkDist = 0;

            double k = 1;
            if (trunk.length > 0) {
                CState lastTrunkCState = trunk.get(trunk.length - 1).getCarState(0);
                this.trunkLen = lastTrunkCState.trackLen - prevState.getCarState(0).trackLen;
                lastTrunkDist = lastTrunkCState.dist - prevCState.dist;

                double dWorth = 0;
                for (int i = 0; i < trunk.length; i++) {
                    k *= K;
                    CState cState = trunk.get(i).getCarState(0);

                    int dist = cState.dist - prevCState.dist;
                    if (dist > this.maxDist) {
                        double worth = k * 1000000 * (dist - this.maxDist);
                        double Vl = cState.ax * cState.Vx + cState.ay * cState.Vy;
                        if (Vl < 0)
                            worth *= 0.2;

                        dWorth += worth;

                        for (int t=this.maxDist;t<dist;t++) {
                            distWorth[t + 1] = dWorth;
                        }
                        this.maxDist = dist;
                    }
                }
                this.maxWorth = dWorth;
            }
            this.fullLen = this.trunkLen;
            this.bestPathMaxDist = this.maxDist;


            int cMaxDist = 0;
            if (children != null) {
                int worthlessDist = this.bestPathMaxDist - lastTrunkDist;

                double bestChildWorth = -Double.MAX_VALUE;
                CarNode bestChild = null;

                double maxCWorth = -Double.MAX_VALUE;
                for (CarNode child : children) {
                    branchCount += child.branchCount;
                    fullLen += child.fullLen;

                    int cDist = lastTrunkDist + child.maxDist;
                    if (cDist > cMaxDist)
                        cMaxDist = cDist;

                    if (child.maxWorth > maxCWorth)
                        maxCWorth = child.maxWorth;

                    double cWorth = child.eventWorth;
                    if (worthlessDist < child.distWorth.length)
                        cWorth += child.distWorth[child.distWorth.length - 1] - child.distWorth[worthlessDist];

                    if (cWorth > bestChildWorth) {
                        bestChildWorth = cWorth;
                        bestChild = child;
                    }
                }
                maxWorth += k*maxCWorth;

                if (cMaxDist > this.maxDist)
                    this.maxDist = cMaxDist;

                if (bestChild != null) {
                    this.eventWorth += k*bestChild.eventWorth;

                    double dWorth = this.distWorth[this.bestPathMaxDist];
                    int cOverDist = bestChild.distWorth.length - worthlessDist;
                    for (int t=1;t<cOverDist;t++) {
                        dWorth += k * (bestChild.distWorth[worthlessDist + t] - bestChild.distWorth[worthlessDist]);
                        this.distWorth[this.bestPathMaxDist + t] = dWorth;
                    }

                    int cMDist = lastTrunkDist + bestChild.bestPathMaxDist;
                    if (cMDist > this.bestPathMaxDist)
                        this.bestPathMaxDist = cMDist;
                }
            }

            this.distWorth = Arrays.copyOf(this.distWorth, this.bestPathMaxDist + 1);
            this.mDistWorth = this.distWorth[this.bestPathMaxDist];

            //--------------

            k = K;
            double trunkEventWorth = 0;
            WState pState = prevState;
            CState pcState = pState.getCarState(0);
            for (int i = 0; i < trunk.length; i++) {
                WState wState = trunk.get(i);
                CState cState = wState.getCarState(0); //todo hardcode index

                double worth = 0;
                worth += k * 100000*(wState.score - pState.score);
                worth += k * 50000 * 100 * ((cState.nitroChargeCount + cState.nitroChargeCountUsed) - (pcState.nitroChargeCount + pcState.nitroChargeCountUsed));
                worth += k * 15000 * 100 * ((cState.oilCanisterCount) - (pcState.oilCanisterCount));
                worth += k * 20000 * 100 * ((cState.projectileCount) - (pcState.projectileCount));

                double durabilityTaken = cState.durability - pcState.durability;
                if (durabilityTaken >= 0) {
                    worth += k * 70000 * 100 * durabilityTaken;
                } else {
                    trunkEventWorth += k * DAMAGE_WORTH * durabilityTaken;
                }

                if (worth > 0) {
                    int dist = cState.dist - prevCState.dist;
                    int maxPostDist = dist;
                    for (int j = i; j < trunk.length; j++) {
                        int dst = trunk.get(j).getCarState(0).dist - prevCState.dist;
                        if (dst > maxPostDist)
                            maxPostDist = dst;
                    }
                    if (cMaxDist > maxPostDist)
                        maxPostDist = cMaxDist;

                    double scale = 1 / (1 + Math.exp(-(2.0 * ((maxPostDist - dist) - 4.0))));

                    trunkEventWorth += scale * worth;
                }

                if (cState.collision)
                    trunkEventWorth -= k * 50000;

                if (wState.teammateCollisionV2 > 0) {
                    trunkEventWorth -= k * 10_000;
                    trunkEventWorth -= k * 100_000 * wState.teammateCollisionV2;
                }

                trunkEventWorth += k * 2500 * 100 * (pcState.nitroChargeCountUsed - cState.nitroChargeCountUsed);
                if (pcState.durability > 1.0E-3 && cState.durability < 1.0E-3)
                    trunkEventWorth -= k * 100_000_000D;

                k *= K;

                pState = wState;
                pcState = cState;
            }

            this.eventWorth += trunkEventWorth;
            this.maxWorth += trunkEventWorth;
        }

        @Override
        public double getEnginePower() {
            return ep;
        }

        @Override
        public double getWheelTurn() {
            return wt;
        }

        @Override
        public boolean isBreakPedal() {
            return breakPedal;
        }

        @Override
        public boolean isUseNitro() {
            return useNitro;
        }

        @Override
        public int getStateCount() {
            return stateCount;
        }

        @Override
        public int getMaxDist() {
            return maxDist;
        }

        @Override
        public double getMaxWorth() {
            return maxWorth;
        }

        @Override
        public double getMaxRealWorth() {
            return this.eventWorth + this.mDistWorth;
        }

        public interface TreeStrategy {
            double getValuation(CarNode node, CarNode parent);
            boolean isTrunkTurn(CarNode node);
            int selectTrunkPoint(CarNode node);
        }

        public static class StateTreeStrategy implements TreeStrategy {
            @Override
            public double getValuation(CarNode node, CarNode parent) {
//                return (1000 + maxWorth)*(1000 + maxWorth)*(1000 + maxWorth)* (maxDist + 1.0) / branchCount;
                return Math.sqrt(node.stateCount) * (1.0 + node.maxDist) *(100000 + node.maxWorth) / node.branchCount;
            }

            @Override
            public boolean isTrunkTurn(CarNode node) {
                return node.trunk.length > ((node.stateCount + 0.1) / node.branchCount);
            }

            @Override
            public int selectTrunkPoint(CarNode node) {
//            return random.nextInt(node.trunk.length - 1);
                return (node.trunk.length / 2) - 1;
            }
        }

        public static class DistTreeStrategy implements TreeStrategy {
            @Override
            public double getValuation(CarNode node, CarNode parent) {
                return node.fullLen * (1.0 + node.maxDist) *(100000 + node.maxWorth) / (Math.sqrt(node.stateCount)*node.branchCount);
            }

            @Override
            public boolean isTrunkTurn(CarNode node) {
                return (node.trunkLen) > (node.fullLen / (node.branchCount));
            }

            @Override
            public int selectTrunkPoint(CarNode node) {
                double halfLen = node.trunkLen * 0.5;
                CState prevCState = node.prevState.getCarState(0);

                int index = 0;
                while (index < (node.trunk.length - 1)) {
                    CState cState = node.trunk.get(index).getCarState(0);

                    if ((cState.trackLen - prevCState.trackLen) > halfLen)
                        return index;

                    index++;
                }
                return node.trunk.length - 2;
            }
        }
    }

//
//    public static class CarNode extends Tracker.Node<WState, CarNode> implements CarNodeI {
//        public static final double K = 0.99;
//        public static final double DAMAGE_WORTH = 20000000;
//
//        protected WState prevState;
//        protected final double ep, wt;
//        protected final boolean breakPedal;
//        protected final int breakStopTick;
//        protected final boolean useNitro;
//
//        protected int maxDist;
//        protected int branchCount;
//        protected  double fullLen, trunkLen;
//        protected double eventWorth, mDistWorth;
//        protected double[] distWorth;
//
//        public CarNode(WState prevState, double ep, double wt, boolean breakPedal, int breakStopTick, boolean useNitro) {
//            this.prevState = prevState;
//            this.ep = ep;
//            this.wt = wt;
//            this.breakPedal = breakPedal;
//            this.breakStopTick = breakStopTick;
//            this.useNitro = useNitro;
//        }
//
//        @Override
//        public void update(boolean updateTrunk) {
//            this.maxDist = 0;
//            this.branchCount = 1;
//            this.eventWorth = 0;
//            this.trunkLen = 0;
//            this.distWorth = new double[30];
//
//
//            CState prevCState = prevState.getCarState(0);
//            int lastTrunkDist = 0;
//
//            double k = 1;
//            if (trunk.length > 0) {
//                CState lastTrunkCState = trunk.get(trunk.length - 1).getCarState(0);
//                this.trunkLen = lastTrunkCState.trackLen - prevState.getCarState(0).trackLen;
//                lastTrunkDist = lastTrunkCState.dist - prevCState.dist;
//
//                double dWorth = 0;
//                for (int i = 0; i < trunk.length; i++) {
//                    k *= K;
//                    CState cState = trunk.get(i).getCarState(0);
//
//                    int dist = cState.dist - prevCState.dist;
//                    if (dist > this.maxDist) {
//                        double worth = k * 1000000 * (dist - this.maxDist);
//                        double Vl = cState.ax * cState.Vx + cState.ay * cState.Vy;
//                        if (Vl < 0)
//                            worth *= 0.2;
//
//                        dWorth += worth;
//
//                        for (int t=this.maxDist;t<dist;t++) {
//                            distWorth[t + 1] = dWorth;
//                        }
//                        this.maxDist = dist;
//                    }
//                }
//            }
//            fullLen = trunkLen;
//
//
//            int cMaxDist = 0;
//            if (children != null) {
//                int worthlessDist = this.maxDist - lastTrunkDist;
//
//                double maxCWorth = -Double.MAX_VALUE;
//                CarNode bestChild = null;
//                for (CarNode child : children) {
//                    branchCount += child.branchCount;
//                    fullLen += child.fullLen;
//
//
//                    double cWorth = child.eventWorth;
//                    if (worthlessDist < child.distWorth.length)
//                        cWorth += child.distWorth[child.distWorth.length - 1] - child.distWorth[worthlessDist];
//
//                    if (cWorth > maxCWorth) {
//                        maxCWorth = cWorth;
//                        bestChild = child;
//                    }
//                }
//
//                if (bestChild != null) {
//                    this.eventWorth += k*bestChild.eventWorth;
//
//                    double dWorth = this.distWorth[this.maxDist];
//                    int cOverDist = bestChild.distWorth.length - worthlessDist;
//                    for (int t=1;t<cOverDist;t++) {
//                        dWorth += k * (bestChild.distWorth[worthlessDist + t] - bestChild.distWorth[worthlessDist]);
//                        this.distWorth[this.maxDist + t] = dWorth;
//                    }
//
//                    cMaxDist = lastTrunkDist + bestChild.maxDist;
//                    if (cMaxDist > this.maxDist)
//                        this.maxDist = cMaxDist;
//                }
//            }
//
//            this.distWorth = Arrays.copyOf(this.distWorth, this.maxDist + 1);
//            this.mDistWorth = this.distWorth[this.maxDist];
//
//            //--------------
//
//            k = 1;
//            WState pState = prevState;
//            CState pcState = pState.getCarState(0);
//            for (int i = 0; i < trunk.length; i++) {
//                k *= K;
//                WState wState = trunk.get(i);
//                CState cState = wState.getCarState(0); //todo hardcode index
//
//                double worth = 0;
//                worth += k * 100000*(wState.score - pState.score);
//                worth += k * 50000 * 100 * ((cState.nitroChargeCount + cState.nitroChargeCountUsed) - (pcState.nitroChargeCount + pcState.nitroChargeCountUsed));
//                worth += k * 15000 * 100 * ((cState.oilCanisterCount) - (pcState.oilCanisterCount));
//                worth += k * 20000 * 100 * ((cState.projectileCount) - (pcState.projectileCount));
//
//                double durabilityTaken = cState.durability - pcState.durability;
//                if (durabilityTaken >= 0) {
//                    worth += k * 70000 * 100 * durabilityTaken;
//                } else {
//                    eventWorth += k * DAMAGE_WORTH * durabilityTaken;
//                }
//
//                if (worth > 0) {
//                    int dist = cState.dist - prevCState.dist;
//
//                    int maxPostDist = dist;
//                    for (int j = i; j < trunk.length; j++) {
//                        int dst = trunk.get(j).getCarState(0).dist - prevCState.dist;
//                        if (dst > maxPostDist)
//                            maxPostDist = dst;
//                    }
//                    if (cMaxDist > maxPostDist)
//                        maxPostDist = cMaxDist;
//
//                    double scale = 1 / (1 + Math.exp(-(2.0 * ((maxPostDist - dist) - 4.0))));
//
//                    eventWorth += scale * worth;
//                }
//
//                if (cState.collision)
//                    eventWorth -= k * 50000;
//
//                eventWorth += k * 2500 * 100 * (pcState.nitroChargeCountUsed - cState.nitroChargeCountUsed);
//                if (pcState.durability > 1.0E-3 && cState.durability < 1.0E-3)
//                    eventWorth -= k * 100_000_000D;
//
//                pState = wState;
//                pcState = cState;
//            }
//        }
//
//        @Override
//        public double getEnginePower() {
//            return ep;
//        }
//
//        @Override
//        public double getWheelTurn() {
//            return wt;
//        }
//
//        @Override
//        public boolean isBreakPedal() {
//            return breakPedal;
//        }
//
//        @Override
//        public boolean isUseNitro() {
//            return useNitro;
//        }
//
//        @Override
//        public int getStateCount() {
//            return stateCount;
//        }
//
//        @Override
//        public int getMaxDist() {
//            return maxDist;
//        }
//
//        @Override
//        public double getMaxWorth() {
//            return this.eventWorth + this.mDistWorth;
//        }
//
//        public interface TreeStrategy {
//            double getValuation(CarNode node, CarNode parent);
//            boolean isTrunkTurn(CarNode node);
//            int selectTrunkPoint(CarNode node);
//        }
//
//        public static class StateTreeStrategy implements TreeStrategy {
//            @Override
//            public double getValuation(CarNode node, CarNode parent) {
////                return (1000 + maxWorth)*(1000 + maxWorth)*(1000 + maxWorth)* (maxDist + 1.0) / branchCount;
//                return Math.sqrt(node.stateCount) *(1000000 + node.getMaxWorth()) / node.branchCount;
//            }
//
//            @Override
//            public boolean isTrunkTurn(CarNode node) {
//                return node.trunk.length > ((node.stateCount + 0.1) / node.branchCount);
//            }
//
//            @Override
//            public int selectTrunkPoint(CarNode node) {
////            return random.nextInt(node.trunk.length - 1);
//                return (node.trunk.length / 2) - 1;
//            }
//        }
//
//        public static class DistTreeStrategy implements TreeStrategy {
//            @Override
//            public double getValuation(CarNode node, CarNode parent) {
//                return node.fullLen * (1000000 + node.getMaxWorth()) / (Math.sqrt(node.stateCount)*node.branchCount);
//            }
//
//            @Override
//            public boolean isTrunkTurn(CarNode node) {
//                return (node.trunkLen) > (node.fullLen / (node.branchCount));
//            }
//
//            @Override
//            public int selectTrunkPoint(CarNode node) {
//                double halfLen = node.trunkLen * 0.5;
//                CState prevCState = node.prevState.getCarState(0);
//
//                int index = 0;
//                while (index < (node.trunk.length - 1)) {
//                    CState cState = node.trunk.get(index).getCarState(0);
//
//                    if ((cState.trackLen - prevCState.trackLen) > halfLen)
//                        return index;
//
//                    index++;
//                }
//                return node.trunk.length - 2;
//            }
//        }
//    }


    public static class WEngine extends WorldEngine<CState, CarPathEngine, WState> {
        public WEngine(CarPathEngine[] carEngine, GameData data, OilSlick[] oilSlicks, WState[] bestTeammateTrack) {
            super(carEngine, data, oilSlicks, bestTeammateTrack);
        }

        @Override
        public void perStep() {
            super.perStep();
            getState().tick++;
        }
    }

    public static class WState extends WorldEngine.State<CState> {
        public int tick;

        public WState(CState[] carState, GameData data, Projectile[] projectiles) {
            super(carState, data, projectiles);
        }

        public WState(WorldEngine.State<CState> state) {
            super(state);
        }

        @Override
        public WState copy() {
            WState state = new WState(this);
            state.tick = this.tick;
            return state;
        }
    }

    public static class CarPathEngine extends CarEngine<CState> {
        private final double invTSize;
        private final int width;
        private final int[][] path;

        public CarPathEngine(Car car, GameData data, int[][] path, short wpIndex) {
            super(car, data);
            this.invTSize = 1.0 / data.game.getTrackTileSize();
            this.width = data.world.getWidth();
            this.path = path;

            CState state = createState(car, data.getPrevState(car), data.game);
            state.wpIndex = wpIndex;
            state.tIndex = getTIndex(state);
            setState(state);
        }

        private int getTIndex(UnitState state) {
            return ((int)(state.y * invTSize)) * width + ((int)(state.x * invTSize));
        }

        private int preTIndex;
        private double preX, preY;

        @Override
        public void perStepPre() {
            CState preState = getState();
            this.preTIndex = preState.tIndex;
            this.preX = preState.x;
            this.preY = preState.y;
            super.perStepPre();
        }

        @Override
        public void perStepPost() {
            super.perStepPost();
            CState state = getState();
            state.tick++;

            double dx = state.x - preX;
            double dy = state.y - preY;
            state.trackLen += Math.sqrt(dx*dx + dy*dy);

            state.tIndex = getTIndex(state);
            if (state.tIndex != preTIndex) {
                int[] wPath = path[state.wpIndex];
                int postRange = wPath[state.tIndex];
                state.dist += (wPath[preTIndex] - postRange);

                if (postRange == 0) {
                    state.wpIndex++;
                    state.wpIndex = (short)(state.wpIndex % path.length);
                }
            }
        }

        @Override
        public CState createState(Car car, UnitState prevState, Game game) {
            UnitState.CarState carState = super.createState(car, prevState, game);
            return new CState(carState);
        }
    }

    public static class CState extends UnitState.CarState {
        public short dist, wpIndex, tick;
        public int tIndex;
        public float trackLen;

        public CState(CarState state) {
            super(state);
        }

        @Override
        public CState copy() {
            CState state = new CState(this);
            state.dist = dist;
            state.wpIndex = wpIndex;
            state.tIndex = tIndex;
            state.tick = tick;
            state.trackLen = trackLen;
            return state;
        }
    }
}
