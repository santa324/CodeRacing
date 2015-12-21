import model.*;

public class CarEngine<T extends UnitState.CarState> {
    public static final int SUB_STEPS = 10;
    public static final double SUB_STEPS_INV = 0.1;
    private static final double EPSILON = 1.0E-7D;

    private T state;

    private double enginePowerAim, wheelTurnAim;
    private boolean breakPedal, useNitro;

    private static final double oiledRotationFriction = 0.0017453292519943296D*SUB_STEPS_INV*SUB_STEPS_INV;

    public final long carId;
    public final double Im, Iam;
    private final double forwardPowerFactor, rearPowerFactor;
    private final double crosswiseFriction, lengthwiseFriction, airFriction;
    private final double rotationFriction, rotationAirFriction;
    private final double angularSpeedFactor;
    public final double wheelTurnPerTick, enginePowerPerTick, nitroEnginePower;
    public final short nitroDurationTicks, nitroCooldownTicks;
    private final Borders borders;

    private double lFriction, cFriction, aFriction, powerFactor;

    public CarEngine(Car car, GameData data) {
        this.carId = car.getId();
        this.Im = 1.0 / car.getMass();
        this.Iam = 12.0D * this.Im / (car.getWidth()*car.getWidth() + car.getHeight()*car.getHeight());

        if (CarType.BUGGY.equals(car.getType())) {
            this.forwardPowerFactor = data.game.getBuggyEngineForwardPower()*SUB_STEPS_INV*SUB_STEPS_INV* Im;
            this.rearPowerFactor = data.game.getBuggyEngineRearPower()*SUB_STEPS_INV*SUB_STEPS_INV* Im;
        } else if (CarType.JEEP.equals(car.getType())) {
            this.forwardPowerFactor = data.game.getJeepEngineForwardPower()*SUB_STEPS_INV*SUB_STEPS_INV* Im;
            this.rearPowerFactor = data.game.getJeepEngineRearPower()*SUB_STEPS_INV*SUB_STEPS_INV* Im;
        } else {
            throw new IllegalStateException("unknown CarType!");
        }
        this.crosswiseFriction = data.game.getCarCrosswiseMovementFrictionFactor()*SUB_STEPS_INV*SUB_STEPS_INV;
        this.lengthwiseFriction = data.game.getCarLengthwiseMovementFrictionFactor()*SUB_STEPS_INV*SUB_STEPS_INV;
        this.airFriction = Math.pow(1.0 - data.game.getCarMovementAirFrictionFactor(), SUB_STEPS_INV);
        this.rotationFriction = data.game.getCarRotationFrictionFactor()*SUB_STEPS_INV*SUB_STEPS_INV;
        this.rotationAirFriction = Math.pow(1.0 - data.game.getCarRotationAirFrictionFactor(), SUB_STEPS_INV);
        this.angularSpeedFactor = data.game.getCarAngularSpeedFactor();    //0.0017453292519943296
        this.wheelTurnPerTick = data.game.getCarWheelTurnChangePerTick();
        this.enginePowerPerTick = data.game.getCarEnginePowerChangePerTick();
        this.nitroEnginePower = data.game.getNitroEnginePowerFactor();
        this.nitroDurationTicks = (short)data.game.getNitroDurationTicks();
        this.nitroCooldownTicks = (short)data.game.getUseNitroCooldownTicks();
        this.borders = data.borders;
    }

    public UnitState.CarState createState(Car car, UnitState prevState, Game game) {
        double prevVl = prevState.ax * prevState.Vx + prevState.ay * prevState.Vy;
        return new UnitState.CarState(car, car.getWheelTurn()*angularSpeedFactor*prevVl, game);
    }

    public T getState() {
        return state;
    }
    public void setState(T state) {
        this.state = state;
    }

    public void setEnginePowerAim(double enginePowerAim) {
        if (enginePowerAim > 1.0) {
            this.enginePowerAim = 1.0;
        } else if (enginePowerAim < -1.0) {
            this.enginePowerAim = -1.0;
        } else {
            this.enginePowerAim = enginePowerAim;
        }
    }
    public void setWheelTurnAim(double wheelTurnAim) {
        if (wheelTurnAim > 1.0) {
            this.wheelTurnAim = 1.0;
        } else if (wheelTurnAim < -1.0) {
            this.wheelTurnAim = -1.0;
        } else {
            this.wheelTurnAim = wheelTurnAim;
        }
    }
    public double getEnginePowerAim() {
        return enginePowerAim;
    }
    public double getWheelTurnAim() {
        return wheelTurnAim;
    }

    public boolean isBreakPedal() {
        return breakPedal;
    }
    public void setBreakPedal(boolean breakPedal) {
        this.breakPedal = breakPedal;
    }

    public void setUseNitro(boolean useNitro) {
        this.useNitro = useNitro;
    }

