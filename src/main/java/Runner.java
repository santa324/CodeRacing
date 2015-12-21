import model.*;

import java.io.IOException;

public final class Runner {
    private final RemoteProcessClient remoteProcessClient;
    private final String token;

    public static void main(String[] args) throws IOException {
        if (args.length == 3) {
            new Runner(args).run();
        } else {
            new Runner(new String[]{"127.0.0.1", "31001", "0000000000000000"}).run();
        }
    }

    private Runner(String[] args) throws IOException {
        remoteProcessClient = new RemoteProcessClient(args[0], Integer.parseInt(args[1]));
        token = args[2];
    }

    public void run() throws IOException {
        try {
            remoteProcessClient.writeToken(token);
            int teamSize = remoteProcessClient.readTeamSize();
            remoteProcessClient.writeProtocolVersion();
            Game game = remoteProcessClient.readGameContext();

            Strategy[] strategies = new Strategy[teamSize];

            for (int strategyIndex = 0; strategyIndex < teamSize; ++strategyIndex) {
                strategies[strategyIndex] = new MyStrategy();
            }

            PlayerContext playerContext;

            while ((playerContext = remoteProcessClient.readPlayerContext()) != null) {
                Car[] playerCars = playerContext.getCars();
                if (playerCars == null || playerCars.length != teamSize) {
                    break;
                }

                Move[] moves = new Move[teamSize];

                for (int carIndex = 0; carIndex < teamSize; ++carIndex) {
                    Car playerCar = playerCars[carIndex];

                    Move move = new Move();
                    moves[carIndex] = move;
                    strategies[playerCar.getTeammateIndex()].move(
                            playerCar, playerContext.getWorld(), game, move
                    );
                }

                remoteProcessClient.writeMoves(moves);
            }
        } finally {
            remoteProcessClient.close();
        }
    }
}
