import model.*;

import java.util.*;

public class WorldEngine<T extends UnitState.CarState, K extends CarEngine<T>, E extends WorldEngine.State<T>> {
    private final double slickRadius2;
    private final short maxOiledStateDurationTicks;
    private final double tireDamageDactor, minTireSpeed, minTireSpeed2;
    private final double tireMInv, tireAMInv;
    private final double washerDamage;

    private final Borders borders;
    private final Bonus[] bonuses;
    private final OilSlick[] oilSlicks;
    private final int pureScoreAmount;
    private final E[] bestTeammateTrack;

    private boolean resolveBorderCollisions;
    private final K[] carEngine;
    private E state;

    private final Collider.BoxBox carCarCollider;
    private final Collider.CircleBox carTireCollider, carWasherCollider;

    public WorldEngine(K[] carEngine, GameData data, OilSlick[] oilSlicks, E[] bestTeammateTrack) {
        this.bestTeammateTrack = bestTeammateTrack;

        this.slickRadius2 = data.game.getOilSlickRadius() * data.game.getOilSlickRadius();
        this.maxOiledStateDurationTicks = (short) data.game.getMaxOiledStateDurationTicks();

        this.carEngine = carEngine;
        this.borders = data.borders;
        this.bonuses = data.bonuses;
        this.oilSlicks = oilSlicks;
        this.pureScoreAmount = data.game.getPureScoreAmount();
        this.tireDamageDactor = CarEngine.SUB_STEPS * data.game.getTireDamageFactor() / data.game.getTireInitialSpeed();
        this.minTireSpeed = data.game.getTireDisappearSpeedFactor() * data.game.getTireInitialSpeed() * CarEngine.SUB_STEPS_INV;
        this.minTireSpeed2 = this.minTireSpeed * this.minTireSpeed;

        this.carTireCollider = new Collider.CircleBox(data.game.getTireRadius(), data.game.getCarWidth() / 2.0, data.game.getCarHeight() / 2.0);
        this.tireMInv = 1.0 / data.game.getTireMass();
        this.tireAMInv = 2.0 * tireMInv / (data.game.getTireRadius() * data.game.getTireRadius());

        this.carWasherCollider = new Collider.CircleBox(data.game.getWasherRadius(), data.game.getCarWidth() / 2.0, data.game.getCarHeight() / 2.0);
        this.washerDamage = data.game.getWasherDamage();

        this.carCarCollider = new Collider.BoxBox(data.game.getCarWidth() / 2.0, data.game.getCarHeight() / 2.0);
    }

    public void setResolveBorderCollisions(boolean resolveBorderCollisions) {
        this.resolveBorderCollisions = resolveBorderCollisions;
    }

    public E getState() {
        return state;
    }

    public void setState(E state) {
        this.state = state;
        for (int i = 0; i < this.carEngine.length; i++) {
            this.carEngine[i].setState(state.getCarState(i));
        }
    }

    public int getCarCount() {
        return carEngine.length;
    }

    public K getCarEngine(int index) {
        return carEngine[index];
    }

