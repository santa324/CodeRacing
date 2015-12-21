import model.*;

public class UnitState {
    public double x,y,Va,Vx,Vy,ax,ay;

    public UnitState() {}

    public UnitState(UnitState state) {
        this.x = state.x;
        this.y = state.y;
        this.ax = state.ax;
        this.ay = state.ay;
        this.Vx = state.Vx;
        this.Vy = state.Vy;
        this.Va = state.Va;
    }

    public UnitState(Unit unit) {
        this.x = unit.getX();
        this.y = unit.getY();
        Util.Vector2 angle = new Util.Vector2(unit.getAngle());
        this.ax = angle.x;
        this.ay = angle.y;
        this.Vx = unit.getSpeedX()*CarEngine.SUB_STEPS_INV;
        this.Vy = unit.getSpeedY()*CarEngine.SUB_STEPS_INV;
        this.Va = unit.getAngularSpeed()*CarEngine.SUB_STEPS_INV;
    }

    public static class CarState extends UnitState {
        public float durability;
        public double enginePower, wheelTurn;
        public byte nitroChargeCount, oilCanisterCount, projectileCount;
        public byte nitroChargeCountUsed;
        public short lastNitroUseTickPass, remainingOiledTicks;
        public boolean collision;

        public CarState() {}

        public CarState(CarState state) {
            super(state);
            this.enginePower = state.enginePower;
            this.wheelTurn = state.wheelTurn;
            this.nitroChargeCount = state.nitroChargeCount;
            this.nitroChargeCountUsed = state.nitroChargeCountUsed;
            this.oilCanisterCount = state.oilCanisterCount;
            this.projectileCount = state.projectileCount;
            this.durability = state.durability;
            this.lastNitroUseTickPass = state.lastNitroUseTickPass;
            this.remainingOiledTicks = state.remainingOiledTicks;
            this.collision = state.collision;
        }

        public CarState(Car car, double Vaw, Game game) {
            super(car);
            this.enginePower = car.getEnginePower();
            this.wheelTurn = car.getWheelTurn();
            this.Va -= Vaw;
            this.nitroChargeCount = (byte)car.getNitroChargeCount();
            this.oilCanisterCount = (byte)car.getOilCanisterCount();
            this.projectileCount = (byte)car.getProjectileCount();
            this.durability = (float)car.getDurability();
            this.lastNitroUseTickPass = (short)(game.getUseNitroCooldownTicks() - car.getRemainingNitroCooldownTicks());
            this.remainingOiledTicks = (short)car.getRemainingOiledTicks();
        }

        public CarState copy() {
            return new CarState(this);
        }


        public boolean nearEquals(CarState other, double epsilon) {
            boolean result = Math.abs(this.x - other.x) < epsilon &&
                    Math.abs(this.y - other.y) < epsilon &&
                    Math.abs(this.Vx - other.Vx) < epsilon &&
                    Math.abs(this.Vy - other.Vy) < epsilon &&
                    Math.abs(this.Va - other.Va) < epsilon &&
                    Math.abs(this.ax - other.ax) < epsilon &&
                    Math.abs(this.ay - other.ay) < epsilon &&
                    Math.abs(this.enginePower - other.enginePower) < epsilon &&
                    this.nitroChargeCount == other.nitroChargeCount &&
//                    this.oilCanisterCount == other.oilCanisterCount &&      //todo не влияет на рассчеты
//                    this.projectileCount == other.projectileCount &&
                    this.lastNitroUseTickPass == other.lastNitroUseTickPass &&
                    this.remainingOiledTicks == other.remainingOiledTicks &&
                    Math.abs(this.durability - other.durability) < epsilon;

            if (!result) {
                int jj = 0;
            }
            return result;
        }
    }
}
