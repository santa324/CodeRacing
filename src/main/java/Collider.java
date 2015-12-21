public class Collider {
    public static class Collision {
        public double x, y, normalX, normalY, deep;

        public Collision(double x, double y, double normalX, double normalY, double deep) {
            this.x = x;
            this.y = y;
            this.normalX = normalX;
            this.normalY = normalY;
            this.deep = deep;
        }
    }

    public static class BoxBox {
        private final double wight2, height2;

        public BoxBox(double wight2, double height2) {
            this.wight2 = wight2;
            this.height2 = height2;
        }

        public boolean checkBox(double b1x, double b1y, double b1ax, double b1ay, double b2x, double b2y, double b2ax, double b2ay) {
            return checkCentredPart(b1ax, b1ay, b2x - b1x, b2y - b1y, b2ax, b2ay) && checkCentredPart(b2ax, b2ay, b1x - b2x, b1y - b2y, b1ax, b1ay);
        }

        public boolean checkCentredPart(double b1ax, double b1ay, double b2x, double b2y, double b2ax, double b2ay) {
            double bx = b1ax * b2x + b1ay * b2y;
            double by = b1ax * b2y - b1ay * b2x;
            double bax = b1ax * b2ax + b1ay * b2ay;
            double bay = b1ax * b2ay - b1ay * b2ax;
            return checkCentredNormalizedPart(bx, by, bax, bay);
        }

        public boolean checkCentredNormalizedPart(double x, double y, double ax, double ay) {
            double axw = ax*wight2;
            double axh = ax*height2;
            double ayw = ay*wight2;
            double ayh = ay*height2;

            double rtX = axw + ayh;
            double rtY = ayw - axh;
            double rbX = axw - ayh;
            double rbY = ayw + axh;

            double maxX = Math.max(Math.abs(rtX),Math.abs(rbX));
            double maxY = Math.max(Math.abs(rtY),Math.abs(rbY));

            maxX += wight2;
            maxY += height2;

            return (x > -maxX && x < maxX && y > -maxY && y < maxY);
        }
    }

    public static class CircleBox {
        private final double cRadius, cRadius2;
        private final double wight2, height2;

        public CircleBox(double cRadius, double wight2, double height2) {
            this.cRadius = cRadius;
            this.wight2 = wight2;
            this.height2 = height2;
            this.cRadius2 = cRadius*cRadius;
        }

        public Collision checkBoxCentred(double cBoxX, double cBoxY, double ax, double ay) {
            double cx = ax * cBoxX + ay * cBoxY;
            double cy = ax * cBoxY - ay * cBoxX;
            Collision nCollision = checkBoxCentredNormalized(cx, cy);
            if (nCollision != null) {
                double newCX = ax * nCollision.x - ay * nCollision.y;
                double newCY = ay * nCollision.x + ax * nCollision.y;
                double newNX = ax * nCollision.normalX - ay * nCollision.normalY;
                double newNY = ay * nCollision.normalX + ax * nCollision.normalY;

                nCollision.x = newCX;
                nCollision.y = newCY;
                nCollision.normalX = newNX;
                nCollision.normalY = newNY;
            }

            return nCollision;
        }

        public Collision checkBoxCentredNormalized(double cx, double cy) {
            if (cx > -wight2) {
                if (cx < wight2) {
                    if (cy < 0) {
                        double deep = cRadius + height2 + cy;
                        if (deep > 0)
                            return new Collision(cx, -height2, 0, 1, deep);
                    } else {
                        double deep = cRadius + height2 - cy;
                        if (deep > 0)
                            return new Collision(cx, height2, 0, -1, deep);
                    }
                } else {
                    if (cy > -height2) {
                        if (cy < height2) {
                            double deep = cRadius + wight2 - cx;
                            if (deep > 0)
                                return new Collision(wight2, cy, -1, 0, deep);
                        } else {
                            return collidePoint(cx, cy, wight2, height2);
                        }
                    } else {
                        return collidePoint(cx, cy, wight2, -height2);
                    }
                }
            } else {
                if (cy > -height2) {
                    if (cy < height2) {
                        double deep = cRadius + wight2 + cx;
                        if (deep > 0)
                            return new Collision(-wight2, cy, 1, 0, deep);
                    } else {
                        return collidePoint(cx, cy, -wight2, height2);
                    }
                } else {
                    return collidePoint(cx, cy, -wight2, -height2);
                }
            }
            return null;
        }

        private Collision collidePoint(double cx, double cy, double x, double y) {
            double nx = x - cx;
            double ny = y - cy;
            double r2 = nx*nx + ny*ny;
            if (r2 < cRadius2) {
                double r = Math.sqrt(r2);
                nx /= r;
                ny /= r;
                return new Collision(x, y, nx, ny, cRadius - r);
            }

            return null;
        }
    }
}
