import model.*;

import java.io.*;
import java.lang.reflect.Array;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

public final class RemoteProcessClient implements Closeable {
    private static final int BUFFER_SIZE_BYTES = 1 << 20;
    private static final ByteOrder PROTOCOL_BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;
    private static final int INTEGER_SIZE_BYTES = Integer.SIZE / Byte.SIZE;
    private static final int LONG_SIZE_BYTES = Long.SIZE / Byte.SIZE;

    private final Socket socket;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final ByteArrayOutputStream outputStreamBuffer;

    private String mapName;
    private TileType[][] tilesXY;
    private int[][] waypoints;
    private Direction startingDirection;

    public RemoteProcessClient(String host, int port) throws IOException {
        socket = new Socket(host, port);
        socket.setSendBufferSize(BUFFER_SIZE_BYTES);
        socket.setReceiveBufferSize(BUFFER_SIZE_BYTES);
        socket.setTcpNoDelay(true);

        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
        outputStreamBuffer = new ByteArrayOutputStream(BUFFER_SIZE_BYTES);
    }

    public void writeToken(String token) throws IOException {
        writeEnum(MessageType.AUTHENTICATION_TOKEN);
        writeString(token);
        flush();
    }

    public int readTeamSize() throws IOException {
        ensureMessageType(readEnum(MessageType.class), MessageType.TEAM_SIZE);
        return readInt();
    }

    public void writeProtocolVersion() throws IOException {
        writeEnum(MessageType.PROTOCOL_VERSION);
        writeInt(2);
        flush();
    }

    public Game readGameContext() throws IOException {
        ensureMessageType(readEnum(MessageType.class), MessageType.GAME_CONTEXT);
        if (!readBoolean()) {
            return null;
        }

        return new Game(
                readLong(), readInt(), readInt(), readInt(), readDouble(), readDouble(), readInt(), readInt(),
                readInt(), readDouble(), readIntArray(), readInt(), readDouble(), readDouble(), readInt(), readDouble(),
                readDouble(), readDouble(), readDouble(), readDouble(), readDouble(), readDouble(), readDouble(),
                readDouble(), readDouble(), readInt(), readInt(), readInt(), readDouble(), readInt(), readInt(),
                readDouble(), readDouble(), readDouble(), readDouble(), readDouble(), readDouble(), readDouble(),
                readDouble(), readInt(), readDouble(), readDouble(), readDouble(), readDouble(), readDouble(),
                readDouble(), readDouble(), readDouble(), readDouble(), readDouble(), readDouble(), readDouble(),
                readInt(), readInt()
        );
    }

    public PlayerContext readPlayerContext() throws IOException {
        MessageType messageType = readEnum(MessageType.class);
        if (messageType == MessageType.GAME_OVER) {
            return null;
        }

        ensureMessageType(messageType, MessageType.PLAYER_CONTEXT);
        return readBoolean() ? new PlayerContext(readCars(), readWorld()) : null;
    }

