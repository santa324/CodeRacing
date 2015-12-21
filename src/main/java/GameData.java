import model.*;

import java.util.*;

public class GameData {
    public final Car self;
    public final World world;
    public final Game game;
    public final Move move;
    private final Map<Long, UnitState.CarState> prevStates;

    public final double tSizeInv;

    public final TileType[][] tileTypes;
    public final Map<TileType, Util.TileInfo> tileTypeInfo;
    public final Borders borders;

    public final Bonus[] bonuses;

    public GameData(Car self, World world, Game game, Move move, Map<Long, UnitState.CarState> prevStates) {
        this.self = self;
        this.world = world;
        this.game = game;
        this.move = move;
        this.prevStates = prevStates;

        this.tSizeInv = 1.0 / game.getTrackTileSize();

        EnumMap<TileType, Util.TileInfo> ttInfo = new EnumMap<>(TileType.class);
        ttInfo.put(TileType.VERTICAL, new Util.TileInfo(Util.Side.LEFT, Util.Side.RIGHT));
        ttInfo.put(TileType.HORIZONTAL, new Util.TileInfo(Util.Side.TOP, Util.Side.BOTTOM));
        ttInfo.put(TileType.LEFT_TOP_CORNER, new Util.TileInfo(Util.Side.LEFT, Util.Side.TOP));
        ttInfo.put(TileType.RIGHT_TOP_CORNER, new Util.TileInfo(Util.Side.RIGHT, Util.Side.TOP));
        ttInfo.put(TileType.LEFT_BOTTOM_CORNER, new Util.TileInfo(Util.Side.LEFT, Util.Side.BOTTOM));
        ttInfo.put(TileType.RIGHT_BOTTOM_CORNER, new Util.TileInfo(Util.Side.RIGHT, Util.Side.BOTTOM));
        ttInfo.put(TileType.LEFT_HEADED_T, new Util.TileInfo(Util.Side.RIGHT));
        ttInfo.put(TileType.RIGHT_HEADED_T, new Util.TileInfo(Util.Side.LEFT));
        ttInfo.put(TileType.TOP_HEADED_T, new Util.TileInfo(Util.Side.BOTTOM));
        ttInfo.put(TileType.BOTTOM_HEADED_T, new Util.TileInfo(Util.Side.TOP));
        ttInfo.put(TileType.CROSSROADS, new Util.TileInfo());
        ttInfo.put(TileType.UNKNOWN, new Util.TileInfo());
        ttInfo.put(TileType.EMPTY, new Util.TileInfo(Util.Side.LEFT, Util.Side.TOP, Util.Side.RIGHT, Util.Side.BOTTOM));
        tileTypeInfo = Collections.unmodifiableMap(ttInfo);

        Util.Side[] sides = Util.Side.values();

        TileType[] typeByBorders = new TileType[16];
        Arrays.fill(typeByBorders, TileType.EMPTY);
        for (TileType tileType : TileType.values()) {
            Util.TileInfo tileInfo = tileTypeInfo.get(tileType);

            if (TileType.UNKNOWN.equals(tileType))
                continue;

            int index = getIndex(tileInfo.getBorders(), sides);
            typeByBorders[index] = tileType;
        }

        Queue<GameUtil.Point> tileQueue = new PriorityQueue<>();

        TileType[][] tTypes = world.getTilesXY();
        this.tileTypes = new TileType[world.getWidth()][world.getHeight()];
        boolean[][][] tileBorders = new boolean[world.getWidth()][world.getHeight()][4];
        for (int x=0;x<world.getWidth();x++) {
            for (int y = 0; y < world.getHeight(); y++) {
                TileType tileType = tTypes[x][y];

                if (!TileType.UNKNOWN.equals(tileType)) {
                    tileQueue.add(new GameUtil.Point(x, y, 0));
                }

                Util.TileInfo tileInfo = tileTypeInfo.get(tileType);
                for (Util.Side side : sides) {
                    int nX = x + side.x;
                    int nY = y + side.y;
                    if (isOutOfWorld(nX, nY) || tileInfo.isBorder(side))
                        tileBorders[x][y][side.ordinal()] = true;
                }
            }
        }

        while (!tileQueue.isEmpty()) {
            GameUtil.Point point = tileQueue.remove();
            boolean[] tBorders = tileBorders[point.x][point.y];
            TileType type = typeByBorders[getIndex(tBorders, sides)];
            Util.TileInfo tInfo = tileTypeInfo.get(type);

            if (!type.equals(this.tileTypes[point.x][point.y])) {
                for (Util.Side side : sides) {
                    if (tInfo.isBorder(side))
                        tBorders[side.ordinal()] = true;

                    int nX = point.x + side.x;
                    int nY = point.y + side.y;
                    if (!isOutOfWorld(nX, nY)) {
                        tileBorders[nX][nY][Util.reverse(side).ordinal()] = tBorders[side.ordinal()];
                        tileQueue.add(new GameUtil.Point(nX, nY, point.dist + 1));
                    }
                }
            }

            this.tileTypes[point.x][point.y] = type;
        }

        List<Bonus> bList = Arrays.asList(world.getBonuses());
        Collections.sort(bList, new Comparator<Bonus>() {
            @Override
            public int compare(Bonus b1, Bonus b2) {
                return Long.compare(b1.getId(), b2.getId());
            }
        });
        bonuses = bList.toArray(new Bonus[bList.size()]);

        this.borders = new Borders(this);
    }

    public UnitState.CarState getPrevState(Car car) {
        UnitState.CarState pState = prevStates.get(car.getId());
        if (pState == null) {
            pState = new UnitState.CarState(car, car.getAngularSpeed(), game);
            prevStates.put(car.getId(), pState);
        }

        return pState;
    }

    public boolean isOutOfWorld(int x, int y) {
        return x < 0 || x >= world.getWidth() || y < 0 || y >= world.getHeight();
    }

    private int getIndex(boolean[] borders, Util.Side[] sides) {
        int index = 0;
        for (Util.Side side: sides) {
            if (borders[side.ordinal()])
                index |= (1 << side.ordinal());
        }
        return index;
    }
}
