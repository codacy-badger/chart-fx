package de.gsi.chart.axes.spi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.gsi.chart.axes.AxisTransform;
import de.gsi.chart.axes.LogAxisType;
import de.gsi.chart.axes.TickUnitSupplier;
import de.gsi.chart.axes.spi.transforms.DefaultAxisTransform;
import de.gsi.chart.axes.spi.transforms.LogarithmicAxisTransform;
import de.gsi.chart.axes.spi.transforms.LogarithmicTimeAxisTransform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.chart.NumberAxis;

/**
 * A axis class that plots a range of numbers with major tick marks every
 * "tickUnit". You can use any Number type with this axis, Long, Double,
 * BigDecimal etc.
 * <p>
 * Compared to the {@link NumberAxis}, this one has a few additional features:
 * <ul>
 * <li>Re-calculates tick unit also when the {@link #autoRangingProperty()
 * auto-ranging} is off</li>
 * <li>Supports configuration of {@link #autoRangePaddingProperty() auto-range
 * padding}</li>
 * <li>Supports configuration of {@link #autoRangeRoundingProperty() auto-range
 * rounding}</li>
 * <li>Supports custom {@link #tickUnitSupplierProperty() tick unit
 * suppliers}</li>
 * </ul>
 * 
 * @author rstein
 */
public class DefaultNumericAxis extends AbstractAxis {
    public static final double DEFAULT_LOG_MIN_VALUE = 1e-6;
    private double offset;

    private static final int MAX_TICK_COUNT = 20;

    private static final int DEFAULT_RANGE_LENGTH = 2;
    private final Cache cache = new Cache();

    private final DefaultAxisTransform linearTransform = new DefaultAxisTransform(this);
    private final LogarithmicAxisTransform logTransform = new LogarithmicAxisTransform(this);
    private final LogarithmicTimeAxisTransform logTimeTransform = new LogarithmicTimeAxisTransform(this);
    private AxisTransform axisTransform = linearTransform;

    protected boolean isUpdating = true;

    /**
     * Creates an {@link #autoRangingProperty() auto-ranging} Axis.
     *
     * @param axisLabel
     *            the axis {@link #labelProperty() label}
     */
    public DefaultNumericAxis(final String axisLabel) {
        this(axisLabel, 0.0, 0.0, 5.0);
    }

    /**
     * Creates an {@link #autoRangingProperty() auto-ranging} Axis.
     *
     * @param axisLabel
     *            the axis {@link #labelProperty() label}
     * @param axisLabel
     *            the unit of the axis axis {@link #unitProperty() label}
     */
    public DefaultNumericAxis(final String axisLabel, final String unit) {
        this(axisLabel, 0.0, 0.0, 5.0);
        setUnit(unit);
    }

    /**
     * Creates an {@link #autoRangingProperty() auto-ranging} Axis.
     */
    public DefaultNumericAxis() {
        this("axis label", 0.0, 0.0, 5.0);
    }

    /**
     * Creates a {@link #autoRangingProperty() non-auto-ranging} Axis with the
     * given upper bound, lower bound and tick unit.
     *
     * @param lowerBound
     *            the {@link #lowerBoundProperty() lower bound} of the axis
     * @param upperBound
     *            the {@link #upperBoundProperty() upper bound} of the axis
     * @param tickUnit
     *            the tick unit, i.e. space between tick marks
     */
    public DefaultNumericAxis(final double lowerBound, final double upperBound, final double tickUnit) {
        this(null, lowerBound, upperBound, tickUnit);
    }

    /**
     * Create a {@link #autoRangingProperty() non-auto-ranging} Axis with the
     * given upper bound, lower bound and tick unit.
     *
     * @param axisLabel
     *            the axis {@link #labelProperty() label}
     * @param lowerBound
     *            the {@link #lowerBoundProperty() lower bound} of the axis
     * @param upperBound
     *            the {@link #upperBoundProperty() upper bound} of the axis
     * @param tickUnit
     *            the tick unit, i.e. space between tick marks
     */
    public DefaultNumericAxis(final String axisLabel, final double lowerBound, final double upperBound,
            final double tickUnit) {
        super(lowerBound, upperBound);
        this.setLabel(axisLabel);
        if (lowerBound >= upperBound || lowerBound == 0 && upperBound == 0) {
            setAutoRanging(true);
        }
        setTickUnit(tickUnit);
        setMinorTickCount(AbstractAxisParameter.DEFAULT_MINOR_TICK_COUNT);

        isUpdating = false;
    }