    public void writeMoves(Move[] moves) throws IOException {
        writeEnum(MessageType.MOVES);

        if (moves == null) {
            writeInt(-1);
        } else {
            int moveCount = moves.length;
            writeInt(moveCount);

            for (int moveIndex = 0; moveIndex < moveCount; ++moveIndex) {
                Move move = moves[moveIndex];

                if (move == null) {
                    writeBoolean(false);
                } else {
                    writeBoolean(true);

                    writeDouble(move.getEnginePower());
                    writeBoolean(move.isBrake());
                    writeDouble(move.getWheelTurn());
                    writeBoolean(move.isThrowProjectile());
                    writeBoolean(move.isUseNitro());
                    writeBoolean(move.isSpillOil());
                }
            }
        }

        flush();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    private World readWorld() throws IOException {
        if (!readBoolean()) {
            return null;
        }

        return new World(
                readInt(), readInt(), readInt(), readInt(), readInt(), readPlayers(), readCars(), readProjectiles(),
                readBonuses(), readOilSlicks(), readMapName(), readTilesXY(), readWaypoints(), readStartingDirection()
        );
    }

    private Player[] readPlayers() throws IOException {
        int playerCount = readInt();
        if (playerCount < 0) {
            return null;
        }

        Player[] players = new Player[playerCount];

        for (int playerIndex = 0; playerIndex < playerCount; ++playerIndex) {
            if (readBoolean()) {
                players[playerIndex] = new Player(readLong(), readBoolean(), readString(), readBoolean(), readInt());
            }
        }

        return players;
    }

    private Car[] readCars() throws IOException {
        int carCount = readInt();
        if (carCount < 0) {
            return null;
        }

        Car[] cars = new Car[carCount];

        for (int carIndex = 0; carIndex < carCount; ++carIndex) {
            cars[carIndex] = readCar();
        }

        return cars;
    }

    private Car readCar() throws IOException {
        if (!readBoolean()) {
            return null;
        }

        return new Car(
                readLong(), readDouble(), readDouble(), readDouble(), readDouble(), readDouble(), readDouble(),
                readDouble(), readDouble(), readDouble(), readLong(), readInt(), readBoolean(), readEnum(CarType.class),
                readInt(), readInt(), readInt(), readInt(), readInt(), readInt(), readInt(), readInt(), readDouble(),
                readDouble(), readDouble(), readInt(), readInt(), readInt(), readBoolean()
        );
    }

    private Projectile[] readProjectiles() throws IOException {
        int projectileCount = readInt();
        if (projectileCount < 0) {
            return null;
        }

        Projectile[] projectiles = new Projectile[projectileCount];

        for (int projectileIndex = 0; projectileIndex < projectileCount; ++projectileIndex) {
            projectiles[projectileIndex] = readProjectile();
        }

        return projectiles;
    }

    private Projectile readProjectile() throws IOException {
        if (!readBoolean()) {
            return null;
        }

        return new Projectile(
                readLong(), readDouble(), readDouble(), readDouble(), readDouble(), readDouble(), readDouble(),
                readDouble(), readDouble(), readLong(), readLong(), readEnum(ProjectileType.class)
        );
    }

    private Bonus[] readBonuses() throws IOException {
        int bonusCount = readInt();
        if (bonusCount < 0) {
            return null;
        }

        Bonus[] bonuses = new Bonus[bonusCount];

        for (int bonusIndex = 0; bonusIndex < bonusCount; ++bonusIndex) {
            bonuses[bonusIndex] = readBonus();
        }

        return bonuses;
    }

    private Bonus readBonus() throws IOException {
        if (!readBoolean()) {
            return null;
        }

        return new Bonus(
                readLong(), readDouble(), readDouble(), readDouble(), readDouble(), readDouble(), readDouble(),
                readDouble(), readDouble(), readDouble(), readEnum(BonusType.class)
        );
    }

    private OilSlick[] readOilSlicks() throws IOException {
        int oilSlickCount = readInt();
        if (oilSlickCount < 0) {
            return null;
        }

        OilSlick[] oilSlicks = new OilSlick[oilSlickCount];

        for (int oilSlickIndex = 0; oilSlickIndex < oilSlickCount; ++oilSlickIndex) {
            oilSlicks[oilSlickIndex] = readOilSlick();
        }

        return oilSlicks;
    }

    private OilSlick readOilSlick() throws IOException {
        if (!readBoolean()) {
            return null;
        }

        return new OilSlick(
                readLong(), readDouble(), readDouble(), readDouble(), readDouble(), readDouble(), readDouble(),
                readDouble(), readDouble(), readInt()
        );
    }

    private String readMapName() throws IOException {
        if (mapName != null) {
            return mapName;
        }

        return mapName = readString();
    }

    private TileType[][] readTilesXY() throws IOException {
        TileType[][] newTilesXY = readEnumArray2D(TileType.class);

        if (newTilesXY != null && newTilesXY.length > 0) {
            tilesXY = newTilesXY;
        }

        return tilesXY;
    }

    private int[][] readWaypoints() throws IOException {
        if (waypoints != null) {
            return waypoints;
        }

        return waypoints = readIntArray2D();
    }

    private Direction readStartingDirection() throws IOException {
        if (startingDirection != null) {
            return startingDirection;
        }

        return startingDirection = readEnum(Direction.class);
    }

    private static void ensureMessageType(MessageType actualType, MessageType expectedType) {
        if (actualType != expectedType) {
            throw new IllegalArgumentException(String.format(
                    "Received wrong message [actual=%s, expected=%s].", actualType, expectedType
            ));
        }
    }

    private <E extends Enum> E readEnum(Class<E> enumClass) throws IOException {
        byte ordinal = readBytes(1)[0];

        E[] values = enumClass.getEnumConstants();
        int valueCount = values.length;

        for (int valueIndex = 0; valueIndex < valueCount; ++valueIndex) {
            E value = values[valueIndex];
            if (value.ordinal() == ordinal) {
                return value;
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private <E extends Enum> E[] readEnumArray(Class<E> enumClass, int count) throws IOException {
        byte[] bytes = readBytes(count);
        E[] array = (E[]) Array.newInstance(enumClass, count);

        E[] values = enumClass.getEnumConstants();
        int valueCount = values.length;

        Arrays.sort(values, new Comparator<E>() {
            @Override
            public int compare(E valueA, E valueB) {
                return valueA.ordinal() - valueB.ordinal();
            }
        });

        for (int i = 0; i < count; ++i) {
            byte ordinal = bytes[i];

            if (ordinal >= 0 && ordinal < valueCount) {
                array[i] = values[ordinal];
            }
        }

        return array;
    }

    private <E extends Enum> E[] readEnumArray(Class<E> enumClass) throws IOException {
        int count = readInt();
        if (count < 0) {
            return null;
        }

        return readEnumArray(enumClass, count);
    }

    @SuppressWarnings("unchecked")
    private <E extends Enum> E[][] readEnumArray2D(Class<E> enumClass) throws IOException {
        int count = readInt();
        if (count < 0) {
            return null;
        }

        E[][] array;
        try {
            array = (E[][]) Array.newInstance(Class.forName("[L" + enumClass.getName() + ';'), count);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Can't load array class for " + enumClass + '.', e);
        }

        for (int i = 0; i < count; ++i) {
            array[i] = readEnumArray(enumClass);
        }

        return array;
    }

    private <E extends Enum> void writeEnum(E value) throws IOException {
        writeBytes(new byte[]{value == null ? (byte) -1 : (byte) value.ordinal()});
    }

    private String readString() throws IOException {
        int length = readInt();
        if (length == -1) {
            return null;
        }

        return new String(readBytes(length), StandardCharsets.UTF_8);
    }

    private void writeString(String value) throws IOException {
        if (value == null) {
            writeInt(-1);
            return;
        }

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

        writeInt(bytes.length);
        writeBytes(bytes);
    }

    private boolean readBoolean() throws IOException {
        return readBytes(1)[0] != 0;
    }

    private boolean[] readBooleanArray(int count) throws IOException {
        byte[] bytes = readBytes(count);
        boolean[] array = new boolean[count];

        for (int i = 0; i < count; ++i) {
            array[i] = bytes[i] != 0;
        }

        return array;
    }

    private boolean[] readBooleanArray() throws IOException {
        int count = readInt();
        if (count < 0) {
            return null;
        }

        return readBooleanArray(count);
    }

    private boolean[][] readBooleanArray2D() throws IOException {
        int count = readInt();
        if (count < 0) {
            return null;
        }

        boolean[][] array = new boolean[count][];

        for (int i = 0; i < count; ++i) {
            array[i] = readBooleanArray();
        }

        return array;
    }

    private void writeBoolean(boolean value) throws IOException {
        writeBytes(new byte[]{value ? (byte) 1 : (byte) 0});
    }

    private int readInt() throws IOException {
        return ByteBuffer.wrap(readBytes(INTEGER_SIZE_BYTES)).order(PROTOCOL_BYTE_ORDER).getInt();
    }

    private int[] readIntArray(int count) throws IOException {
        byte[] bytes = readBytes(count * INTEGER_SIZE_BYTES);
        int[] array = new int[count];

        for (int i = 0; i < count; ++i) {
            array[i] = ByteBuffer.wrap(
                    bytes, i * INTEGER_SIZE_BYTES, INTEGER_SIZE_BYTES
            ).order(PROTOCOL_BYTE_ORDER).getInt();
        }

        return array;
    }

    private int[] readIntArray() throws IOException {
        int count = readInt();
        if (count < 0) {
            return null;
        }

        return readIntArray(count);
    }

    private int[][] readIntArray2D() throws IOException {
        int count = readInt();
        if (count < 0) {
            return null;
        }

        int[][] array = new int[count][];

        for (int i = 0; i < count; ++i) {
            array[i] = readIntArray();
        }

        return array;
    }

    private void writeInt(int value) throws IOException {
        writeBytes(ByteBuffer.allocate(INTEGER_SIZE_BYTES).order(PROTOCOL_BYTE_ORDER).putInt(value).array());
    }

    private long readLong() throws IOException {
        return ByteBuffer.wrap(readBytes(LONG_SIZE_BYTES)).order(PROTOCOL_BYTE_ORDER).getLong();
    }

    private void writeLong(long value) throws IOException {
        writeBytes(ByteBuffer.allocate(LONG_SIZE_BYTES).order(PROTOCOL_BYTE_ORDER).putLong(value).array());
    }

    private double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    private void writeDouble(double value) throws IOException {
        writeLong(Double.doubleToLongBits(value));
    }

    private byte[] readBytes(int byteCount) throws IOException {
        byte[] bytes = new byte[byteCount];
        int offset = 0;
        int readByteCount;

        while (offset < byteCount && (readByteCount = inputStream.read(bytes, offset, byteCount - offset)) != -1) {
            offset += readByteCount;
        }

        if (offset != byteCount) {
            throw new IOException(String.format("Can't read %d bytes from input stream.", byteCount));
        }

        return bytes;
    }

    private void writeBytes(byte[] bytes) throws IOException {
        outputStreamBuffer.write(bytes);
    }

    private void flush() throws IOException {
        outputStream.write(outputStreamBuffer.toByteArray());
        outputStreamBuffer.reset();
        outputStream.flush();
    }

    private enum MessageType {
        UNKNOWN,
        GAME_OVER,
        AUTHENTICATION_TOKEN,
        TEAM_SIZE,
        PROTOCOL_VERSION,
        GAME_CONTEXT,
        PLAYER_CONTEXT,
        MOVES
    }
}
