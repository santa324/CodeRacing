import model.*;

import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.Queue;

public class GameUtil {
    public static boolean checkCollision(RectangularUnit u1, RectangularUnit u2) {
        //todo точнее
        double r1 = Math.sqrt(u1.getHeight()*u1.getHeight() + u1.getWidth()*u1.getWidth())/2;
        double r2 = Math.sqrt(u2.getHeight()*u2.getHeight() + u2.getWidth()*u2.getWidth())/2;

        return (u1.getX() + r1) >= (u2.getX() - r2) && (u1.getX() - r1) <= (u2.getX() + r2) &&
                (u1.getY() + r1) >= (u2.getY() - r2) && (u1.getY() - r1) <= (u2.getY() + r2);
    }


    public static int[][] findPath(int wpIndex, GameData data) {
        int[][] wayPoints = data.world.getWaypoints();
        int[][] result = new int[wayPoints.length][];

        for (int i=0;i<wayPoints.length;i++) {
            int index = (wpIndex + i) % wayPoints.length;
            int wpX = wayPoints[index][0];
            int wpY = wayPoints[index][1];
            result[index] = findPath(wpX, wpY, data);
        }
        return result;
    }
    public static int[] findPath(int x, int y, GameData data) {
        int[] wayPointDist = new int[data.world.getWidth()*data.world.getHeight()];
        Arrays.fill(wayPointDist, Integer.MAX_VALUE);
        Point start = new Point(x, y, 0);
        wayPointDist[getTIndex(start.x, start.y, data.world.getWidth())] = start.dist;

        Util.Side[] sides = Util.Side.values();
        Queue<Point> points = new PriorityQueue<>();
        points.add(start);
        while(!points.isEmpty()) {
            Point point = points.poll();
            int index = getTIndex(point.x, point.y, data.world.getWidth());

            Util.TileInfo tileInfo = data.tileTypeInfo.get(data.tileTypes[point.x][point.y]);
            for (Util.Side side : sides) {
                if (!tileInfo.isBorder(side)) {
                    int nX = point.x + side.x;
                    int nY = point.y + side.y;
                    if (nX < 0 || nY < 0 || nX >= data.world.getWidth() || nY >= data.world.getHeight())
                        continue;
                    Util.TileInfo nTileInfo = data.tileTypeInfo.get(data.tileTypes[nX][nY]);
                    if (nTileInfo == null || nTileInfo.isBorder(Util.reverse(side)))
                        continue;

                    int nIndex = getTIndex(nX, nY, data.world.getWidth());
                    if (wayPointDist[nIndex] == Integer.MAX_VALUE) {
                        int nDist = wayPointDist[index] + 1;
                        wayPointDist[nIndex] = nDist;
                        points.add(new Point(nX, nY, nDist));
                    }
                }
            }
        }
        return wayPointDist;
    }

    public static int getTIndex(double x, double y, int width, double tSize) {
        return getTIndex((int)(x/tSize), (int)(y/tSize), width);
    }
    public static int getTIndex(int x, int y, int width) {
        return y * width + x;
    }

    public static class Point implements Comparable<Point> {
        public final int x,y;
        public final int dist;

        public Point(int x, int y, int dist) {
            this.x = x;
            this.y = y;
            this.dist = dist;
        }

        @Override
        public int compareTo(Point p) {
            return Integer.compare(dist, p.dist);
        }
    }
}