    /**
     * Gets the transformation (linear, logarithmic, etc) applied to the values
     * of this axis.
     *
     * @return the axis transformation
     */
    @Override
    public AxisTransform getAxisTransform() {
        return axisTransform;
    }

    private final BooleanProperty forceZeroInRange = new SimpleBooleanProperty(this, "forceZeroInRange", false) {
        @Override
        protected void invalidated() {
            if (isAutoRanging() || isAutoGrowRanging()) {
                invalidateRange();
                requestAxisLayout();
            }
        }
    };

    /**
     * When {@code true} zero is always included in the visible range. This only
     * has effect if {@link #autoRangingProperty() auto-ranging} is on.
     *
     * @return forceZeroInRange property
     */
    public BooleanProperty forceZeroInRangeProperty() {
        return forceZeroInRange;
    }

    /**
     * Returns the value of the {@link #forceZeroInRangeProperty()}.
     *
     * @return value of the forceZeroInRange property
     */
    public boolean isForceZeroInRange() {
        return forceZeroInRange.getValue();
    }

    /**
     * Sets the value of the {@link #forceZeroInRangeProperty()}.
     *
     * @param value
     *            if {@code true}, zero is always included in the visible range
     */
    public void setForceZeroInRange(final boolean value) {
        forceZeroInRange.setValue(value);
    }

    protected boolean isLogAxis = false; // internal use (for performance reason
    private final BooleanProperty logAxis = new SimpleBooleanProperty(this, "logAxis", isLogAxis) {
        @Override
        protected void invalidated() {
            isLogAxis = get();

            if (isLogAxis) {
                if (DefaultNumericAxis.this.isTimeAxis()) {
                    axisTransform = logTimeTransform;
                    setMinorTickCount(0);
                } else {
                    axisTransform = logTransform;
                    setMinorTickCount(AbstractAxisParameter.DEFAULT_MINOR_TICK_COUNT);
                }
                if (getLowerBound() <= 0) {
                    isUpdating = true;
                    setLowerBound(DefaultNumericAxis.DEFAULT_LOG_MIN_VALUE);
                    isUpdating = false;
                }

                invalidateRange();
                requestLayout();
            } else {
                axisTransform = linearTransform;
                if (DefaultNumericAxis.this.isTimeAxis()) {
                    setMinorTickCount(0);
                } else {
                    setMinorTickCount(AbstractAxisParameter.DEFAULT_MINOR_TICK_COUNT);
                }
            }

            if (isAutoRanging() || isAutoGrowRanging()) {
                invalidateRange();
            }
            requestAxisLayout();
        }
    };

    /**
     * When {@code true} axis is being a log-axis (default = false)
     *
     * @see getLogAxisType for more infomation
     * @return logAxis property
     */
    public BooleanProperty logAxisProperty() {
        return logAxis;
    }

    /**
     * Returns the value of the {@link #logAxisProperty()}.
     *
     * @return value of the logAxis property
     */
    @Override
    public boolean isLogAxis() {
        return isLogAxis;
    }

    /**
     * Sets the value of the {@link #logAxisProperty()}.
     *
     * @param value
     *            if {@code true}, log axis is drawn
     */
    public void setLogAxis(final boolean value) {
        isLogAxis = value;
        logAxis.set(value);
    }

    /**
     * @return the log axis Type @see LogAxisType
     */
    @Override
    public LogAxisType getLogAxisType() {
        if (isLogAxis) {
            return LogAxisType.LOG10_SCALE;
        }
        return LogAxisType.LINEAR_SCALE;
    }

    /**
     * Base of the logarithm used by the axis, must be grater than 1.
     * <p>
     * <b>Default value: 10</b>
     * </p>
     *
     * @return base of the logarithm
     */
    public DoubleProperty logarithmBaseProperty() {
        return logTransform.logarithmBaseProperty();
    }

