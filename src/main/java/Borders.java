import model.Bonus;
import model.TileType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Borders {
    public static final double EPSILON = 1.0E-7;
    public static final double EPSILON2 = EPSILON*EPSILON*CarEngine.SUB_STEPS_INV*CarEngine.SUB_STEPS_INV;
    public static final double M_TRANSFER = 0.5;
    public static final double S_FRICTION = 0.5*Math.sqrt(2.0);
    public static final double M_TRANSFER_CAR = 1.25D;
    public static final double S_FRICTION_CAR = 0.25D*Math.sqrt(2.0);
    public static final double CAR_DAMAGE_FACTOR = 0.03;

    private final double tSize, tMargin, invSubSize, carW2, carH2, dWidth, dHeight, tireRadius, tireMInv, tireAMInv;
    private final int height, width, width3;
    private final TileType[][] tileTypes;
    private final Map<TileType, Util.TileInfo> tileTypeInfo;

    private final Collider.CircleBox circleBoxCollider;
    private final CollisionChecker[] collisionCheckers;
    private final int[][] bonusIndexes;
    public final double[] bonusesData;

    public Borders(GameData data) {
        tSize = data.game.getTrackTileSize();
        tMargin = data.game.getTrackTileMargin();
        invSubSize = (1.0* SubSector.STEPS) / tSize;
        height = data.world.getHeight();
        width = data.world.getWidth();
        width3 = data.world.getWidth() * SubSector.STEPS;
        carW2 = data.game.getCarWidth() / 2;
        carH2 = data.game.getCarHeight() / 2;
        dWidth = width * tSize - 10;
        dHeight = height * tSize - 10;
        tireRadius = data.game.getTireRadius();
        tireMInv = 1.0 / data.game.getTireMass();
        tireAMInv = 2.0 * tireMInv / (tireRadius*tireRadius);
        tileTypes = data.tileTypes;
        tileTypeInfo = data.tileTypeInfo;

        circleBoxCollider = new Collider.CircleBox(tMargin, carW2, carH2);
        collisionCheckers = new CollisionChecker[width * height * SubSector.STEPS * SubSector.STEPS];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                TileType tileType = tileTypes[x][y];
                if (tileType == null || TileType.EMPTY.equals(tileType))
                    continue;

                Util.TileInfo tileInfo = tileTypeInfo.get(tileType);
                Util.TileInfo leftNeighbor = x <= 0 ? null : tileTypeInfo.get(tileTypes[x + Util.Side.LEFT.x][y + Util.Side.LEFT.y]);
                Util.TileInfo rightNeighbor = x >= (width-1) ? null : tileTypeInfo.get(tileTypes[x + Util.Side.RIGHT.x][y + Util.Side.RIGHT.y]);
                Util.TileInfo topNeighbor = y <= 0 ? null : tileTypeInfo.get(tileTypes[x + Util.Side.TOP.x][y + Util.Side.TOP.y]);
                Util.TileInfo bottomNeighbor = y >= (height-1) ? null : tileTypeInfo.get(tileTypes[x + Util.Side.BOTTOM.x][y + Util.Side.BOTTOM.y]);

                if (isBorder(tileInfo, Util.Side.LEFT, leftNeighbor))
                    collisionCheckers[getIndex(x, y, SubSector.LEFT_MIDDLE)] = new LeftLineChecker(x, SubSector.LEFT_MIDDLE, tSize, tMargin);
                if (isBorder(tileInfo, Util.Side.TOP, topNeighbor))
                    collisionCheckers[getIndex(x, y, SubSector.MIDDLE_TOP)] = new TopLineChecker(y, SubSector.MIDDLE_TOP, tSize, tMargin);
                if (isBorder(tileInfo, Util.Side.RIGHT, rightNeighbor))
                    collisionCheckers[getIndex(x, y, SubSector.RIGHT_MIDDLE)] = new RightLineChecker(x, SubSector.RIGHT_MIDDLE, tSize, tMargin);
                if (isBorder(tileInfo, Util.Side.BOTTOM, bottomNeighbor))
                    collisionCheckers[getIndex(x, y, SubSector.MIDDLE_BOTTOM)] = new BottomLineChecker(y, SubSector.MIDDLE_BOTTOM, tSize, tMargin);

                CollisionChecker leftTopChecker = getCornerChecker(x, y, Util.Side.LEFT, Util.Side.TOP, leftNeighbor, topNeighbor, SubSector.LEFT_TOP);
                collisionCheckers[getIndex(x, y, SubSector.LEFT_TOP)] = leftTopChecker;
                CollisionChecker rightTopChecker = getCornerChecker(x, y, Util.Side.TOP, Util.Side.RIGHT, topNeighbor, rightNeighbor, SubSector.RIGHT_TOP);
                collisionCheckers[getIndex(x, y, SubSector.RIGHT_TOP)] = rightTopChecker;
                CollisionChecker rightBottomChecker = getCornerChecker(x, y, Util.Side.RIGHT, Util.Side.BOTTOM, rightNeighbor, bottomNeighbor, SubSector.RIGHT_BOTTOM);
                collisionCheckers[getIndex(x, y, SubSector.RIGHT_BOTTOM)] = rightBottomChecker;
                CollisionChecker leftBottomChecker = getCornerChecker(x, y, Util.Side.BOTTOM, Util.Side.LEFT, bottomNeighbor, leftNeighbor, SubSector.LEFT_BOTTOM);
                collisionCheckers[getIndex(x, y, SubSector.LEFT_BOTTOM)] = leftBottomChecker;
            }
        }

        Util.Side[] sides = Util.Side.values();
        List<Integer>[] sectorsBonuses = new List[collisionCheckers.length];
        for (int i=0;i<data.bonuses.length; i++) {
            Bonus bonus = data.bonuses[i];
            int xIndex = (int)(bonus.getX() * invSubSize);
            int yIndex = (int)(bonus.getY() * invSubSize);
            List<Integer> secBonuses =  sectorsBonuses[yIndex * width3 + xIndex];
            if (secBonuses == null) {
                secBonuses = new ArrayList<>(5);
                sectorsBonuses[yIndex * width3 + xIndex] = secBonuses;
            }
            secBonuses.add(i);
            for (Util.Side side : sides) {
                int xI = xIndex + side.x;
                int yI = yIndex + side.y;
                if (xI >= 0 && xI < (width* SubSector.STEPS) && yI >= 0 && yI < (height* SubSector.STEPS)) {
                    List<Integer> sBonuses =  sectorsBonuses[yI * width3 + xI];
                    if (sBonuses == null) {
                        sBonuses = new ArrayList<>(5);
                        sectorsBonuses[yI * width3 + xI] = sBonuses;
                    }
                    sBonuses.add(i);
                }
            }
        }

        bonusIndexes = new int[sectorsBonuses.length][];
        for (int i=0;i<sectorsBonuses.length;i++) {
            List<Integer> secBonuses = sectorsBonuses[i];
            if (secBonuses != null && secBonuses.size() > 0) {
                bonusIndexes[i] = new int[secBonuses.size()];
                int k = 0;
                for (Integer bIndex : secBonuses) {
                    bonusIndexes[i][k++] = bIndex;
                }
            }
        }

        bonusesData = new double[data.bonuses.length*4];
        for (int i=0;i<data.bonuses.length;i++) {
            Bonus bonus = data.bonuses[i];
            bonusesData[i*4] = bonus.getX();
            bonusesData[i*4 + 1] = bonus.getY();
            bonusesData[i*4 + 2] = (bonus.getWidth() / 2) - 1;
            bonusesData[i*4 + 3] = (bonus.getHeight() / 2) - 1;
        }
    }

    private int getIndex(int x, int y, SubSector sector) {
        return y * width * SubSector.STEPS * SubSector.STEPS + sector.y * width * SubSector.STEPS + x * SubSector.STEPS + sector.x;
    }

    public int checkBonusCollision(UnitState state, long bonuses1, long bonuses2) {
        if (state.x < 0 || state.y < 0 || state.x >= dWidth || state.y >= dHeight)
            return -1;

        int xIndex = (int)(state.x * invSubSize);
        int yIndex = (int)(state.y * invSubSize);
        int sIndex = yIndex * width3 + xIndex;
        int[] bIndexes = bonusIndexes[sIndex];
        if (bIndexes != null) {
            for (int bIndex : bIndexes) {
                boolean exist;
                if (bIndex < 64) {
                    long bMask = 1L << bIndex;
                    exist = (bonuses1 & bMask) > 0;
                } else {
                    long bMask = 1L << (bIndex - 64);
                    exist = (bonuses2 & bMask) > 0;
                }

                if (exist) {
                    int bI = bIndex*4;
                    double bx = bonusesData[bI];
                    double by = bonusesData[bI + 1];
                    double bw2 = bonusesData[bI + 2];
                    double bh2 = bonusesData[bI + 3];

                    bx -= state.x;
                    by -= state.y;

                    if (!checkBoxPartCollision(bw2, bh2, -bx, -by, carW2, carH2, state.ax, state.ay))
                        return - 1;

                    double newBX = state.ax * bx + state.ay * by;
                    double newBY = state.ax * by - state.ay * bx;

                    if (!checkBoxPartCollision(carW2, carH2, newBX, newBY, bw2, bh2, state.ax, -state.ay))
                        return - 1;

                    return bIndex;
                }
            }
        }

        return -1;
    }

    private static boolean checkBoxPartCollision(double cw2, double ch2, double x, double y, double w2, double h2, double ax, double ay) {
        double xw = ax * w2;
        double xh = ax * h2;
        double yh = ay * h2;
        double yw = ay * w2;

        double maxX = Math.max(Math.abs(xw + yh), Math.abs(xw - yh));
        double maxY = Math.max(Math.abs(xh - yw), Math.abs(xh + yw));

        if ((x + maxX) <= -cw2)
            return false;
        if ((x - maxX) >= cw2)
            return false;
        if ((y + maxY) <= -ch2)
            return false;
        if ((y - maxY) >= ch2)
            return false;

        return true;
    }

    public boolean collideBox(UnitState.CarState state, double Vaw, double invM, double invAM, boolean resolve) {
        if (state.x < 0 || state.y < 0 || state.x >= dWidth || state.y >= dHeight)
            return true;

        int xIndex = (int)(state.x * invSubSize);
        int yIndex = (int)(state.y * invSubSize);
        int sIndex = yIndex * width3 + xIndex;
        CollisionChecker collisionChecker = collisionCheckers[sIndex];
        if (collisionChecker != null) {
            if (collisionChecker.collideBox(state, Vaw, invM, invAM, carW2, carH2, resolve))
                return true;
        }
        return false;
    }

    public boolean collideCircle(WorldEngine.ProjectileState state) {
        int xIndex = (int)(state.x * invSubSize);
        int yIndex = (int)(state.y * invSubSize);
        int sIndex = yIndex * width3 + xIndex;
        CollisionChecker collisionChecker = collisionCheckers[sIndex];
        if (collisionChecker != null) {
            return collisionChecker.collideCircle(state, tireRadius, tireMInv, tireAMInv);
        }
        return false;
    }

    private interface CollisionChecker {
        boolean collideBox(UnitState.CarState state, double Vaw, double invM, double invAM, double wight2, double height2, boolean resolve);
        boolean collideCircle(WorldEngine.ProjectileState state, double radius, double invM, double invAM);
    }

    private CollisionChecker getCornerChecker(int x, int y, Util.Side n1Side, Util.Side n2Side, Util.TileInfo n1TI, Util.TileInfo n2TI, SubSector sector) {
        Util.TileInfo tileInfo = tileTypeInfo.get(tileTypes[x][y]);
        if (isBorder(tileInfo, n1Side, n1TI) && isBorder(tileInfo, n2Side, n2TI)) {
            return new CornerInnerChecker(x, y, sector, tSize, tMargin, n1Side);
        } else {
            if (isBorder(tileInfo, n1Side, n1TI)) {
                if (n2TI == null || n2TI.isBorder(n1Side)) {
                    return getLineChecker(x, y, sector, tSize, tMargin, n1Side);
                } else {
                    return new LineCircleChecker(x, y, sector, tSize, tMargin, n1Side, false, circleBoxCollider);
                }
            } else if (isBorder(tileInfo, n2Side, n2TI)) {
                if (n1TI == null || n1TI.isBorder(n2Side)) {
                    return getLineChecker(x, y, sector, tSize, tMargin, n2Side);
                } else {
                    return new LineCircleChecker(x, y, sector, tSize, tMargin, n2Side, true, circleBoxCollider);
                }
            } else {
                if ((n1TI == null || n1TI.isBorder(n2Side)) && (n2TI == null || n2TI.isBorder(n1Side))) {
                    return new CornerOuterChecker(x, y, sector, tSize, tMargin, n1Side, circleBoxCollider);
                } else {
                    if (n1TI == null || n1TI.isBorder(n2Side)) {
                        return new CirceLineChecker(x, y, sector, tSize, tMargin, n2Side, true, circleBoxCollider);
                    } else if (n2TI == null || n2TI.isBorder(n1Side)) {
                        return new CirceLineChecker(x, y, sector, tSize, tMargin, n1Side, false, circleBoxCollider);
                    } else {
                        return new CircleChecker(x, y, sector, tSize, tMargin, n1Side, circleBoxCollider);
                    }
                }
            }
        }
    }

    private boolean isBorder(Util.TileInfo tileInfo, Util.Side side, Util.TileInfo sideNeighbor) {
        return tileInfo.isBorder(side) || sideNeighbor == null || sideNeighbor.isBorder(Util.reverse(side));
    }

    private CollisionChecker getLineChecker(int x, int y, SubSector sector, double tSize, double tMargin, Util.Side side) {
        if (Util.Side.LEFT.equals(side)) {
            return new LeftLineChecker(x, sector, tSize, tMargin);
        } else if (Util.Side.TOP.equals(side)) {
            return new TopLineChecker(y, sector, tSize, tMargin);
        } else if (Util.Side.RIGHT.equals(side)) {
            return new RightLineChecker(x, sector, tSize, tMargin);
        } else {
            return new BottomLineChecker(y, sector, tSize, tMargin);
        }
    }

    private static class RightLineChecker implements CollisionChecker {
        private final double rightX;

        public RightLineChecker(int x, SubSector sector, double tSize, double tMargin) {
            this.rightX = ((sector.x + 1)/ SubSector.STEPS + x)*tSize - tMargin;
        }
        @Override
        public boolean collideBox(UnitState.CarState state, double Vaw, double invM, double invAM, double wight2, double height2, boolean resolve) {
            return collideBox(state, Vaw, invM, invAM, wight2, height2, rightX, resolve);
        }

        @Override
        public boolean collideCircle(WorldEngine.ProjectileState state, double radius, double invM, double invAM) {
            return collideCircle(state, radius, invM, invAM, this.rightX);
        }

        public static boolean collideBox(UnitState.CarState state, double Vaw, double invM, double invAM, double wight2, double height2, double rightX, boolean resolve) {
            double wx = state.ax * wight2;
            double hx = - state.ay * height2;
            double lineX = rightX - state.x;

            double rbX = wx + hx;
            double lbX = hx - wx;

            boolean rbIn = rbX > lineX;
            boolean lbIn = lbX > lineX;
            boolean ltIn = rbX < -lineX;
            boolean rtIn = lbX < -lineX;

            if (!resolve)
                return rbIn || lbIn || ltIn || rtIn;

            boolean collision = false;
            double intersectY = 0;
            if (rbIn != lbIn) {
                intersectY += (state.ay * lineX + height2) / state.ax;
                collision = true;
            }
            if (lbIn != ltIn) {
                intersectY += - (wight2 + state.ax * lineX) / state.ay;
                collision = true;
            }
            if (ltIn != rtIn) {
                intersectY += (state.ay * lineX - height2) / state.ax;
                collision = true;
            }
            if (rtIn != rbIn) {
                intersectY += (wight2 - state.ax * lineX) / state.ay;
                collision = true;
            }

            if (collision) {
                intersectY *= 0.5;

                double Va = state.Va + Vaw;
                double rvCx = - (state.Vx - intersectY *Va);
                double rvCy = - (state.Vy + lineX *Va);

                if (rvCx < Borders.EPSILON) {
                    double denominatorR = invM + invAM * intersectY * intersectY;
                    double impulseChangeR = Borders.M_TRANSFER_CAR * rvCx / denominatorR;

                    state.Vx += impulseChangeR * invM;
                    state.Va += - intersectY * impulseChangeR * invAM;

                    double relativeVt2 = rvCy * rvCy;
                    if (relativeVt2 > Borders.EPSILON2) {
                        double denominatorL = invM + invAM * lineX * lineX;
                        double impulseChangeL = - Borders.S_FRICTION_CAR * rvCy * rvCx / (denominatorL * Math.sqrt(relativeVt2 + rvCx * rvCx));

                        state.Vy += impulseChangeL * invM;
                        state.Va += lineX * impulseChangeL * invAM;
                    }

                    double damage = -rvCx * CAR_DAMAGE_FACTOR;
                    if(damage > 0.01D) {
                        state.durability -= damage;
                        if (state.durability < 0)
                            state.durability = 0;
                    }
                }

                double deep = Math.max(Math.abs(rbX), Math.abs(lbX)) - lineX;
                if (deep >= Borders.EPSILON) {
                    state.x -= deep + Borders.EPSILON;
                }

                return false; //todo return true
            }
            return false;
        }

        public static boolean collideCircle(WorldEngine.ProjectileState state, double radius, double invM, double invAM, double rightX) {
            double deep = state.x + radius - rightX;
            if (deep >= 0) {
                double r = radius - deep;
                double Vt = state.Vy + state.Va*r;
                double Vt2 = Vt*Vt;
                if (Vt2 > EPSILON2) {
                    double invAMr = invAM*r;
                    double denominator = invM + invAMr*r;

                    double impulseChange =  - S_FRICTION * state.Vx * Vt / (denominator * Math.sqrt(state.Vx*state.Vx + Vt2));

                    state.Vy += impulseChange * invM;
                    state.Va += impulseChange * invAMr;
                }

                state.Vx = -state.Vx * M_TRANSFER;

                if (deep > EPSILON)
                    state.x -= (deep + EPSILON);
                return true;
            }
            return false;
        }
    }
    private static class LeftLineChecker implements CollisionChecker {
        private final double leftX;

        public LeftLineChecker(int x, SubSector sector, double tSize, double tMargin) {
            this.leftX = (x + sector.x / SubSector.STEPS) * tSize + tMargin;
        }
        @Override
        public boolean collideBox(UnitState.CarState state, double Vaw, double invM, double invAM, double wight2, double height2, boolean resolve) {
            return collideBox(state, Vaw, invM, invAM, wight2, height2, leftX, resolve);
        }

        @Override
        public boolean collideCircle(WorldEngine.ProjectileState state, double radius, double invM, double invAM) {
            return collideCircle(state, radius, invM, invAM, this.leftX);
        }

        public static boolean collideBox(UnitState.CarState state, double Vaw, double invM, double invAM, double wight2, double height2, double leftX, boolean resolve) {
            double wx = state.ax * wight2;
            double hx = - state.ay * height2;
            double lineX = leftX - state.x;

            double rbX = wx + hx;
            double lbX = hx - wx;

            boolean rbIn = rbX < lineX;
            boolean lbIn = lbX < lineX;
            boolean ltIn = rbX > -lineX;
            boolean rtIn = lbX > -lineX;

            if (!resolve)
                return rbIn || lbIn || ltIn || rtIn;

            boolean collision = false;
            double intersectY = 0;
            if (rbIn != lbIn) {
                intersectY += (state.ay * lineX + height2) / state.ax;
                collision = true;
            }
            if (lbIn != ltIn) {
                intersectY += - (wight2 + state.ax * lineX) / state.ay;
                collision = true;
            }
            if (ltIn != rtIn) {
                intersectY += (state.ay * lineX - height2) / state.ax;
                collision = true;
            }
            if (rtIn != rbIn) {
                intersectY += (wight2 - state.ax * lineX) / state.ay;
                collision = true;
            }

            if (collision) {
                intersectY *= 0.5;

                double Va = state.Va + Vaw;
                double rvCx = - (state.Vx - intersectY *Va);
                double rvCy = - (state.Vy + lineX *Va);

                if (rvCx > -Borders.EPSILON) {
                    double denominatorR = invM + invAM * intersectY * intersectY;
                    double impulseChangeR = Borders.M_TRANSFER_CAR * rvCx / denominatorR;

                    state.Vx += impulseChangeR * invM;
                    state.Va += - intersectY * impulseChangeR * invAM;

                    double relativeVt2 = rvCy * rvCy;
                    if (relativeVt2 > Borders.EPSILON2) {
                        double denominatorL = invM + invAM * lineX * lineX;
                        double impulseChangeL = Borders.S_FRICTION_CAR * rvCy * rvCx / (denominatorL * Math.sqrt(relativeVt2 + rvCx * rvCx));

                        state.Vy += impulseChangeL * invM;
                        state.Va += lineX * impulseChangeL * invAM;
                    }

                    double damage = rvCx * CAR_DAMAGE_FACTOR;
                    if(damage > 0.01D) {
                        state.durability -= damage;
                        if (state.durability < 0)
                            state.durability = 0;
                    }
                }

                double deep = Math.max(Math.abs(rbX), Math.abs(lbX)) + lineX;
                if (deep >= Borders.EPSILON) {
                    state.x += deep + Borders.EPSILON;
                }

                return false; //todo return true
            }
            return false;
        }

        public static boolean collideCircle(WorldEngine.ProjectileState state, double radius, double invM, double invAM, double leftX) {
            double deep = leftX + radius - state.x;
            if (deep >= 0) {
                double r = radius - deep;
                double Vt = state.Vy - state.Va*r;
                double Vt2 = Vt*Vt;
                if (Vt2 > EPSILON2) {
                    double invAMr = invAM*r;
                    double denominator = invM + invAMr*r;

                    double impulseChange = S_FRICTION * state.Vx * Vt / (denominator * Math.sqrt(state.Vx*state.Vx + Vt2));

                    state.Vy += impulseChange * invM;
                    state.Va -= impulseChange * invAMr;
                }

                state.Vx = -state.Vx * M_TRANSFER;

                if (deep > EPSILON)
                    state.x += deep + EPSILON;
                return true;
            }
            return false;
        }
    }
    private static class TopLineChecker implements CollisionChecker {
        private final double topY;

        public TopLineChecker(int y, SubSector sector, double tSize, double tMargin) {
            this.topY = (y + sector.y / SubSector.STEPS) * tSize + tMargin;
        }
        @Override
        public boolean collideBox(UnitState.CarState state, double Vaw, double invM, double invAM, double wight2, double height2, boolean resolve) {
            return collideBox(state, Vaw, invM, invAM, wight2, height2, topY, resolve);
        }

        @Override
        public boolean collideCircle(WorldEngine.ProjectileState state, double radius, double invM, double invAM) {
            return collideCircle(state, radius, invM, invAM, this.topY);
        }

        public static boolean collideBox(UnitState.CarState state, double Vaw, double invM, double invAM, double wight2, double height2, double topY, boolean resolve) {
            double wy = state.ay * wight2;
            double hy = state.ax * height2;
            double lineY = topY - state.y;

            double rbY = wy + hy;
            double lbY = hy - wy;

            boolean rbIn = rbY < lineY;
            boolean lbIn = lbY < lineY;
            boolean ltIn = rbY > -lineY;
            boolean rtIn = lbY > -lineY;

            if (!resolve)
                return rbIn || lbIn || ltIn || rtIn;

            boolean collision = false;
            double intersectX = 0;
            if (rbIn != lbIn) {
                intersectX += (state.ax * lineY - height2) / state.ay;
                collision = true;
            }
            if (lbIn != ltIn) {
                intersectX += - (wight2 + state.ay * lineY) / state.ax;
                collision = true;
            }
            if (ltIn != rtIn) {
                intersectX += (state.ax * lineY + height2) / state.ay;
                collision = true;
            }
            if (rtIn != rbIn) {
                intersectX += (wight2 - state.ay * lineY) / state.ax;
                collision = true;
            }

            if (collision) {
                intersectX *= 0.5;

                double Va = state.Va + Vaw;
                double rvCx = - (state.Vx - lineY *Va);
                double rvCy = - (state.Vy + intersectX *Va);

                if (rvCy > -Borders.EPSILON) {
                    double denominatorR = invM + invAM * intersectX * intersectX;
                    double impulseChangeR = Borders.M_TRANSFER_CAR * rvCy / denominatorR;

                    state.Vy += impulseChangeR * invM;
                    state.Va += intersectX * impulseChangeR * invAM;

                    double relativeVt2 = rvCx * rvCx;
                    if (relativeVt2 > Borders.EPSILON2) {
                        double denominatorL = invM + invAM * lineY * lineY;
                        double impulseChangeL = Borders.S_FRICTION_CAR * rvCy * rvCx / (denominatorL * Math.sqrt(relativeVt2 + rvCy * rvCy));

                        state.Vx += impulseChangeL * invM;
                        state.Va += -lineY * impulseChangeL * invAM;
                    }

                    double damage = rvCy * CAR_DAMAGE_FACTOR;
                    if(damage > 0.01D) {
                        state.durability -= damage;
                        if (state.durability < 0)
                            state.durability = 0;
                    }
                }

                double deep = Math.max(Math.abs(rbY), Math.abs(lbY)) + lineY;
                if (deep >= Borders.EPSILON) {
                    state.y += deep + Borders.EPSILON;
                }

                return false; //todo return true
            }
            return false;
        }

        public static boolean collideCircle(WorldEngine.ProjectileState state, double radius, double invM, double invAM, double topY) {
            double deep = topY + radius - state.y;
            if (deep >= 0) {
                double r = radius - deep;
                double Vt = state.Vx + state.Va*r;
                double Vt2 = Vt*Vt;
                if (Vt2 > EPSILON2) {
                    double invAMr = invAM*r;
                    double denominator = invM + invAMr*r;

                    double impulseChange = S_FRICTION * state.Vy * Vt / (denominator * Math.sqrt(state.Vy*state.Vy + Vt2));

                    state.Vx += impulseChange * invM;
                    state.Va += impulseChange * invAMr;
                }

                state.Vy = -state.Vy * M_TRANSFER;

                if (deep > EPSILON)
                    state.y += deep + EPSILON;
                return true;
            }
            return false;
        }
    }
    private static class BottomLineChecker implements CollisionChecker {
        private final double bottomY;

        public BottomLineChecker(int y, SubSector sector, double tSize, double tMargin) {
            this.bottomY = (y + (sector.y + 1) / SubSector.STEPS) * tSize - tMargin;
        }
        @Override
        public boolean collideBox(UnitState.CarState state, double Vaw, double invM, double invAM, double wight2, double height2, boolean resolve) {
            return collideBox(state, Vaw, invM, invAM, wight2, height2, bottomY, resolve);
        }

        @Override
        public boolean collideCircle(WorldEngine.ProjectileState state, double radius, double invM, double invAM) {
            return collideCircle(state, radius, invM, invAM, this.bottomY);
        }

        public static boolean collideBox(UnitState.CarState state, double Vaw, double invM, double invAM, double wight2, double height2, double bottomY, boolean resolve) {
            double wy = state.ay * wight2;
            double hy = state.ax * height2;
            double lineY = bottomY - state.y;

            double rbY = wy + hy;
            double lbY = hy - wy;

            boolean rbIn = rbY > lineY;
            boolean lbIn = lbY > lineY;
            boolean ltIn = rbY < -lineY;
            boolean rtIn = lbY < -lineY;

            if (!resolve)
                return rbIn || lbIn || ltIn || rtIn;

            boolean collision = false;
            double intersectX = 0;
            if (rbIn != lbIn) {
                intersectX += (state.ax * lineY - height2) / state.ay;
                collision = true;
            }
            if (lbIn != ltIn) {
                intersectX += - (wight2 + state.ay * lineY) / state.ax;
                collision = true;
            }
            if (ltIn != rtIn) {
                intersectX += (state.ax * lineY + height2) / state.ay;
                collision = true;
            }
            if (rtIn != rbIn) {
                intersectX += (wight2 - state.ay * lineY) / state.ax;
                collision = true;
            }

            if (collision) {
                intersectX *= 0.5;

                double Va = state.Va + Vaw;
                double rvCx = - (state.Vx - lineY *Va);
                double rvCy = - (state.Vy + intersectX *Va);

                if (rvCy < Borders.EPSILON) {
                    double denominatorR = invM + invAM * intersectX * intersectX;
                    double impulseChangeR = Borders.M_TRANSFER_CAR * rvCy / denominatorR;

                    state.Vy += impulseChangeR * invM;
                    state.Va += intersectX * impulseChangeR * invAM;

                    double relativeVt2 = rvCx * rvCx;
                    if (relativeVt2 > Borders.EPSILON2) {
                        double denominatorL = invM + invAM * lineY * lineY;
                        double impulseChangeL = Borders.S_FRICTION_CAR * rvCy * rvCx / (denominatorL * Math.sqrt(relativeVt2 + rvCy * rvCy));

                        state.Vx += - impulseChangeL * invM;
                        state.Va += lineY * impulseChangeL * invAM;
                    }

                    double damage = -rvCy * CAR_DAMAGE_FACTOR;
                    if(damage > 0.01D) {
                        state.durability -= damage;
                        if (state.durability < 0)
                            state.durability = 0;
                    }
                }

                double deep = Math.max(Math.abs(rbY), Math.abs(lbY)) - lineY;
                if (deep >= Borders.EPSILON) {
                    state.y -= deep + Borders.EPSILON;
                }

                return false; //todo return true
            }
            return false;
        }

        public static boolean collideCircle(WorldEngine.ProjectileState state, double radius, double invM, double invAM, double bottomY) {
            double deep = state.y + radius - bottomY;
            if (deep >= 0) {
                double r = radius - deep;
                double Vt = state.Vx - state.Va*r;
                double Vt2 = Vt*Vt;
                if (Vt2 > EPSILON2) {
                    double invAMr = invAM*r;
                    double denominator = invM + invAMr*r;

                    double impulseChange = - S_FRICTION * state.Vy * Vt / (denominator * Math.sqrt(state.Vy*state.Vy + Vt2));

                    state.Vx += impulseChange * invM;
                    state.Va -= impulseChange * invAMr;
                }

                state.Vy = -state.Vy * M_TRANSFER;

                if (deep > EPSILON)
                    state.y -= (deep + EPSILON);
                return true;
            }
            return false;
        }
    }

    private static class CornerInnerChecker implements CollisionChecker {
        private final double xLine, yLine;
        private final int xScale, yScale;

        public CornerInnerChecker(int x, int y, SubSector sector, double tSize, double tMargin, Util.Side side) {
            if (Util.Side.RIGHT.equals(side) || Util.Side.BOTTOM.equals(side)) {
                this.yLine = (y + (sector.y + 1) / SubSector.STEPS) * tSize - tMargin;
                this.yScale = -1;
            } else {
                this.yLine = (y + sector.y / SubSector.STEPS) * tSize + tMargin;
                this.yScale = 1;
            }
            if (Util.Side.TOP.equals(side) || Util.Side.RIGHT.equals(side)) {
                this.xLine = ((sector.x + 1)/ SubSector.STEPS + x)*tSize - tMargin;
                this.xScale = -1;
            } else {
                this.xLine = (x + sector.x / SubSector.STEPS) * tSize + tMargin;
                this.xScale = 1;
            }
        }

        public boolean collideBox(UnitState.CarState state, double Vaw, double invM, double invAM, double wight2, double height2, boolean resolve) {
            boolean collision = false;
            if (this.yScale == -1 && BottomLineChecker.collideBox(state, Vaw, invM, invAM, wight2, height2, this.yLine, resolve))
                collision = true;

            if (this.yScale == 1 && TopLineChecker.collideBox(state, Vaw, invM, invAM, wight2, height2, this.yLine, resolve))
                collision = true;

            if (this.xScale == 1 && LeftLineChecker.collideBox(state, Vaw, invM, invAM, wight2, height2, this.xLine, resolve))
                collision = true;

            if (this.xScale == -1 && RightLineChecker.collideBox(state, Vaw, invM, invAM, wight2, height2, this.xLine, resolve))
                collision = true;

            return collision;

//            double axw = state.ax * wight2;
//            double ayh = state.ay * height2;
//            double rightLine = (state.x - xLine)*xScale;
//            if (Math.abs(axw + ayh) > rightLine || Math.abs(axw - ayh) > rightLine)
//                return true;
//
//
//            double ayw = state.ay * wight2;
//            double axh = state.ax * height2;
//            double bottomLine = (state.y - yLine)*yScale;
//            return Math.abs(axh - ayw) > bottomLine || Math.abs(axh + ayw) > bottomLine;

//            Util.Vector2 uPos = new Util.Vector2(x, y).sub(centerX, centerY).rotateBack(rotation);
//            Util.Vector2 uAngle = new Util.Vector2(ax, ay).rotateBack(rotation);
//            Util.Vector2 rtCorner = new Util.Vector2(wight2, height2).rotateBack(uAngle);
//            Util.Vector2 rbCorner = new Util.Vector2(wight2, -height2).rotateBack(uAngle);
//            double maxX = Math.max(Math.abs(rtCorner.x), Math.abs(rbCorner.x));
//            double maxY = Math.max(Math.abs(rtCorner.y), Math.abs(rbCorner.y));
//            return (uPos.x + maxX) > lineDist || (uPos.y + maxY) > lineDist;
        }

        @Override
        public boolean collideCircle(WorldEngine.ProjectileState state, double radius, double invM, double invAM) {
            if (this.yScale == -1 && BottomLineChecker.collideCircle(state, radius, invM, invAM, this.yLine))
                return true;

            if (this.yScale == 1 && TopLineChecker.collideCircle(state, radius, invM, invAM, this.yLine))
                return true;

            if (this.xScale == 1 && LeftLineChecker.collideCircle(state, radius, invM, invAM, this.xLine))
                return true;

            if (this.xScale == -1 && RightLineChecker.collideCircle(state, radius, invM, invAM, this.xLine))
                return true;

            return false;
        }
    }

    private static class CircleChecker extends Checker {
        private final Collider.CircleBox circleBoxCollider;
        private final Util.Vector2RO circleCenter;
        private final double cr2;

        public CircleChecker(int x, int y, SubSector sector, double tSize, double tMargin, Util.Side side, Collider.CircleBox circleBoxCollider) {
            super(x, y, sector, tSize, tMargin, side);
            this.circleBoxCollider = circleBoxCollider;
            circleCenter = new Util.Vector2(subSize / 2, subSize / 2).rotate(side).add(centerX, centerY);
            this.cr2 = tMargin * tMargin;
        }

        @Override
        public boolean collideBox(UnitState.CarState state, double Vaw, double invM, double invAM, double wight2, double height2, boolean resolve) {
            double x = circleCenter.getX() - state.x;
            double y = circleCenter.getY() - state.y;

            if (resolve) {
                Collider.Collision collision = circleBoxCollider.checkBoxCentred(x, y, state.ax, state.ay);
                if (collision != null) {
                    double carToCX = collision.x;
                    double carToCY = collision.y;

                    double rvCx = - (state.Vx - carToCY *(state.Va + Vaw));
                    double rvCy = - (state.Vy + carToCX *(state.Va + Vaw));
                    double normalRelativeVelocityLengthC = rvCx * collision.normalX + rvCy * collision.normalY;

                    if (normalRelativeVelocityLengthC > -Borders.EPSILON) {
                        double impulseChangeX = 0;
                        double impulseChangeY = 0;

                        double carL = carToCX * collision.normalY - carToCY * collision.normalX;

                        double denominatorR = invM + invAM * carL * carL;
                        double impulseChangeR = M_TRANSFER_CAR * normalRelativeVelocityLengthC / denominatorR;

                        impulseChangeX += collision.normalX * impulseChangeR;
                        impulseChangeY += collision.normalY * impulseChangeR;
                        state.Va += carL * impulseChangeR * invAM;

                        double relativeVt = rvCy * collision.normalX - rvCx * collision.normalY;
                        double relativeVt2 = relativeVt * relativeVt;
                        if (relativeVt2 > Borders.EPSILON2) {
                            double carR = carToCX * collision.normalX + carToCY * collision.normalY;
                            double denominatorL = invM + invAM * carR * carR;

                            double surfaceFriction = Borders.S_FRICTION_CAR * Math.abs(normalRelativeVelocityLengthC) / Math.sqrt(rvCx * rvCx + rvCy * rvCy);
                            double impulseChangeL = 1.0D * surfaceFriction * relativeVt / denominatorL;

                            impulseChangeX += -collision.normalY * impulseChangeL;
                            impulseChangeY += collision.normalX * impulseChangeL;
                            state.Va += carR * impulseChangeL * invAM;
                        }

                        state.Vx += impulseChangeX * invM;
                        state.Vy += impulseChangeY * invM;


                        double damage = normalRelativeVelocityLengthC * CAR_DAMAGE_FACTOR;
                        if(damage > 0.01D) {
                            state.durability -= damage;
                            if (state.durability < 0)
                                state.durability = 0;
                        }
                    }

                    if (collision.deep >= Borders.EPSILON) {
                        double pb = collision.deep + Borders.EPSILON;

                        state.x += collision.normalX * pb;
                        state.y += collision.normalY * pb;
                    }
                }
                return false;
            } else {
                double cx = state.ax * x + state.ay * y;
                double cy = state.ax * y - state.ay * x;
                if (cx > -wight2) {
                    if (cx < wight2) {
                        if ((cy - tMargin) < height2 && (cy + tMargin) > -height2)
                            return true;
                    } else {
                        if (cy > -height2) {
                            if (cy < height2) {
                                if ((cx - tMargin) < wight2)
                                    return true;
                            } else {
                                if (isPointInCircle(wight2 - cx, height2 - cy))
                                    return true;
                            }
                        } else {
                            if (isPointInCircle(wight2 - cx, height2 + cy))
                                return true;
                        }
                    }
                } else {
                    if (cy > -height2) {
                        if (cy < height2) {
                            if ((cx + tMargin) > -wight2)
                                return true;
                        } else {
                            if (isPointInCircle(wight2 + cx, height2 - cy))
                                return true;
                        }
                    } else {
                        if (isPointInCircle(wight2 + cx, height2 + cy))
                            return true;
                    }
                }
                return false;
            }
        }

        private boolean isPointInCircle(double x, double y) {
            return (x*x + y*y) < cr2;
        }

        @Override
        public boolean collideCircle(WorldEngine.ProjectileState state, double radius, double invM, double invAM) {
            double d2 = radius + tMargin;

            double dx = circleCenter.getX() - state.x;
            if (dx < d2 && dx > -d2) {
                double dy = circleCenter.getY() - state.y;
                if (dy < d2 && dy > -d2) {
                    double R2 = dx * dx + dy * dy;
                    if (R2 < d2 * d2) {
                        double R = Math.sqrt(R2);

                        double scale = 1.0 / R;
                        double nx = dx * scale;
                        double ny = dy * scale;

                        //normal =  {nx;ny}
                        //tangent = {ny;-nx}

                        double Vr = state.Vx * nx + state.Vy * ny;
                        double Vt = state.Vx * ny - state.Vy * nx;

                        double r = 0.5 * (R - (tMargin*tMargin - radius*radius) * scale);
//                        double r = R - tMargin;

                        double vt = Vt - state.Va*r;
                        double vt2 = vt*vt;
                        if (vt2 > EPSILON2) {
                            double invAMr = invAM*r;
                            double denominator = invM + invAMr*r;
                            double impulseChange = - S_FRICTION * Vr * vt / (denominator * Math.sqrt(Vr*Vr + vt2));

                            Vt += impulseChange * invM;
                            state.Va -= impulseChange * invAMr;
                        }

                        Vr = -Vr * M_TRANSFER;

                        state.Vx = Vr * nx + Vt * ny;
                        state.Vy = Vr * ny - Vt * nx;

                        double deep = d2 - R;
                        if (deep > EPSILON) {
                            deep += EPSILON;
                            state.x -= nx * deep;
                            state.y -= ny * deep;
                        }

                        return true;
                    }
                }
            }

            return false;
        }
    }

    private static class LineCircleChecker extends CircleChecker {
        private final boolean reflect;
        private final double bottom;
        private final Util.Side rot;

        public LineCircleChecker(int x, int y, SubSector sector, double tSize, double tMargin, Util.Side side, boolean reflect, Collider.CircleBox circleBoxCollider) {
            super(x, y, sector, tSize, tMargin, reflect ? Util.rotateBackSide(side) : side, circleBoxCollider);
            this.rot = side;
            this.reflect = reflect;
            this.bottom = subSize / 2;
        }

        @Override
        public boolean collideBox(UnitState.CarState state, double Vaw, double invM, double invAM, double wight2, double height2, boolean resolve) {
            double uPosX = rot.x * (state.x - centerX) + rot.y * (state.y - centerY);
            double uPosY = rot.x * (state.y - centerY) - rot.y * (state.x - centerX);

            double uAngleX = rot.x * state.ax + rot.y * state.ay;
            double uAngleY = rot.x * state.ay - rot.y * state.ax;

            if (reflect) {
                uPosY = - uPosY;
                uAngleY = - uAngleY;
            }


            double rtCornerX = uAngleX * wight2 + uAngleY * height2;
            double rtCornerY =  uAngleY * wight2 - uAngleX * height2;
            if (rtCornerX < 0) {
                rtCornerX = -rtCornerX;
                rtCornerY = -rtCornerY;
            }

            double rbCornerX = uAngleX * wight2 - uAngleY * height2;
            double rbCornerY = uAngleX * height2 + uAngleY * wight2;
            if (rbCornerX < 0) {
                rbCornerX = -rbCornerX;
                rbCornerY = -rbCornerY;
            }

            double rCornerX = rtCornerX < rbCornerX ? rbCornerX : rtCornerX;
            double rCornerY = rtCornerX < rbCornerX ? rbCornerY : rtCornerY;
            if ((uPosX + rCornerX) <= lineDist)
                return false;

            if ((uPosY + rCornerY) < bottom) {
                if (resolve) {
                    if (Util.Side.RIGHT.equals(rot)) {
                        return RightLineChecker.collideBox(state, Vaw, invM, invAM, wight2, height2, centerX + lineDist, resolve);
                    } else if (Util.Side.BOTTOM.equals(rot)) {
                        return BottomLineChecker.collideBox(state, Vaw, invM, invAM, wight2, height2, centerY + lineDist, resolve);
                    } else if (Util.Side.LEFT.equals(rot)) {
                        return LeftLineChecker.collideBox(state, Vaw, invM, invAM, wight2, height2, centerX - lineDist, resolve);
                    } else {
                        return TopLineChecker.collideBox(state, Vaw, invM, invAM, wight2, height2, centerY - lineDist, resolve);
                    }
                } else {
                    return true;
                }
            }

//            Util.Vector2 uPos = new Util.Vector2(x, y).sub(centerX, centerY).rotateBack(rot);
//            Util.Vector2 uAngle = new Util.Vector2(ax, ay).rotateBack(rot);
//            if (reflect) {
//                uPos.reflectY();
//                uAngle.reflectY();
//            }
//
//            Util.Vector2 rtCorner = new Util.Vector2(wight2, height2).rotateBack(uAngle);
//            if (rtCorner.x < 0)
//                rtCorner.reverse();
//            Util.Vector2 rbCorner = new Util.Vector2(wight2, -height2).rotateBack(uAngle);
//            if (rbCorner.x < 0)
//                rbCorner.reverse();
//            Util.Vector2 rightCorner = rtCorner.x < rbCorner.x ? rbCorner : rtCorner;
//            if ((uPos.x + rightCorner.x) <= lineDist)
//                return false;
//
//            if ((uPos.y + rightCorner.y) < bottom)
//                return true;

            return super.collideBox(state, Vaw, invM, invAM, wight2, height2, resolve);
        }

        @Override
        public boolean collideCircle(WorldEngine.ProjectileState state, double radius, double invM, double invAM) {
            if (Util.Side.RIGHT.equals(rot)) {
                return RightLineChecker.collideCircle(state, radius, invM, invAM, centerX + lineDist);
            } else if (Util.Side.BOTTOM.equals(rot)) {
                return BottomLineChecker.collideCircle(state, radius, invM, invAM, centerY + lineDist);
            } else if (Util.Side.LEFT.equals(rot)) {
                return LeftLineChecker.collideCircle(state, radius, invM, invAM, centerX - lineDist);
            } else {
                return TopLineChecker.collideCircle(state, radius, invM, invAM, centerY - lineDist);
            }
        }
    }

    private static class CirceLineChecker extends CircleChecker {
        private final boolean reflect;
        private final double bottom;
        private final Util.Side rot;


        public CirceLineChecker(int x, int y, SubSector sector, double tSize, double tMargin, Util.Side side, boolean reflect, Collider.CircleBox circleBoxCollider) {
            super(x, y, sector, tSize, tMargin, reflect ? Util.rotateBackSide(side) : side, circleBoxCollider);
            this.rot = side;
            this.reflect = reflect;
            this.bottom = subSize / 2;
        }

        @Override
        public boolean collideBox(UnitState.CarState state, double Vaw, double invM, double invAM, double wight2, double height2, boolean resolve) {
            double uPosX = rot.x * (state.x - centerX) + rot.y * (state.y - centerY);
            double uPosY = rot.x * (state.y - centerY) - rot.y * (state.x - centerX);

            double uAngleX = rot.x * state.ax + rot.y * state.ay;
            double uAngleY = rot.x * state.ay - rot.y * state.ax;

            if (reflect) {
                uPosY = - uPosY;
                uAngleY = - uAngleY;
            }


            double rtCornerX = uAngleX * wight2 + uAngleY * height2;
            double rtCornerY = uAngleY * wight2 - uAngleX * height2;
            if (rtCornerX < 0) {
                rtCornerX = -rtCornerX;
                rtCornerY = -rtCornerY;
            }

            double rbCornerX = uAngleX * wight2 - uAngleY * height2;
            double rbCornerY = uAngleX * height2 + uAngleY * wight2;
            if (rbCornerX < 0) {
                rbCornerX = -rbCornerX;
                rbCornerY = -rbCornerY;
            }

            double rCornerX = rtCornerX < rbCornerX ? rbCornerX : rtCornerX;
            double rCornerY = rtCornerX < rbCornerX ? rbCornerY : rtCornerY;
            if ((uPosX + rCornerX) <= lineDist)
                return false;

            if ((uPosY + rCornerY) > bottom)
                return true;


//            Util.Vector2 uPos = new Util.Vector2(x, y).sub(centerX, centerY).rotateBack(rot);
//            Util.Vector2 uAngle = new Util.Vector2(ax, ay).rotateBack(rot);
//            if (reflect) {
//                uPos.reflectY();
//                uAngle.reflectY();
//            }
//
//            Util.Vector2 rtCorner = new Util.Vector2(wight2, height2).rotateBack(uAngle);
//            if (rtCorner.x < 0)
//                rtCorner.reverse();
//            Util.Vector2 rbCorner = new Util.Vector2(wight2, -height2).rotateBack(uAngle);
//            if (rbCorner.x < 0)
//                rbCorner.reverse();
//            Util.Vector2 rightCorner = rtCorner.x < rbCorner.x ? rbCorner : rtCorner;
//            if ((uPos.x + rightCorner.x) <= lineDist)
//                return false;
//
//            if ((uPos.y + rightCorner.y) > bottom)
//                return true;

            return super.collideBox(state, Vaw, invM, invAM, wight2, height2, resolve);
        }
    }

    private static class CornerOuterChecker extends CircleChecker {
        private final double bottom;

        public CornerOuterChecker(int x, int y, SubSector sector, double tSize, double tMargin, Util.Side side, Collider.CircleBox circleBoxCollider) {
            super(x, y, sector, tSize, tMargin, side, circleBoxCollider);
            this.bottom = subSize / 2;
        }

        @Override
        public boolean collideBox(UnitState.CarState state, double Vaw, double invM, double invAM, double wight2, double height2, boolean resolve) {
            double uPosX = rotation.x * (state.x - centerX) + rotation.y * (state.y - centerY);
            double uPosY = rotation.x * (state.y - centerY) - rotation.y * (state.x - centerX);

            double uAngleX = rotation.x * state.ax + rotation.y * state.ay;
            double uAngleY = rotation.x * state.ay - rotation.y * state.ax;

            double rtCornerX = uAngleX * wight2 + uAngleY * height2;
            double rtCornerY = uAngleY * wight2 - uAngleX * height2;
            if (rtCornerX < 0) {
                rtCornerX = -rtCornerX;
                rtCornerY = -rtCornerY;
            }

            double rbCornerX = uAngleX * wight2 - uAngleY * height2;
            double rbCornerY = uAngleX * height2 + uAngleY * wight2;
            if (rbCornerX < 0) {
                rbCornerX = -rbCornerX;
                rbCornerY = -rbCornerY;
            }

            double rCornerX = rtCornerX < rbCornerX ? rbCornerX : rtCornerX;
            double rCornerY = rtCornerX < rbCornerX ? rbCornerY : rtCornerY;

            if (rtCornerY < 0) {
                rtCornerX = -rtCornerX;
                rtCornerY = -rtCornerY;
            }
            if (rbCornerY < 0) {
                rbCornerX = -rbCornerX;
                rbCornerY = -rbCornerY;
            }
            double bCornerX = rtCornerY < rbCornerY ? rbCornerX : rtCornerX;
            double bCornerY = rtCornerY < rbCornerY ? rbCornerY : rtCornerY;

            if ((uPosX + rCornerX) <= lineDist || (uPosY + bCornerY) <= lineDist)
                return false;

            if ((uPosY + rCornerY) > bottom || (uPosX + bCornerX) > bottom)
                return true;


//            Util.Vector2 uPos = new Util.Vector2(x, y).sub(centerX, centerY).rotateBack(rotation);
//            Util.Vector2 uAngle = new Util.Vector2(ax, ay).rotateBack(rotation);
//
//            Util.Vector2 rtCorner = new Util.Vector2(wight2, height2).rotateBack(uAngle);
//            if (rtCorner.x < 0)
//                rtCorner.reverse();
//            Util.Vector2 rbCorner = new Util.Vector2(wight2, -height2).rotateBack(uAngle);
//            if (rbCorner.x < 0)
//                rbCorner.reverse();
//            Util.Vector2 rightCorner = rtCorner.x < rbCorner.x ? new Util.Vector2(rbCorner) : new Util.Vector2(rtCorner);
//            if (rtCorner.y < 0)
//                rtCorner.reverse();
//            if (rbCorner.y < 0)
//                rbCorner.reverse();
//            Util.Vector2 bottomCorner = rtCorner.y < rbCorner.y ? rbCorner : rtCorner;
//
//            if ((uPos.x + rightCorner.x) <= lineDist || (uPos.y + bottomCorner.y) <= lineDist)
//                return false;
//
//            if ((uPos.y + rightCorner.y) > bottom || (uPos.x + bottomCorner.x) > bottom)
//                return true;

            return super.collideBox(state, Vaw, invM, invAM, wight2, height2, resolve);
        }
    }

    private static abstract class Checker implements CollisionChecker {
        protected final double subSize, tMargin, lineDist;
        protected final double centerX, centerY;
        protected final Util.Side rotation;

        public Checker(int x, int y, SubSector sector, double tSize, double tMargin, Util.Side side) {
            this.tMargin = tMargin;
            this.subSize = tSize / SubSector.STEPS;
            this.centerX = x*tSize + (sector.x + 0.5) * subSize;
            this.centerY = y*tSize + (sector.y + 0.5) * subSize;
            this.lineDist = tSize / (2 * SubSector.STEPS) - tMargin;
            this.rotation = side;
        }
    }

    private enum SubSector {
        LEFT_TOP(0, 0), MIDDLE_TOP(1, 0), RIGHT_TOP(2, 0), LEFT_MIDDLE(0, 1), MIDDLE_MIDDLE(1, 1), RIGHT_MIDDLE(2, 1), LEFT_BOTTOM(0, 2), MIDDLE_BOTTOM(1, 2), RIGHT_BOTTOM(2, 2);

        public static final int STEPS = 3;
        public final int x, y;

        SubSector(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
