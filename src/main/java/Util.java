import java.util.Arrays;

public class Util {

    public enum Side {
        RIGHT(1, 0), BOTTOM(0, 1), LEFT(-1, 0), TOP(0, -1);

        public final int x, y;

        Side(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static Side rotateBackSide(Side side) {
        if (Side.RIGHT.equals(side)) {
            return Side.TOP;
        } else if (Side.TOP.equals(side)) {
            return Side.LEFT;
        } else if (Side.LEFT.equals(side)) {
            return Side.BOTTOM;
        } else {
            return Side.RIGHT;
        }
    }

    public static Util.Side reverse(Util.Side side) {
        if (Util.Side.LEFT.equals(side)) {
            return Util.Side.RIGHT;
        } else if (Util.Side.RIGHT.equals(side)) {
            return Util.Side.LEFT;
        } else if (Util.Side.TOP.equals(side)) {
            return Util.Side.BOTTOM;
        } else {
            return Util.Side.TOP;
        }
    }

    static class TileInfo {
        private final boolean[] borders;

        public TileInfo(Side... borderSides) {
            this.borders = new boolean[4];
            for (Side border : borderSides) {
                this.borders[border.ordinal()] = true;
            }
        }

        public boolean[] getBorders() {
            return Arrays.copyOf(borders, borders.length);
        }

        public boolean isBorder(Side side) {
            return borders[side.ordinal()];
        }
    }

    public interface Vector2RO {
        double getX();
        double getY();
    }

    public static class Vector2 implements Vector2RO {
        public double x,y;

        public Vector2() {
        }

        public Vector2(double angle) {
            this.x = Math.cos(angle);
            this.y = Math.sin(angle);
        }

        public Vector2(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public Vector2(Vector2RO vec) {
            this.x = vec.getX();
            this.y = vec.getY();
        }

        @Override
        public double getX() {
            return x;
        }

        @Override
        public double getY() {
            return y;
        }

        public double squaredLen() {
            return x*x + y*y;
        }

        public Vector2 add(Vector2RO vec) {
            this.x += vec.getX();
            this.y += vec.getY();
            return this;
        }
        public Vector2 sub(Vector2RO vec) {
            this.x -= vec.getX();
            this.y -= vec.getY();
            return this;
        }
        public Vector2 sub(double x, double y) {
            this.x -= x;
            this.y -= y;
            return this;
        }

        public Vector2 add(double x, double y) {
            this.x += x;
            this.y += y;
            return this;
        }

        public Vector2 mul(double scale) {
            this.x *= scale;
            this.y *= scale;
            return this;
        }

        public Vector2 rotate(Vector2RO angle) {
            double newX = angle.getX() * x - angle.getY() * y;
            double newY = angle.getY() * x + angle.getX() * y;
            x = newX;
            y = newY;
            return this;
        }

        public Vector2 rotateBack(Vector2RO angle) {
            double newX = angle.getX() * x + angle.getY() * y;
            double newY = angle.getX() * y - angle.getY() * x;
            x = newX;
            y = newY;
            return this;
        }

        public Vector2 rotateBack(Side side) {
            double newX = side.x * x + side.y * y;
            double newY = side.x * y - side.y * x;
            x = newX;
            y = newY;
            return this;
        }

        public Vector2 rotate(Side side) {
            double newX = side.x * x - side.y * y;
            double newY = side.y * x + side.x * y;
            x = newX;
            y = newY;
            return this;
        }

        public Vector2 reverse() {
            x = -x;
            y = -y;
            return this;
        }

        public Vector2 reflectY() {
            y = -y;
            return this;
        }
    }
}