    /**
     * Returns the value of the {@link #logarithmBaseProperty()}.
     *
     * @return base of the logarithm
     */
    public double getLogarithmBase() {
        return logarithmBaseProperty().get();
    }

    /**
     * Sets value of the {@link #logarithmBaseProperty()}.
     *
     * @param value
     *            base of the logarithm, value > 1
     */
    public void setLogarithmBase(final double value) {
        logarithmBaseProperty().set(value);
        invalidateRange();
        requestAxisLayout();
    }

    @Override
    protected void setRange(final AxisRange range, final boolean animate) {
        super.setRange(range, animate);
        setTickUnit(range.getTickUnit());
    }

    @Override
    protected List<Double> calculateMajorTickValues(final double axisLength, final AxisRange axisRange) {
        final List<Double> tickValues = new ArrayList<>();
        if (isLogAxis) {
            if (axisRange.getLowerBound() >= axisRange.getUpperBound()) {
                return Arrays.asList(axisRange.getLowerBound());
            }
            double exp = Math.ceil(axisTransform.forward(axisRange.getLowerBound()));
            for (double tickValue = axisTransform.backward(exp); tickValue <= axisRange
                    .getUpperBound(); tickValue = axisTransform.backward(++exp)) {
                tickValues.add(tickValue);
            }

            // add minor tick marks to major
            // tickValues.addAll(calculateMinorTickMarks());

            return tickValues;
        }

        if (axisRange.getLowerBound() == axisRange.getUpperBound() || axisRange.getTickUnit() <= 0) {
            return Arrays.asList(axisRange.getLowerBound());
        }

        final double firstTick = DefaultNumericAxis.computeFistMajorTick(axisRange.getLowerBound(),
                axisRange.getTickUnit());
        for (double major = firstTick; major <= axisRange.getUpperBound(); major += axisRange.getTickUnit()) {
            tickValues.add(major);
        }
        return tickValues;
    }

    private static double computeFistMajorTick(final double lowerBound, final double tickUnit) {
        return Math.ceil(lowerBound / tickUnit) * tickUnit;
    }

    @Override
    protected List<Double> calculateMinorTickValues() {
        if (isLogAxis) {
            if (getMinorTickCount() <= 0) {
                return Collections.emptyList();
            }
        } else if (getMinorTickCount() == 0 || getTickUnit() == 0) {
            return Collections.emptyList();
        }

        final List<Double> minorTickMarks = new ArrayList<>();
        final double lowerBound = getLowerBound();
        final double upperBound = getUpperBound();
        final double majorUnit = getTickUnit();

        if (isLogAxis) {
            double exp = Math.floor(axisTransform.forward(lowerBound));
            for (double majorTick = axisTransform.backward(exp); majorTick < upperBound; majorTick = axisTransform
                    .backward(++exp)) {
                final double nextMajorTick = axisTransform.backward(exp + 1);
                final double minorUnit = (nextMajorTick - majorTick) / getMinorTickCount();
                for (double minorTick = majorTick + minorUnit; minorTick < nextMajorTick; minorTick += minorUnit) {
                    if (minorTick >= lowerBound && minorTick <= upperBound) {
                        minorTickMarks.add(minorTick);
                    }
                }
            }
        } else {
            final double firstMajorTick = DefaultNumericAxis.computeFistMajorTick(lowerBound, majorUnit);
            final double minorUnit = majorUnit / getMinorTickCount();

            for (double majorTick = firstMajorTick - majorUnit; majorTick < upperBound; majorTick += majorUnit) {
                final double nextMajorTick = majorTick + majorUnit;
                for (double minorTick = majorTick + minorUnit; minorTick < nextMajorTick; minorTick += minorUnit) {
                    if (minorTick >= lowerBound && minorTick <= upperBound) {
                        minorTickMarks.add(minorTick);
                    }
                }
            }
        }
        return minorTickMarks;
    }