    protected double Fx, Fy;
    private double angleVwX, angleVwY;
    public double Vaw;
    public void perStepPre() {
        if (state.remainingOiledTicks > 0) {
            state.remainingOiledTicks--;
            cFriction = lengthwiseFriction;
            lFriction = lengthwiseFriction;
            aFriction = oiledRotationFriction;
        } else {
            cFriction = crosswiseFriction;
            aFriction = rotationFriction;
            this.lFriction = breakPedal ? crosswiseFriction : lengthwiseFriction;
        }

        if (useNitro && state.nitroChargeCount > 0 && state.lastNitroUseTickPass >= nitroCooldownTicks) {
            state.nitroChargeCount--;
            state.nitroChargeCountUsed++;
            state.lastNitroUseTickPass = 0;
        }

        //wheelTurnPerTick
        if (state.wheelTurn < wheelTurnAim) {
            state.wheelTurn += wheelTurnPerTick;
            if (state.wheelTurn > wheelTurnAim)
                state.wheelTurn = wheelTurnAim;
        } else if (state.wheelTurn > wheelTurnAim) {
            state.wheelTurn -= wheelTurnPerTick;
            if (state.wheelTurn < wheelTurnAim)
                state.wheelTurn = wheelTurnAim;
        }
        if (state.lastNitroUseTickPass < nitroDurationTicks) {
            state.enginePower = nitroEnginePower;
        }
        if (state.enginePower == nitroEnginePower && state.lastNitroUseTickPass == nitroDurationTicks) {
            state.enginePower = 1.0;
        }

        if (state.lastNitroUseTickPass >= nitroDurationTicks) {
            //enginePowerPerTick
            if (state.enginePower < enginePowerAim) {
                state.enginePower += enginePowerPerTick;
                if (state.enginePower > enginePowerAim)
                    state.enginePower = enginePowerAim;
            } else if (state.enginePower > enginePowerAim) {
                state.enginePower -= enginePowerPerTick;
                if (state.enginePower < enginePowerAim)
                    state.enginePower = enginePowerAim;
            }
        }

        //wheelTurn
        double Vl = state.ax * state.Vx + state.ay * state.Vy;
        this.Vaw = state.wheelTurn * angularSpeedFactor * Vl;

        this.angleVwX = Math.cos(Vaw);
        this.angleVwY = Math.sin(Vaw);

        double ep = state.enginePower;
        if (state.lastNitroUseTickPass < nitroDurationTicks)
            ep = nitroEnginePower;

        double f = ep * (ep > 0 ? forwardPowerFactor : rearPowerFactor);
        state.lastNitroUseTickPass++;
        if (state.lastNitroUseTickPass > nitroCooldownTicks) {
            state.lastNitroUseTickPass = nitroCooldownTicks;
        }

        this.Fx = state.ax * f;
        this.Fy = state.ay * f;
    }

    public void perStepPost() {}

    public void subStep() {
        state.x += state.Vx;
        state.y += state.Vy;

        //enginePower
        if (!breakPedal) {
            state.Vx += Fx;
            state.Vy += Fy;
        }

        //airFriction
        state.Vx *= airFriction;
        state.Vy *= airFriction;

//            if (Math.abs(state.Vx) < EPSILON)
//                state.Vx = 0;
//            if (Math.abs(state.Vy) < EPSILON)
//                state.Vy = 0;


        double Vl = state.ax * state.Vx + state.ay * state.Vy;
        double Vr = state.ax * state.Vy - state.ay * state.Vx;

        //Friction
        if (Vl > lFriction) {
            Vl -= lFriction;
        } else if (Vl < -lFriction) {
            Vl += lFriction;
        } else {
            Vl = 0;
        }

        //Friction
        if (Vr > cFriction) {
            Vr -= cFriction;
        } else if (Vr < -cFriction) {
            Vr += cFriction;
        } else {
            Vr = 0;
        }

        state.Vx = state.ax * Vl - state.ay * Vr;
        state.Vy = state.ay * Vl + state.ax * Vr;


        double newAX = angleVwX * state.ax - angleVwY * state.ay;
        double newAY = angleVwY * state.ax + angleVwX * state.ay;
        state.ax = newAX;
        state.ay = newAY;

        if (state.Va != 0) {
            double Vax = Math.cos(state.Va);
            double Vay = Math.sin(state.Va);
            newAX = Vax * state.ax - Vay * state.ay;
            newAY = Vay * state.ax + Vax * state.ay;
            state.ax = newAX;
            state.ay = newAY;

            //rotationAirFriction
            state.Va *= rotationAirFriction;

            //rotationFriction
            if (state.Va > aFriction) {
                state.Va -= aFriction;
            } else if (state.Va < -aFriction) {
                state.Va += aFriction;
            } else {
                state.Va = 0;
            }
        }
    }
}