    public void perStep() {
        for (K cEngine : this.carEngine) {
            cEngine.perStepPre();
        }

        for (int t = 0; t < CarEngine.SUB_STEPS; t++) {
            for (int i = 0; i < state.projectileStates.length; i++) {
                ProjectileState pState = state.projectileStates[i];
                if (pState != null) {
                    pState.x += pState.Vx;
                    pState.y += pState.Vy;

                    if (i < state.tireCount) {
                        if (borders.collideCircle(pState)) {
                            if ((pState.Vx * pState.Vx + pState.Vy * pState.Vy) < minTireSpeed2) {
                                state.projectileStates[i] = null;
                                break;
                            }
                        }
                    }
                }
            }

            for (K cEngine : this.carEngine) {
                cEngine.subStep();
                T carState = cEngine.getState();

                if (borders.collideBox(carState, cEngine.Vaw, cEngine.Im, cEngine.Iam, resolveBorderCollisions))
                    carState.collision = true;

                for (int i = 0; i < state.projectileStates.length; i++) {
                    ProjectileState pState = state.projectileStates[i];
                    if (pState != null) {
                        if (i < state.tireCount) {
                            if (Math.abs(pState.x - carState.x) > 200 || Math.abs(pState.y - carState.y) > 200)
                                continue;

                            if (pState.carId == cEngine.carId && (pState.Vx * pState.Vx + pState.Vy * pState.Vy) > 35.9)
                                continue;

                            collideCarTire(cEngine, carState, pState);

                            if ((pState.Vx * pState.Vx + pState.Vy * pState.Vy) < minTireSpeed2) {
                                state.projectileStates[i] = null;
                            }
                        } else {
                            if (Math.abs(pState.x - carState.x) > 145 || Math.abs(pState.y - carState.y) > 145)
                                continue;

                            if (pState.carId == cEngine.carId)
                                continue;

                            if (collideCarWasher(cEngine, carState, pState)) {
                                state.projectileStates[i] = null;
                            }
                        }
                    }
                }
            }
        }


        E tState = null;

        state.teammateCollisionV2 = -1;
        if (bestTeammateTrack != null && state.tick < bestTeammateTrack.length) {
            tState = bestTeammateTrack[state.tick];
            T tCarState = tState.getCarState(0);

            T cState = state.getCarState(0);

            if (carCarCollider.checkBox(cState.x, cState.y, cState.ax, cState.ay, tCarState.x, tCarState.y, tCarState.ax, tCarState.ay)) {
                double dVx = cState.Vx - tCarState.Vx;
                double dVy = cState.Vy - tCarState.Vy;
                state.teammateCollisionV2 = (dVx*dVx + dVy*dVy);
            }
        }


        for (K cEngine : this.carEngine) {
            cEngine.perStepPost();
            T carState = cEngine.getState();

            int bonusIndex = borders.checkBonusCollision(carState, state.bonuses1, state.bonuses2);
            if (bonusIndex >= 0) {
                if (bonusIndex < 64) {
                    if (tState != null && (tState.bonuses1 & (1L << bonusIndex)) == 0)
                        continue;

                    state.bonuses1 &= ~(1L << bonusIndex);
                } else {
                    if (tState != null && (tState.bonuses2 & (1L << (bonusIndex - 64))) == 0)
                        continue;

                    state.bonuses2 &= ~(1L << (bonusIndex - 64));
                }

                Bonus bonus = bonuses[bonusIndex];
                if (BonusType.NITRO_BOOST.equals(bonus.getType())) {
                    carState.nitroChargeCount++;
                } else if (BonusType.OIL_CANISTER.equals(bonus.getType())) {
                    carState.oilCanisterCount++;
                } else if (BonusType.AMMO_CRATE.equals(bonus.getType())) {
                    carState.projectileCount++;
                } else if (BonusType.REPAIR_KIT.equals(bonus.getType())) {
                    carState.durability = 1.0f;
                } else if (BonusType.PURE_SCORE.equals(bonus.getType())) {
                    state.score += pureScoreAmount;
                }
            }
        }


//        double angularSpeedRandomFactor = 0.0023271056693257726D;
        for (OilSlick slick : oilSlicks) {
            if (slick.getRemainingLifetime() > state.tick) {
                for (K aCarEngine : this.carEngine) {
                    T carState = aCarEngine.getState();
                    if (carState.remainingOiledTicks > 0)
                        continue;

                    double dx = carState.x - slick.getX();
                    double dy = carState.y - slick.getY();
                    if ((dx * dx + dy * dy) < this.slickRadius2) {
                        short oiledStateTicks = (short) Math.min(maxOiledStateDurationTicks, slick.getRemainingLifetime());
//                        slickState.remainingLifetime -= oiledStateTicks;
                        carState.remainingOiledTicks = oiledStateTicks;

//                        double rndVa = angularSpeedRandomFactor * Math.sqrt(carState.Vx*carState.Vx + carState.Vy*carState.Vy);
//                        carState.Va += rndVa;
                    }
                }
            }
        }

        state.tick++;
    }

    private boolean collideCarWasher(K cEngine, T carState, ProjectileState pState) {
        Collider.Collision collision = carWasherCollider.checkBoxCentred(pState.x - carState.x, pState.y - carState.y, carState.ax, carState.ay);
        if (collision != null) {
            //todo impulse

            carState.durability -= washerDamage;
            if (carState.durability < 0)
                carState.durability = 0;

            return true;
        }
        return false;
    }

