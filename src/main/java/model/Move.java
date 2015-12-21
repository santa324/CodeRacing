package model;

/**
 * Стратегия игрока может управлять кодемобилем посредством установки свойств объекта данного класса.
 */
public class Move {
    private double enginePower;
    private boolean brake;
    private double wheelTurn;
    private boolean throwProjectile;
    private boolean useNitro;
    private boolean spillOil;

    /**
     * @return Возвращает текущую установку режима работы двигателя кодемобиля.
     */
    public double getEnginePower() {
        return enginePower;
    }

    /**
     * Задаёт установку режима работы двигателя кодемобиля.
     * <p/>
     * Установка режима работы является относительной и должна лежать в интервале от {@code -1.0} до {@code 1.0}.
     * Значения, выходящие за указанный интервал, будут приведены к ближайшей его границе.
     * <p/>
     * Реальный режим работы двигателя может отличаться от установки, так как его изменение происходит не мгновенно, а
     * со скоростью не более {@code game.carEnginePowerChangePerTick} за тик. Режим работы двигателя фактически
     * определяет ускорение в направлении, совпадающем с углом поворота кодемобиля. Абсолютное значение ускорения
     * равномерно изменяется на интервале от {@code -1.0} до {@code 0.0} и на интервале от {@code 0.0} до {@code 1.0}.
     */
    public void setEnginePower(double enginePower) {
        this.enginePower = enginePower;
    }

    /**
     * @return Возвращает текущее положение педали тормоза.
     */
    public boolean isBrake() {
        return brake;
    }

    /**
     * Задаёт текущее положение педали тормоза.
     * <p/>
     * При утопленной педали тормоза значение силы трения вдоль направления, совпадающего с углом поворота кодемобиля,
     * возрастает с {@code game.carLengthwiseMovementFrictionFactor} до {@code game.carCrosswiseMovementFrictionFactor}.
     */
    public void setBrake(boolean brake) {
        this.brake = brake;
    }

    /**
     * @return Возвращает текущий относительный угол поворота колёс кодемобиля.
     */
    public double getWheelTurn() {
        return wheelTurn;
    }

    /**
     * Задаёт относительный угол поворота колёс (или руля, что эквивалентно) кодемобиля.
     * <p/>
     * Относительный угол должен лежать в интервале от {@code -1.0} до {@code 1.0}.
     * Значения, выходящие за указанный интервал, будут приведены к ближайшей его границе.
     * <p/>
     * Реальный поворот колёс может отличаться от установки, так как его изменение происходит не мгновенно, а
     * со скоростью не более {@code game.carWheelTurnChangePerTick} за тик. Поворот колёс создаёт добавочную угловую
     * скорость кодемобиля (помимо угловой скорости, вызванной соударениями объектов и другими причинами), значение
     * которой прямо пропорционально текущему относительному углу поворота колёс кодемобиля ({@code car.wheelTurn}),
     * коэффициенту {@code game.carAngularSpeedFactor}, а также скалярному произведению вектора скорости кодемобиля и
     * единичного вектора, направление которого совпадает с направлением кодемобиля.
     */
    public void setWheelTurn(double wheelTurn) {
        this.wheelTurn = wheelTurn;
    }

    /**
     * @return Возвращает текущее значение указания метнуть снаряд.
     */
    public boolean isThrowProjectile() {
        return throwProjectile;
    }

    /**
     * Устанавливает значение указания метнуть снаряд.
     * <p/>
     * Указание может быть проигнорировано, если у кодемобиля не осталось снарядов
     * либо прошло менее {@code game.throwProjectileCooldownTicks} тиков с момента запуска предыдущего снаряда.
     */
    public void setThrowProjectile(boolean throwProjectile) {
        this.throwProjectile = throwProjectile;
    }

    /**
     * @return Возвращает текущее значение указания использовать <<нитро>>.
     */
    public boolean isUseNitro() {
        return useNitro;
    }

    /**
     * Устанавливает значение указания использовать <<нитро>>.
     * <p/>
     * Указание может быть проигнорировано, если у кодемобиля не осталось зарядов для системы закиси азота
     * либо прошло менее {@code game.useNitroCooldownTicks} тиков с момента предыдущего ускорения.
     */
    public void setUseNitro(boolean useNitro) {
        this.useNitro = useNitro;
    }

    /**
     * @return Возвращает текущее значение указания разлить канистру с мазутом.
     */
    public boolean isSpillOil() {
        return spillOil;
    }

    /**
     * Устанавливает значение указания разлить канистру с мазутом.
     * <p/>
     * Указание может быть проигнорировано, если у кодемобиля не осталось канистр с мазутом
     * либо прошло менее {@code game.spillOilCooldownTicks} тиков с момента предыдущего использования данного действия.
     */
    public void setSpillOil(boolean spillOil) {
        this.spillOil = spillOil;
    }
}