    @Override
    protected AxisRange autoRange(final double minValue, final double maxValue, final double length,
            final double labelSize) {
        double min = minValue > 0 && isForceZeroInRange() ? 0 : minValue;
        if (isLogAxis && minValue <= 0) {
            min = DefaultNumericAxis.DEFAULT_LOG_MIN_VALUE;
            isUpdating = true;
            // TODO: check w.r.t. inverted axis (lower <-> upper bound exchange)
            setLowerBound(DefaultNumericAxis.DEFAULT_LOG_MIN_VALUE);
            isUpdating = false;
        }
        final double max = maxValue < 0 && isForceZeroInRange() ? 0 : maxValue;
        final double padding = DefaultNumericAxis.getEffectiveRange(min, max) * getAutoRangePadding();
        final double paddingScale = 1.0 + getAutoRangePadding();
        final double paddedMin = isLogAxis ? minValue / paddingScale
                : DefaultNumericAxis.clampBoundToZero(min - padding, min);
        final double paddedMax = isLogAxis ? maxValue * paddingScale
                : DefaultNumericAxis.clampBoundToZero(max + padding, max);

        return computeRange(paddedMin, paddedMax, length, labelSize);
    }

    protected static double getEffectiveRange(final double min, final double max) {
        double effectiveRange = max - min;
        if (effectiveRange == 0) {
            effectiveRange = min == 0 ? DefaultNumericAxis.DEFAULT_RANGE_LENGTH : Math.abs(min);
        }
        return effectiveRange;
    }

    /**
     * If padding pushed the bound above or below zero - stick it to zero.
     */
    protected static double clampBoundToZero(final double paddedBound, final double bound) {
        if (paddedBound < 0 && bound >= 0 || paddedBound > 0 && bound <= 0) {
            return 0;
        }
        return paddedBound;
    }

    @Override
    protected AxisRange computeRange(final double min, final double max, final double axisLength,
            final double labelSize) {
        double minValue = min;
        double maxValue = max;
        if (isLogAxis) {
            if ((isAutoRanging() || isAutoGrowRanging()) && isAutoRangeRounding()) {
                minValue = axisTransform.getRoundedMinimumRange(minValue);
                maxValue = axisTransform.getRoundedMaximumRange(maxValue);
            }
            final double newScale = calculateNewScale(axisLength, minValue, maxValue);
            return new AxisRange(minValue, maxValue, axisLength, newScale, getTickUnit());
        }

        if (maxValue - minValue == 0) {
            final double padding = getAutoRangePadding() < 0 ? 0.0 : getAutoRangePadding();
            final double paddedRange = DefaultNumericAxis.getEffectiveRange(minValue, maxValue) * padding;
            minValue = minValue - paddedRange / 2;
            maxValue = maxValue + paddedRange / 2;
        }

        return computeRangeImpl(minValue, maxValue, axisLength, labelSize);
    }