    private void collideCarTire(K cEngine, T carState, ProjectileState pState) {
        Collider.Collision collision = carTireCollider.checkBoxCentred(pState.x - carState.x, pState.y - carState.y, carState.ax, carState.ay);
        if (collision != null) {
            double carToCX = collision.x;
            double carToCY = collision.y;

            double tireToCX = carState.x + collision.x - pState.x;
            double tireToCY = carState.y + collision.y - pState.y;

            double rvCx = pState.Vx - tireToCY * pState.Va - (carState.Vx - carToCY *(carState.Va + cEngine.Vaw));
            double rvCy = pState.Vy + tireToCX * pState.Va - (carState.Vy + carToCX *(carState.Va + cEngine.Vaw));
            double normalRelativeVelocityLengthC = rvCx * collision.normalX + rvCy * collision.normalY;

            if (normalRelativeVelocityLengthC > -Borders.EPSILON) {
                double impulseChangeX = 0;
                double impulseChangeY = 0;

                double carL = carToCX * collision.normalY - carToCY * collision.normalX;

                double denominatorR = cEngine.Im + tireMInv + cEngine.Iam * carL * carL;
                double impulseChangeR = (1.0D + Borders.M_TRANSFER) * normalRelativeVelocityLengthC / denominatorR;

                impulseChangeX += collision.normalX * impulseChangeR;
                impulseChangeY += collision.normalY * impulseChangeR;
                carState.Va += carL * impulseChangeR * cEngine.Iam;

                double relativeVt = rvCy * collision.normalX - rvCx * collision.normalY;
                double relativeVt2 = relativeVt * relativeVt;
                if (relativeVt2 > Borders.EPSILON2) {
                    double carR = carToCX * collision.normalX + carToCY * collision.normalY;
                    double tireR = tireToCX * collision.normalX + tireToCY * collision.normalY;
                    double denominatorL = cEngine.Im + tireMInv + cEngine.Iam * carR * carR + tireAMInv * tireR * tireR;

                    double surfaceFriction = Borders.S_FRICTION * Math.abs(normalRelativeVelocityLengthC) / Math.sqrt(rvCx * rvCx + rvCy * rvCy);
                    double impulseChangeL = 1.0D * surfaceFriction * relativeVt / denominatorL;

                    impulseChangeX += -collision.normalY * impulseChangeL;
                    impulseChangeY += collision.normalX * impulseChangeL;
                    carState.Va += carR * impulseChangeL * cEngine.Iam;
                    pState.Va -= tireR * impulseChangeL * tireAMInv;
                }

                carState.Vx += impulseChangeX * cEngine.Im;
                carState.Vy += impulseChangeY * cEngine.Im;

                pState.Vx -= impulseChangeX * tireMInv;
                pState.Vy -= impulseChangeY * tireMInv;


                carState.durability -= normalRelativeVelocityLengthC * tireDamageDactor;
                if (carState.durability < 0)
                    carState.durability = 0;
            }

            if (collision.deep >= Borders.EPSILON) {
                double pb = 0.5D * (collision.deep + Borders.EPSILON);

                carState.x += collision.normalX * pb;
                carState.y += collision.normalY * pb;
                pState.x -= collision.normalX * pb;
                pState.y -= collision.normalY * pb;
            }
        }
    }

    public static class State<K extends UnitState.CarState> {
        private final Object[] carState;
        public short score, tick;
        public long bonuses1, bonuses2;
        public byte tireCount;
        public ProjectileState[] projectileStates;
        public double teammateCollisionV2 = -1;

        public State(K[] carState, GameData data, Projectile[] projectilesArrays) {
            this.carState = carState;
            this.score = (short) data.world.getMyPlayer().getScore();

            if (data.bonuses.length < 64) {
                bonuses1 = -1L >>> (64 - data.bonuses.length);
            } else {
                bonuses1 = -1L;
                bonuses2 = -1L >>> (128 - data.bonuses.length);
            }

            if (projectilesArrays == null)
                projectilesArrays = new Projectile[0];
            List<Projectile> projectiles = new LinkedList<>(Arrays.asList(projectilesArrays));

            Collections.sort(projectiles, new Comparator<Projectile>() {
                @Override
                public int compare(Projectile o1, Projectile o2) {
                    if (ProjectileType.TIRE.equals(o1.getType()) && !ProjectileType.TIRE.equals(o2.getType()))
                        return -1;
                    if (!ProjectileType.TIRE.equals(o1.getType()) && ProjectileType.TIRE.equals(o2.getType()))
                        return 1;

                    if (o1.getX() != o2.getX())
                        return Double.compare(o1.getX(), o2.getX());

                    return Double.compare(o1.getY(), o2.getY());
                }
            });
            projectileStates = new ProjectileState[projectiles.size()];
            for (int i = 0; i < projectiles.size(); i++) {
                Projectile projectle = projectiles.get(i);
                if (ProjectileType.TIRE.equals(projectle.getType()))
                    this.tireCount++;
                projectileStates[i] = new ProjectileState(projectle);
            }
        }

