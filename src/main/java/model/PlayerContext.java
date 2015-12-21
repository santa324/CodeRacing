package model;

import java.util.Arrays;

public final class PlayerContext {
    private final Car[] cars;
    private final World world;

    public PlayerContext(Car[] cars, World world) {
        this.cars = Arrays.copyOf(cars, cars.length);
        this.world = world;
    }

    public Car[] getCars() {
        return Arrays.copyOf(cars, cars.length);
    }

    public World getWorld() {
        return world;
    }
}