    private AxisRange computeRangeImpl(final double min, final double max, final double axisLength,
            final double labelSize) {
        final int numOfFittingLabels = (int) Math.floor(axisLength / labelSize);
        final int numOfTickMarks = Math.max(Math.min(numOfFittingLabels, DefaultNumericAxis.MAX_TICK_COUNT), 2);

        double rawTickUnit = (max - min) / numOfTickMarks;
        if (rawTickUnit == 0 || Double.isNaN(rawTickUnit)) {
            rawTickUnit = 1e-3;// TODO: remove hack
        }

        // double tickUnitRounded = Double.MIN_VALUE; // TODO check if not
        // '-Double.MAX_VALUE'
        final double tickUnitRounded = computeTickUnit(rawTickUnit);
        final boolean round = (isAutoRanging() || isAutoGrowRanging()) && isAutoRangeRounding();
        final double minRounded = round ? axisTransform.getRoundedMinimumRange(min) : min;
        final double maxRounded = round ? axisTransform.getRoundedMaximumRange(max) : max;

        // int ticksCount;
        // double reqLength;
        //
        // do {
        // if (Double.isNaN(rawTickUnit)) {
        // throw new IllegalArgumentException("Can't calculate axis range: data
        // contains NaN value");
        // }
        // // Here we ignore the tickUnit property, so even if the tick unit
        // // was specified and the auto-range is off
        // // we don't use it. When narrowing the range (e.g. zoom-in) - this
        // // is usually ok, but if one wants
        // // explicitly change bounds while preserving the specified tickUnit,
        // // this won't work. Perhaps the usage of
        // // tickUnit should be independent of the auto-range so we should
        // // introduce autoTickUnit. The other option is
        // // to provide custom TickUnitSupplier that always returns the same
        // // tick unit.
        // prevTickUnitRounded = tickUnitRounded;
        // tickUnitRounded = computeTickUnit(rawTickUnit);
        // if (tickUnitRounded <= prevTickUnitRounded) {
        // break;
        // }
        //
        // double firstMajorTick;
        // if ((isAutoRanging() || isAutoGrowRanging()) &&
        // isAutoRangeRounding()) {
        // minRounded = Math.floor(min / tickUnitRounded) * tickUnitRounded;
        // maxRounded = Math.ceil(max / tickUnitRounded) * tickUnitRounded;
        // firstMajorTick = minRounded;
        // } else {
        // firstMajorTick = Math.ceil(min / tickUnitRounded) * tickUnitRounded;
        // }
        //
        // ticksCount = 0;
        // double maxReqTickGap = 0;
        // double halfOfLastTickSize = 0;
        // for (double major = firstMajorTick; major <= maxRounded; major +=
        // tickUnitRounded, ticksCount++) {
        // final double tickMarkSize = measureTickMarkLength(major);
        // if (major == firstMajorTick) {
        // halfOfLastTickSize = tickMarkSize / 2;
        // } else {
        // maxReqTickGap = Math.max(maxReqTickGap, halfOfLastTickSize +
        // TICK_MARK_GAP + tickMarkSize / 2);
        // }
        // }
        // reqLength = (ticksCount - 1) * maxReqTickGap;
        // rawTickUnit = tickUnitRounded * NEXT_TICK_UNIT_FACTOR;
        // } while (numOfTickMarks > 2 && (reqLength > axisLength || ticksCount
        // > MAX_TICK_COUNT));

        final double newScale = calculateNewScale(axisLength, minRounded, maxRounded);
        return new AxisRange(minRounded, maxRounded, axisLength, newScale, tickUnitRounded);
    }

    protected double computeTickUnit(final double rawTickUnit) {
        final TickUnitSupplier unitSupplier = getAxisLabelFormatter().getTickUnitSupplier();
        if (unitSupplier == null) {
            throw new IllegalStateException("class defaults not properly initialised");
        }
        final double majorUnit = unitSupplier.computeTickUnit(rawTickUnit);
        if (majorUnit <= 0) {
            throw new IllegalArgumentException("The " + unitSupplier.getClass().getName()
                    + " computed illegal unit value [" + majorUnit + "] for argument " + rawTickUnit);
        }
        return majorUnit;
    }

    /**
     * Computes the preferred tick unit based on the upper/lower bounds and the
     * length of the axis in screen coordinates.
     *
     * @param axisLength
     *            the length in screen coordinates
     * @return the tick unit
     */
    @Override
    public double computePreferredTickUnit(final double axisLength) {
        final double labelSize = getTickLabelFont().getSize() * 2;
        final int numOfFittingLabels = (int) Math.floor(axisLength / labelSize);
        final int numOfTickMarks = Math.max(Math.min(numOfFittingLabels, DefaultNumericAxis.MAX_TICK_COUNT), 2);
        double rawTickUnit = (getUpperBound() - getLowerBound()) / numOfTickMarks;
        if (rawTickUnit == 0 || Double.isNaN(rawTickUnit)) {
            rawTickUnit = 1e-3;// TODO: remove this hack (eventually) ;-)
        }
        return computeTickUnit(rawTickUnit);
    }

    /**
     * Get the display position along this axis for a given value. If the value
     * is not in the current range, the returned value will be an extrapolation
     * of the display position. -- cached double optimised version (shaves of
     * 50% on delays)
     *
     * @param value
     *            The data value to work out display position for
     * @return display position
     */
    @Override
    public double getDisplayPosition(final double value) {
        if (isInvertedAxis) {
            return offset - getDisplayPositionImpl(value);
        }
        return getDisplayPositionImpl(value);
    }