        public K getCarState(int index) {
            return (K) carState[index];
        }

        public State(State<K> state) {
            this.carState = new Object[state.carState.length];
            for (int i = 0; i < state.carState.length; i++) {
                this.carState[i] = ((UnitState.CarState) state.carState[i]).copy();
            }
            this.teammateCollisionV2 = state.teammateCollisionV2;
            this.score = state.score;
            this.bonuses1 = state.bonuses1;
            this.bonuses2 = state.bonuses2;
            this.tick = state.tick;
            this.tireCount = state.tireCount;
            this.projectileStates = new ProjectileState[state.projectileStates.length];
            for (int i = 0; i < this.projectileStates.length; i++) {
                if (state.projectileStates[i] != null) {
                    this.projectileStates[i] = state.projectileStates[i].copy();
                }
            }
        }

        public State<K> copy() {
            return new State<>(this);
        }

        public boolean nearEquals(State<K> other, double epsilon) {
            if (this.carState.length != other.carState.length)
                return false;

            for (int i = 0; i < this.carState.length; i++) {
                UnitState.CarState carState = (UnitState.CarState) this.carState[i];
                UnitState.CarState carStateOther = (UnitState.CarState) other.carState[i];
                if (!carState.nearEquals(carStateOther, epsilon))
                    return false;
            }

            List<ProjectileState> thisProjectiles = normalizeProjectiles(this.projectileStates);
            List<ProjectileState> otherProjectiles = normalizeProjectiles(other.projectileStates);

            if (thisProjectiles.size() != otherProjectiles.size())
                return false;

            for (int i = 0; i < thisProjectiles.size(); i++) {
                ProjectileState thisProjectile = thisProjectiles.get(i);
                ProjectileState otherProjectile = otherProjectiles.get(i);
                if (!thisProjectile.nearEquals(otherProjectile, epsilon))
                    return false;
            }

            //todo баг с бонусами (сравнение не корректно - каждый тик массив бонусов обновляется)
            return Math.abs(this.teammateCollisionV2 - other.teammateCollisionV2) < epsilon && this.tireCount == other.tireCount && this.bonuses1 == other.bonuses1 && this.bonuses2 == other.bonuses2/* && this.score == other.score*/;
        }

        private static List<ProjectileState> normalizeProjectiles(ProjectileState[] projectileStates) {
            List<ProjectileState> projectiles = new LinkedList<>(Arrays.asList(projectileStates));
            Iterator<ProjectileState> projectilesIterator = projectiles.iterator();
            while (projectilesIterator.hasNext()) {
                if (projectilesIterator.next() == null)
                    projectilesIterator.remove();
            }
            Collections.sort(projectiles, new Comparator<ProjectileState>() {
                @Override
                public int compare(ProjectileState o1, ProjectileState o2) {
                    if (o1.x != o2.x)
                        return Double.compare(o1.x, o2.x);

                    return Double.compare(o1.y, o2.y);
                }
            });
            return projectiles;
        }
    }

    public static class ProjectileState {
        public double x, y, Vx, Vy, Va;
        public final long carId;

        public ProjectileState(Projectile projectile) {
            this.x = projectile.getX();
            this.y = projectile.getY();
            this.Vx = projectile.getSpeedX() * CarEngine.SUB_STEPS_INV;
            this.Vy = projectile.getSpeedY() * CarEngine.SUB_STEPS_INV;
            this.Va = projectile.getAngularSpeed() * CarEngine.SUB_STEPS_INV;
            this.carId = projectile.getCarId();
        }

        public ProjectileState(ProjectileState state) {
            this.x = state.x;
            this.y = state.y;
            this.Vx = state.Vx;
            this.Vy = state.Vy;
            this.Va = state.Va;
            this.carId = state.carId;
        }

        public ProjectileState copy() {
            return new ProjectileState(this);
        }

        public boolean nearEquals(ProjectileState other, double epsilon) {
            return Math.abs(this.x - other.x) < epsilon && Math.abs(this.y - other.y) < epsilon &&
                    Math.abs(this.Vx - other.Vx) < epsilon && Math.abs(this.Vy - other.Vy) < epsilon &&
                    Math.abs(this.Va - other.Va) < epsilon && this.carId == other.carId;
        }
    }
}