    private double getDisplayPositionImpl(final double value) {
        if (isLogAxis) {
            final double valueLogOffset = axisTransform.forward(value) - cache.lowerBoundLog;

            if (cache.isVerticalAxis) {
                return cache.axisHeight - valueLogOffset * cache.logScaleLengthInv;
            }
            return valueLogOffset * cache.logScaleLengthInv;
        }

        // default case: linear axis computation (dependent variables are being
        // cached for performance reasons)
        // return cache.localOffset + (value - cache.localCurrentLowerBound) *
        // cache.localScale;
        return cache.localOffset2 + value * cache.localScale;
    }

    /**
     * Get the data value for the given display position on this axis. If the
     * axis is a CategoryAxis this will be the nearest value. -- cached double
     * optimised version (shaves of 50% on delays)
     *
     * @param displayPosition
     *            A pixel position on this axis
     * @return the nearest data value to the given pixel position or null if not
     *         on axis;
     */
    @Override
    public double getValueForDisplay(final double displayPosition) {
        if (isInvertedAxis) {
            // return getReverseValueForDisplayImpl(displayPosition);
            return getValueForDisplayImpl(offset - displayPosition);
        }
        return getValueForDisplayImpl(displayPosition);
    }

    private double getValueForDisplayImpl(final double displayPosition) {
        if (isLogAxis) {
            if (cache.isVerticalAxis) {
                final double height = cache.axisHeight;
                return axisTransform
                        .backward(cache.lowerBoundLog + (height - displayPosition) / height * cache.logScaleLength);
            }
            return axisTransform
                    .backward(cache.lowerBoundLog + displayPosition / cache.axisWidth * cache.logScaleLength);
        }

        return cache.localCurrentLowerBound + (displayPosition - cache.localOffset) / cache.localScale;
    }

    /**
     * Get the display position of the zero line along this axis.
     *
     * @return display position or Double.NaN if zero is not in current range;
     */
    @Override
    public double getZeroPosition() {
        if (isLogAxis) {
            return getDisplayPosition(cache.localCurrentLowerBound);
        }

        if (0 < cache.localCurrentLowerBound || 0 > cache.localCurrentUpperBound) {
            return Double.NaN;
        }

        return getDisplayPosition(0);
    }

    /**
     * Checks if the given value is plottable on this axis
     *
     * @param value
     *            The value to check if its on axis
     * @return true if the given value is plottable on this axis
     */
    @Override
    public boolean isValueOnAxis(final double value) {
        // if (isInvertedAxis) {
        // return value <= getLowerBound() && value >= getUpperBound();
        // } else {
        // return value >= getLowerBound() && value <= getUpperBound();
        // }
        return value >= getLowerBound() && value <= getUpperBound();
    }

    @Override
    public void requestAxisLayout() {
        if (isUpdating) {
            return;
        }

        super.requestAxisLayout();
    }

    @Override
    protected void updateCachedVariables() {
        cache.updateCachedAxisVariables();
    }

    protected class Cache {
        protected double localScale;
        protected double localCurrentLowerBound;
        protected double localCurrentUpperBound;
        protected double localOffset;
        protected double localOffset2;
        protected double upperBoundLog;
        protected double lowerBoundLog;
        protected double logScaleLength;
        protected double logScaleLengthInv;
        protected boolean isVerticalAxis;
        protected double axisWidth;
        protected double axisHeight;

        private void updateCachedAxisVariables() {
            axisWidth = getWidth();
            axisHeight = getHeight();
            localCurrentLowerBound = currentLowerBound.get();
            localCurrentUpperBound = DefaultNumericAxis.super.getUpperBound();

            upperBoundLog = axisTransform.forward(getUpperBound());
            lowerBoundLog = axisTransform.forward(getLowerBound());
            logScaleLength = upperBoundLog - lowerBoundLog;

            logScaleLengthInv = 1.0 / logScaleLength;

            localScale = scaleProperty().get();
            final double zero = DefaultNumericAxis.super.getDisplayPosition(0);
            localOffset = zero + localCurrentLowerBound * localScale;
            localOffset2 = localOffset - cache.localCurrentLowerBound * cache.localScale;

            if (getSide() != null) {
                isVerticalAxis = getSide().isVertical();
            }

            if (isVerticalAxis) {
                logScaleLengthInv = axisHeight / logScaleLength;
            } else {
                logScaleLengthInv = axisWidth / logScaleLength;
            }

            offset = isVerticalAxis ? getHeight() : getWidth();
        }
    }
}