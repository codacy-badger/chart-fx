package de.gsi.chart.axes.spi;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReentrantLock;

import de.gsi.chart.axes.Axis;
import de.gsi.chart.axes.AxisLabelFormatter;
import de.gsi.chart.axes.AxisLabelOverlapPolicy;
import de.gsi.chart.axes.spi.format.DefaultFormatter;
import de.gsi.chart.axes.spi.format.DefaultLogFormatter;
import de.gsi.chart.axes.spi.format.DefaultTimeFormatter;
import de.gsi.chart.ui.ResizableCanvas;
import de.gsi.chart.ui.geometry.Side;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Dimension2D;
import javafx.geometry.VPos;
import javafx.scene.CacheHint;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Path;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import javafx.util.StringConverter;

/**
 * @author rstein
 */
public abstract class AbstractAxis extends AbstractAxisParameter implements Axis {
    protected static final int RANGE_ANIMATION_DURATION_MS = 700;
    protected WeakHashMap<Number, Dimension2D> tickMarkSizeCache = new WeakHashMap<>();
    protected final Timeline animator = new Timeline();
    private final Canvas canvas = new ResizableCanvas();
    protected boolean labelOverlap = false;
    protected double cachedOffset; // for caching
    protected final List<InvalidationListener> listeners = new LinkedList<>();
    protected final ReentrantLock lock = new ReentrantLock();
    protected boolean autoNotification = true;
    protected double maxLabelHeight;
    protected double maxLabelWidth;

    /**
     * @param coordinate
     *            double coordinate to snapped to actual pixel index
     * @return coordinate that is snapped to pixel (for a 'crisper' display)
     */
    private static double snap(final double coordinate) {
        return Math.round(coordinate) + 0.5;
    }

    /**
     * The current value for the lowerBound of this axis, ie min value. This may
     * be the same as lowerBound or different. It is used by NumberAxis to
     * animate the lowerBound from the old value to the new value.
     */
    protected final DoubleProperty currentLowerBound = new SimpleDoubleProperty(this, "currentLowerBound");

    // -------------- PUBLIC PROPERTIES
    // --------------------------------------------------------------------------------

    private final ObjectProperty<AxisLabelFormatter> axisFormatter = new SimpleObjectProperty<AxisLabelFormatter>(this,
            "axisLabelFormatter", null) {
        /**
         * default fall-back formatter in case no {@code axisFormatter} is
         * specified (ie. 'null')
         */
        private final AxisLabelFormatter defaultFormatter = new DefaultFormatter(AbstractAxis.this);
        private final AxisLabelFormatter defaultLogFormatter = new DefaultLogFormatter(AbstractAxis.this);
        private final AxisLabelFormatter defaultTimeFormatter = new DefaultTimeFormatter(AbstractAxis.this);
        private final AxisLabelFormatter defaultLogTimeFormatter = new DefaultTimeFormatter(AbstractAxis.this);

        @Override
        public AxisLabelFormatter get() {
            final AxisLabelFormatter superImpl = super.get();
            if (superImpl != null) {
                return superImpl;
            }

            if (isTimeAxis()) {
                if (isLogAxis()) {
                    return defaultLogTimeFormatter;
                }
                return defaultTimeFormatter;
            }

            // non-time format
            if (isLogAxis()) {
                return defaultLogFormatter;
            }
            return defaultFormatter;
        }

        @Override
        protected void invalidated() {
            requestAxisLayout();
        }
    };

    public AxisLabelFormatter getAxisLabelFormatter() {
        return axisFormatter.get();
    }

    public void setAxisLabelFormatter(final AxisLabelFormatter value) {
        axisFormatter.set(value);
    }

    public ObjectProperty<AxisLabelFormatter> axisLabelFormatterProperty() {
        return axisFormatter;
    }

    public AbstractAxis() {
        super();
        setMouseTransparent(false);
        setPickOnBounds(true);
        canvas.setMouseTransparent(false);
        canvas.toFront();
        if (!canvas.isCache()) {
            canvas.setCache(true);
            canvas.setCacheHint(CacheHint.QUALITY);
        }
        getChildren().add(canvas);

        final ChangeListener<? super Number> axisSizeChangeListener = (c, o, n) -> {
            if (o == n) {
                return;
            }
            // N.B. add padding along axis to allow oversized labels
            final double padding = getAxisPadding();
            if (this.getSide().isHorizontal()) {
                canvas.resize(getWidth() + 2 * padding, getHeight());
                canvas.setLayoutX(-padding);
            } else {
                canvas.resize(getWidth() + 2 * padding, getHeight() + 2 * padding);
                canvas.setLayoutY(-padding);
            }
            // canvas.resize(getWidth() + 2 * padding, getHeight() + 2 *
            // padding);
            // canvas.setLayoutX(-padding);
            // canvas.setLayoutY(-padding);
        };

        this.axisPaddingProperty().addListener((ch, o, n) -> {
            if (o == n) {
                return;
            }
            final double padding = getAxisPadding();
            if (this.getSide().isHorizontal()) {
                canvas.resize(getWidth() + 2 * padding, getHeight());
                canvas.setLayoutX(-padding);
            } else {
                canvas.resize(getWidth() + 2 * padding, getHeight() + 2 * padding);
                canvas.setLayoutY(-padding);
            }
        });

        widthProperty().addListener(axisSizeChangeListener);
        heightProperty().addListener(axisSizeChangeListener);

        VBox.setVgrow(this, Priority.ALWAYS);
        HBox.setHgrow(this, Priority.ALWAYS);
    }

    public AbstractAxis(final double lowerBound, final double upperBound) {
        this();
        setLowerBound(lowerBound);
        setUpperBound(upperBound);
        setAutoRanging(false);
    }

    // -------------- PROTECTED METHODS
    // --------------------------------------------------------------------------------

    /**
     * Computes range of this axis, similarly to
     * {@link #autoRange(double, double, double, double)}. The major difference
     * is that this method is called when {@link #autoRangingProperty()
     * auto-range} is off.
     *
     * @param minValue
     *            The min data value that needs to be plotted on this axis
     * @param maxValue
     *            The max data value that needs to be plotted on this axis
     * @param axisLength
     *            The length of the axis in display coordinates
     * @param labelSize
     *            The approximate average size a label takes along the axis
     * @return The calculated range
     * @see #autoRange(double, double, double, double)
     */
    protected abstract AxisRange computeRange(double minValue, double maxValue, double axisLength, double labelSize);

    protected AxisRange autoRange(final double minValue, final double maxValue, final double length,
            final double labelSize) {
        return computeRange(minValue, maxValue, length, 2 * labelSize);
    }

    /**
     * This calculates the upper and lower bound based on the data provided to
     * invalidateRange() method. This must not effect the state of the axis,
     * changing any properties of the axis. Any results of the auto-ranging
     * should be returned in the range object. This will we passed to setRange()
     * if it has been decided to adopt this range for this axis.
     *
     * @param length
     *            The length of the axis in screen coordinates
     * @return Range information, this is implementation dependent
     */
    protected AxisRange autoRange(final double length) {
        // guess a sensible starting size for label size, that is approx 2 lines
        // vertically or 2 charts horizontally
        if (isAutoRanging() || isAutoGrowRanging()) {
            // guess a sensible starting size for label size, that is approx 2
            // lines vertically or 2 charts horizontally
            final double labelSize = getTickLabelFont().getSize() * 2;
            final AxisRange retVal = autoRange(autoRange.getMin(), autoRange.getMax(), length, labelSize);
            return retVal;
        } else {
            return getAxisRange();
        }
    }

    /**
     * Calculate a new scale for this axis. This should not effect any
     * state(properties) of this axis.
     *
     * @param length
     *            The display length of the axis
     * @param lowerBound
     *            The lower bound value
     * @param upperBound
     *            The upper bound value
     * @return new scale to fit the range from lower bound to upper bound in the
     *         given display length
     */
    protected double calculateNewScale(final double length, final double lowerBound, final double upperBound) {
        double newScale = 1;
        final Side side = getSide();
        final double diff = upperBound - lowerBound;
        if (side.isVertical()) {
            cachedOffset = length;
            newScale = diff == 0 ? -length : -(length / diff);
        } else { // HORIZONTAL
            cachedOffset = 0;
            newScale = upperBound - lowerBound == 0 ? length : length / diff;
        }
        return newScale != 0 ? newScale : -1.0;
    }

    /**
     * Calculate a list of the data values for every minor tick mark
     *
     * @return List of data values where to draw minor tick marks
     */

    protected abstract List<Double> calculateMinorTickValues();

    // cache for major tick marks
    protected WeakHashMap<String, TickMark> tickMarkStringCache = new WeakHashMap<>();
    // cache for minor tick marks (N.B. usually w/o string label)
    protected WeakHashMap<Double, TickMark> tickMarkDoubleCache = new WeakHashMap<>();

    public TickMark getNewTickMark(final Double tickValue, final Double tickPosition, final String tickMarkLabel) {
        TickMark tick;
        if (tickMarkLabel.isEmpty()) {
            // usually a minor tick mark w/o label
            tick = tickMarkDoubleCache.computeIfAbsent(tickValue, k -> new TickMark(tickValue, tickPosition, ""));
        } else {
            // usually a major tick mark with label
            tick = tickMarkStringCache.computeIfAbsent(tickMarkLabel, k -> new TickMark(tickValue, tickPosition, k));
            tick.setValue(tickValue);
        }
        tick.setPosition(tickPosition);

        tick.setFont(getTickLabelFont());
        tick.setFill(getTickLabelFill());
        tick.setRotate(getTickLabelRotation());
        tick.setVisible(isTickLabelsVisible());
        tick.applyCss();
        return tick;
    }

    protected List<TickMark> computeTickMarks(final AxisRange range, final boolean majorTickMark) {
        final List<TickMark> newTickMarkList = new LinkedList<>();
        final Side side = getSide();
        if (side == null) {
            return newTickMarkList;
        }
        final double width = getWidth();
        final double height = getHeight();
        final double axisLength = side.isVertical() ? height : width; // [pixel]

        final List<Double> newTickValues = majorTickMark ? calculateMajorTickValues(axisLength, range)
                : calculateMinorTickValues();

        if (majorTickMark) {
            getAxisLabelFormatter().updateFormatter(newTickValues, getUnitScaling());

            // TODO. if first number is very large and range very small ->
            // switch to:
            // first label: full format
            // every other label as '... \n+ X.Y'
        }

        if (majorTickMark) {
            maxLabelHeight = 0;
            maxLabelWidth = 0;
        }

        newTickValues.forEach(newValue -> {
            final double tickPosition = getDisplayPosition(newValue.doubleValue());
            final TickMark tick = getNewTickMark(newValue, tickPosition,
                    majorTickMark ? getTickMarkLabel(newValue.doubleValue()) : "");

            if (majorTickMark && shouldAnimate()) {
                tick.setOpacity(0);
            }
            maxLabelHeight = Math.max(maxLabelHeight, tick.getHeight());
            maxLabelWidth = Math.max(maxLabelWidth, tick.getWidth());

            newTickMarkList.add(tick);
            if (majorTickMark && shouldAnimate()) {
                final FadeTransition ft = new FadeTransition(Duration.millis(750), tick);
                tick.opacityProperty().addListener((ch, o, n) -> {
                    clearAxisCanvas(canvas.getGraphicsContext2D(), width, height);
                    drawAxis(canvas.getGraphicsContext2D(), width, height);
                });
                ft.setFromValue(0);
                ft.setToValue(1);
                ft.play();
            }
        });

        return newTickMarkList;
    }

    public void recomputeTickMarks() {
        recomputeTickMarks(getRange());
    }

    protected void recomputeTickMarks(final AxisRange range) {
        final Side side = getSide();
        if (side == null) {
            return;
        }

        // recalculate major tick marks
        majorTickMarks.setAll(computeTickMarks(range, true));

        // recalculate minor tick marks
        minorTickMarkValues.setAll(computeTickMarks(range, false));
        tickMarksUpdated();
    }

    @Override
    public void drawAxis(final GraphicsContext gc, final double axisWidth, final double axisHeight) {
        if (gc == null || getSide() == null) {
            return;
        }

        drawAxisPre();

        // update CSS data
        majorTickStyle.applyCss();
        minorTickStyle.applyCss();
        axisLabel.applyCss();
        final double axisLength = getSide().isHorizontal() ? axisWidth : axisHeight;

        // draw dominant axis line
        drawAxisLine(gc, axisLength, axisWidth, axisHeight);

        if (!isTickMarkVisible()) {
            // draw axis title w/o major TickMark
            drawAxisLabel(gc, axisLength, axisWidth, axisHeight, getAxisLabel(), null, getTickLength());
            drawAxisPost();
            return;
        }

        final ObservableList<TickMark> majorTicks = getTickMarks();
        final ObservableList<TickMark> minorTicks = getMinorTickMarks();

        // neededLength assumes tick-mark width of one, needed to suppress minor
        // ticks if tick-mark pixel are overlapping
        final double neededLength = (getTickMarks().size() + minorTicks.size()) * 2;
        // Don't draw minor tick marks if there isn't enough space for them!
        if (isMinorTickVisible() && axisLength > neededLength) {
            drawTickMarks(gc, axisLength, axisWidth, axisHeight, minorTicks, getMinorTickLength(), getMinorTickStyle());
            drawTickLabels(gc, axisWidth, axisHeight, minorTicks, getMinorTickLength());
        }

        // draw major tick-mark over minor tick-marks so that the visible
        // (long) line along the axis with the style of the major-tick is
        // visible
        drawTickMarks(gc, axisLength, axisWidth, axisHeight, majorTicks, getTickLength(), getMajorTickStyle());
        drawTickLabels(gc, axisWidth, axisHeight, majorTicks, getTickLength());

        // draw axis title
        drawAxisLabel(gc, axisLength, axisWidth, axisHeight, getAxisLabel(), majorTicks, getTickLength());
        drawAxisPost();
    }

    /**
     * function to be executed prior to drawing axis can be used to execute
     * user-specific code (e.g. modifying tick-marks) prior to drawing
     */
    protected void drawAxisPre() {
        // to be overwritten in derived classes
    }

    /**
     * function to be executed after the axis has been drawn can be used to
     * execute user-specific code (e.g. update of other classes/properties)
     */
    protected void drawAxisPost() {
        // to be overwritten in derived classes
    }

    /**
     * to be overwritten by derived class that want to cache variables for
     * efficiency reasons
     */
    protected void updateCachedVariables() {
        // called once new axis parameters have been established
    }

    @Override
    public void forceRedraw() {
        layoutChildren();
    }

    /**
     * Invoked during the layout pass to layout this axis and all its content.
     */
    @Override
    protected void layoutChildren() {
        final Side side = getSide();
        if (side == null) {
            return;
        }
        final double width = getWidth();
        final double height = getHeight();
        final double axisLength = side.isVertical() ? height : width; // [pixel]
        // if we are not auto ranging we need to calculate the new scale
        if (!isAutoRanging()) {
            // calculate new scale
            setScale(calculateNewScale(axisLength, getLowerBound(), getUpperBound()));
            // update current lower bound
            currentLowerBound.set(getLowerBound());
        }
        boolean recomputedTicks = false;

        // we have done all auto calcs, let Axis position major tickmarks

        final boolean isFirstPass = oldAxisLength == -1;
        // auto range if it is not valid

        final boolean rangeInvalid = !isRangeValid();
        final boolean lengthDiffers = oldAxisLength != axisLength;
        if (lengthDiffers || rangeInvalid || super.isNeedsLayout()) {
            // get range
            AxisRange newAxisRange;
            if (isAutoRanging()) {
                // auto range
                newAxisRange = autoRange(axisLength);
                // set current range to new range
                setRange(newAxisRange, getAnimated() && !isFirstPass && impl_isTreeVisible() && rangeInvalid);
            } else {
                newAxisRange = getAxisRange();
            }

            recomputeTickMarks(newAxisRange);
            // mark all done
            oldAxisLength = axisLength;
            rangeValid = true;

            // update cache in derived classes
            updateCachedVariables();

            recomputedTicks = true;
        }

        if (lengthDiffers || rangeInvalid || measureInvalid || tickLabelsVisibleInvalid) {
            measureInvalid = false;
            tickLabelsVisibleInvalid = false;

            int numLabelsToSkip = 0;
            double totalLabelsSize = 0;
            double maxLabelSize = 0;
            for (final TickMark m : majorTickMarks) {
                m.setPosition(getDisplayPosition(m.getValue()));
                if (m.isVisible()) {
                    final double tickSize = side.isHorizontal() ? m.getWidth() : m.getHeight();
                    totalLabelsSize += tickSize;
                    maxLabelSize = Math.round(Math.max(maxLabelSize, tickSize));
                }
            }

            labelOverlap = false;

            if (maxLabelSize > 0 && axisLength < totalLabelsSize) {
                numLabelsToSkip = (int) (majorTickMarks.size() * maxLabelSize / axisLength) + 1;
                labelOverlap = true;
            }

            final boolean isShiftOverlapPolicy = getOverlapPolicy() == AxisLabelOverlapPolicy.SHIFT_ALT
                    || getOverlapPolicy() == AxisLabelOverlapPolicy.FORCED_SHIFT_ALT;
            if (numLabelsToSkip > 0 && !isShiftOverlapPolicy) {
                int tickIndex = 0;
                for (final TickMark m : majorTickMarks) {
                    if (m.isVisible()) {
                        m.setVisible(tickIndex++ % numLabelsToSkip == 0);
                    }
                }
            }

            if (majorTickMarks.size() > 2) {
                TickMark m1 = majorTickMarks.get(0);
                TickMark m2 = majorTickMarks.get(1);
                if (isTickLabelsOverlap(side, m1, m2, getTickLabelGap())) {
                    labelOverlap = true;
                }
                m1 = majorTickMarks.get(majorTickMarks.size() - 2);
                m2 = majorTickMarks.get(majorTickMarks.size() - 1);
                if (isTickLabelsOverlap(side, m1, m2, getTickLabelGap())) {
                    labelOverlap = true;
                }
            }
        }

        if (recomputedTicks) {
            final GraphicsContext gc = canvas.getGraphicsContext2D();
            // draw minor / major tick marks on canvas
            clearAxisCanvas(gc, canvas.getWidth(), canvas.getHeight());
            final double axisWidth = getWidth();
            final double axisHeight = getHeight();
            drawAxis(gc, axisWidth, axisHeight);

            fireInvalidated();
        }
    }

    /**
     * Called when data has changed and the range may not be valid any more.
     * This is only called by the chart if isAutoRanging() returns true. If we
     * are auto ranging it will cause layout to be requested and auto ranging to
     * happen on next layout pass.
     *
     * @param data
     *            The current set of all data that needs to be plotted on this
     *            axis
     */
    @Override
    public void invalidateRange(final List<Number> data) {
        double dataMaxValue;
        double dataMinValue;
        if (data.isEmpty()) {
            dataMaxValue = getUpperBound();
            dataMinValue = getLowerBound();
            autoRange.set(getLowerBound(), getUpperBound());
        } else {
            dataMinValue = Double.MAX_VALUE;
            // We need to init to the lowest negative double (which is NOT
            // Double.MIN_VALUE)
            // in order to find the maximum (positive or negative)
            dataMaxValue = -Double.MAX_VALUE;
            autoRange.empty();
        }

        for (final Number dataValue : data) {
            dataMinValue = Math.min(dataMinValue, dataValue.doubleValue());
            dataMaxValue = Math.max(dataMaxValue, dataValue.doubleValue());
            autoRange.add(dataValue.doubleValue());
        }

        boolean change = false;
        if (getLowerBound() != dataMinValue) {
            setLowerBound(dataMinValue);
            change = true;
        }
        if (getUpperBound() != dataMaxValue) {
            setUpperBound(dataMaxValue);
            change = true;
        }

        if (change) {
            data.clear();
            autoRange.setAxisLength(getLength() == 0 ? 1 : getLength(), getSide());
        }
        invalidateRange();
        requestAxisLayout();
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
        return Double.isFinite(value) && value >= getLowerBound() && value <= getUpperBound();
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public boolean isLabelOverlapping() {
        // needed for diagnostics purposes
        return labelOverlap;
    }

    public GraphicsContext getGraphicsContext() {
        return canvas.getGraphicsContext2D();
    }

    /**
     * See if the current range is valid, if it is not then any range dependent
     * calculations need to redone on the next layout pass
     *
     * @return true if current range calculations are valid
     */
    protected boolean isRangeValid() {
        return rangeValid;
    }

    /**
     * Mark the current range invalid, this will cause anything that depends on
     * the range to be recalculated on the next layout.
     */
    @Override
    protected void invalidateRange() {
        rangeValid = false;
    }

    /**
     * This is used to check if any given animation should run. It returns true
     * if animation is enabled and the node is visible and in a scene.
     *
     * @return true if animations should happen
     */
    protected boolean shouldAnimate() {
        return getAnimated() && impl_isTreeVisible() && getScene() != null;
    }

    /**
     * We suppress requestLayout() calls here by doing nothing as we don't want
     * changes to our children to cause layout. If you really need to request
     * layout then call requestAxisLayout(). TODO: re-enable
     */
    // @Override
    // public void requestLayout() {
    // }

    /**
     * Request that the axis is laid out in the next layout pass. This replaces
     * requestLayout() as it has been overridden to do nothing so that changes
     * to children's bounds etc do not cause a layout. This was done as a
     * optimisation as the Axis knows the exact minimal set of changes that
     * really need layout to be updated. So we only want to request layout then,
     * not on any child change.
     */
    @Override
    public void requestAxisLayout() {
        // axisLabel.applyCss();
        super.requestLayout();
    }

    /**
     * Calculate a list of all the data values for each tick mark in range
     *
     * @param length
     *            The length of the axis in display units
     * @param range
     *            A range object returned from autoRange()
     * @return A list of tick marks that fit along the axis if it was the given
     *         length
     */
    protected abstract List<Double> calculateMajorTickValues(double length, AxisRange range);

    /**
     * Computes the preferred height of this axis for the given width. If axis
     * orientation is horizontal, it takes into account the tick mark length,
     * tick label gap and label height.
     *
     * @return the computed preferred width for this axis
     */
    @Override
    protected double computePrefHeight(final double width) {
        final Side side = getSide();
        if (side == null || side == Side.CENTER_HOR || side.isVertical()) {
            // default axis size for uninitalised axis
            return 150;
        }

        if (getTickMarks().isEmpty()) {
            final AxisRange range = autoRange(width);
            computeTickMarks(range, true);
            computeTickMarks(range, false);
        }

        // we need to first auto range as this may/will effect tick marks
        // final AxisRange range = autoRange(width);
        // calculate max tick label height
        // calculate the new tick mark label height
        final double maxLabelHeightLocal = isTickLabelsVisible() ? maxLabelHeight : 0.0;
        // if (isTickLabelsVisible()) {
        // // final List<TickMark> tempMajorTickMarks = computeTickMarks(range,
        // // true);
        // final List<TickMark> tempMajorTickMarks = this.getTickMarks();
        // maxLabelHeight = getMaxTickLabelHeight(tempMajorTickMarks) +
        // getTickLabelGap();
        // }

        // calculate tick mark length
        final double tickMarkLength = isTickMarkVisible() && getTickLength() > 0 ? getTickLength() : 0;
        // calculate label height
        final double labelHeight = axisLabel.getText() == null || axisLabel.getText().isEmpty() ? 0
                : axisLabel.prefHeight(-1) + 2*getAxisLabelGap();
        final double shiftedLabels = getOverlapPolicy() == AxisLabelOverlapPolicy.SHIFT_ALT && isLabelOverlapping()
                || getOverlapPolicy() == AxisLabelOverlapPolicy.FORCED_SHIFT_ALT ? labelHeight : 0.0;
        return tickMarkLength + maxLabelHeightLocal + labelHeight + shiftedLabels;
    }

    /**
     * Computes the preferred width of this axis for the given height. If axis
     * orientation is vertical, it takes into account the tick mark length, tick
     * label gap and label height.
     *
     * @return the computed preferred width for this axis
     */
    @Override
    protected double computePrefWidth(final double height) {
        final Side side = getSide();
        if (side == null || side == Side.CENTER_VER || side.isHorizontal()) {
            // default axis size for uninitalised axis
            return 150;
        }

        if (getTickMarks().isEmpty()) {
            final AxisRange range = autoRange(height);
            computeTickMarks(range, true);
            computeTickMarks(range, false);
        }

        // we need to first auto range as this may/will effect tick marks
        // final AxisRange range = autoRange(height);
        // calculate max tick label width
        final double maxLabelWidthLocal = isTickLabelsVisible() ? maxLabelWidth : 0.0;
        // calculate the new tick mark label width
        // if (isTickLabelsVisible()) {
        // // final List<TickMark> tempMajorTickMarks = computeTickMarks(range,
        // // true);
        // final List<TickMark> tempMajorTickMarks = this.getTickMarks();
        // maxLabelWidth = getMaxTickLabelWidth(tempMajorTickMarks) +
        // getTickLabelGap();
        // }
        // calculate tick mark length
        final double tickMarkLength = isTickMarkVisible() && getTickLength() > 0 ? getTickLength() : 0;
        // calculate label height
        final double labelHeight = axisLabel.getText() == null || axisLabel.getText().isEmpty() ? 0
                : axisLabel.prefHeight(-1) + 2*getAxisLabelGap();

        final double shiftedLabels = getOverlapPolicy() == AxisLabelOverlapPolicy.SHIFT_ALT && isLabelOverlapping()
                || getOverlapPolicy() == AxisLabelOverlapPolicy.FORCED_SHIFT_ALT ? labelHeight : 0.0;
        return maxLabelWidthLocal + tickMarkLength + labelHeight + shiftedLabels;
    }

    /**
     * Called during layout if the tickmarks have been updated, allowing
     * subclasses to do anything they need to in reaction.
     */
    protected void tickMarksUpdated() {
    }

    /**
     * @return axsis range that is supposed to be shown
     */
    protected AxisRange getAxisRange() {
        // TODO: switch between auto-range and user-defined range here
        return new AxisRange(getLowerBound(), getUpperBound(), getLength(), getScale(), getTickUnit());
    }

    protected void setRange(final AxisRange rangeObj, final boolean animate) {
        if (!(rangeObj instanceof AxisRange)) {

            return;
        }
        final AxisRange range = rangeObj;
        final double oldLowerBound = getLowerBound();
        if (getLowerBound() != range.getLowerBound()) {
            setLowerBound(range.getLowerBound());
        }
        if (getUpperBound() != range.getUpperBound()) {
            setUpperBound(range.getUpperBound());
        }

        if (animate) {
            animator.stop();
            animator.getKeyFrames()
                    .setAll(new KeyFrame(Duration.ZERO, new KeyValue(currentLowerBound, oldLowerBound),
                            new KeyValue(scaleBinding, getScale())),
                            new KeyFrame(Duration.millis(AbstractAxis.RANGE_ANIMATION_DURATION_MS),
                                    new KeyValue(currentLowerBound, range.getLowerBound()),
                                    new KeyValue(scaleBinding, range.getScale())));
            animator.play();
        } else {
            currentLowerBound.set(range.getLowerBound());
            setScale(range.getScale());
        }
    }

    protected void clearAxisCanvas(final GraphicsContext gc, final double width, final double height) {
        gc.clearRect(0, 0, width, height);
    }

    protected void drawAxisLine(final GraphicsContext gc, final double axisLength, final double axisWidth,
            final double axisHeight) {
        // N.B. axis canvas is (by-design) larger by 'padding' w.r.t.
        // required/requested axis length (needed for nicer label placements on
        // border.
        final double paddingX = getSide().isHorizontal() ? getAxisPadding() : 0.0;
        final double paddingY = getSide().isVertical() ? getAxisPadding() : 0.0;
        // for relative positioning of axes drawn on top of the main canvas
        final double axisCentre = getCenterAxisPosition();

        // save css-styled line parameters
        final Path tickStyle = getMajorTickStyle();
        gc.save();
        gc.setStroke(tickStyle.getStroke());
        gc.setFill(tickStyle.getFill());
        gc.setLineWidth(tickStyle.getStrokeWidth());

        // N.B. important: translate by padding ie. canvas is +padding larger on
        // all size compared to region
        gc.translate(paddingX, paddingY);
        switch (getSide()) {
        case LEFT:
            // axis line on right side of canvas
            gc.strokeLine(snap(axisWidth), snap(0), snap(axisWidth), snap(axisLength));
            break;
        case RIGHT:
            // axis line on left side of canvas
            gc.strokeLine(snap(0), snap(0), snap(0), snap(axisLength));
            break;
        case TOP:
            // line on bottom side of canvas (N.B. (0,0) is top left corner)
            gc.strokeLine(snap(0), snap(axisHeight), snap(axisLength), snap(axisHeight));
            break;
        case BOTTOM:
            // line on top side of canvas (N.B. (0,0) is top left corner)
            gc.strokeLine(snap(0), snap(0), snap(axisLength), snap(0));
            break;
        case CENTER_HOR:
            // axis line at the centre of the canvas
            gc.strokeLine(snap(0), axisCentre * axisHeight, snap(axisLength), snap(axisCentre * axisHeight));

            break;
        case CENTER_VER:
            // axis line at the centre of the canvas
            gc.strokeLine(snap(axisCentre * axisWidth), snap(0), snap(axisCentre * axisWidth), snap(axisLength));

            break;
        default:
            break;
        }
        gc.restore();
    }

    protected void drawTickMarks(final GraphicsContext gc, final double axisLength, final double axisWidth,
            final double axisHeight, final ObservableList<TickMark> tickMarks, final double tickLength,
            final Path tickStyle) {
        if (tickLength <= 0) {
            return;
        }
        final double paddingX = getSide().isHorizontal() ? getAxisPadding() : 0.0;
        final double paddingY = getSide().isVertical() ? getAxisPadding() : 0.0;
        // for relative positioning of axes drawn on top of the main canvas
        final double axisCentre = getCenterAxisPosition();

        gc.save();
        // save css-styled line parameters
        gc.setStroke(tickStyle.getStroke());
        gc.setFill(tickStyle.getFill());
        gc.setLineWidth(tickStyle.getStrokeWidth());
        // N.B. important: translate by padding ie. canvas is +padding larger on
        // all size compared to region
        gc.translate(paddingX, paddingY);

        // N.B. streams, filter, and forEach statements have been evaluated and
        // appear to give no (or negative) performance for little/arguable
        // readability improvement (CG complexity from 40 -> 28, but mere
        // numerical number and not actual readability), thus sticking to 'for'
        // loops
        switch (getSide()) {
        case LEFT:
            // draw trick-lines towards left w.r.t. axis line
            for (final TickMark tickMark : tickMarks) {
                final double position = tickMark.getPosition();
                if (position < 0 || position > axisLength) {
                    // skip tick-marks outside the nominal axis length
                    continue;
                }
                final double x0 = snap(axisWidth - tickLength);
                final double x1 = snap(axisWidth);
                final double y = snap(position);
                gc.strokeLine(x0, y, x1, y);
            }
            break;

        case RIGHT:
            // draw trick-lines towards right w.r.t. axis line
            for (final TickMark tickMark : tickMarks) {
                final double position = tickMark.getPosition();
                if (position < 0 || position > axisLength) {
                    // skip tick-marks outside the nominal axis length
                    continue;
                }
                final double x0 = snap(0);
                final double x1 = snap(tickLength);
                final double y = snap(position);
                gc.strokeLine(x0, y, x1, y);
            }

            break;

        case TOP:
            // draw trick-lines upwards from axis line
            for (final TickMark tickMark : tickMarks) {
                final double position = tickMark.getPosition();
                if (position < 0 || position > axisLength) {
                    // skip tick-marks outside the nominal axis length
                    continue;
                }
                final double x = snap(position);
                final double y0 = snap(axisHeight);
                final double y1 = snap(axisHeight - tickLength);
                gc.strokeLine(x, y0, x, y1);
            }
            break;

        case BOTTOM:
            // draw trick-lines downwards from axis line
            for (final TickMark tickMark : tickMarks) {
                final double position = tickMark.getPosition();
                if (position < 0 || position > axisLength) {
                    // skip tick-marks outside the nominal axis length
                    continue;
                }
                final double x = snap(position);
                final double y0 = snap(0);
                final double y1 = snap(tickLength);
                gc.strokeLine(x, y0, x, y1);
            }
            break;

        case CENTER_HOR:
            // draw symmetric trick-lines around axis line
            for (final TickMark tickMark : tickMarks) {
                final double position = tickMark.getPosition();
                if (position < 0 || position > axisLength) {
                    // skip tick-marks outside the nominal axis length
                    continue;
                }
                final double x = snap(position);
                final double y0 = snap(axisCentre * axisHeight - tickLength);
                final double y1 = snap(axisCentre * axisHeight + tickLength);
                gc.strokeLine(x, y0, x, y1);
            }
            break;

        case CENTER_VER:
            // draw symmetric trick-lines around axis line
            for (final TickMark tickMark : tickMarks) {
                final double position = tickMark.getPosition();
                if (position < 0 || position > axisLength) {
                    // skip tick-marks outside the nominal axis length
                    continue;
                }
                final double x0 = snap(axisCentre * axisWidth - tickLength);
                final double x1 = snap(axisCentre * axisWidth + tickLength);
                final double y = snap(position);
                gc.strokeLine(x0, y, x1, y);
            }
            break;
        default:
            break;
        }

        gc.restore();
    }

    protected void drawTickMarkLabel(final GraphicsContext gc, final double x, final double y, final double rotation,
            final TickMark tickMark) {
        gc.save();
        gc.setFont(tickMark.getFont());
        gc.setFill(tickMark.getFill());
        gc.translate(x, y);
        if (rotation != 0.0) {
            gc.rotate(tickMark.getRotate());
        }
        gc.setGlobalAlpha(tickMark.getOpacity());
        gc.fillText(tickMark.getText(), 0, 0);
        // gc.fillText(tickMark.getText(), x, y);C
        gc.restore();

    }

    protected void drawTickLabels(final GraphicsContext gc, final double axisWidth, final double axisHeight,
            final ObservableList<TickMark> tickMarks, final double tickLength) {
        if (tickLength <= 0) {
            return;
        }
        final double paddingX = getSide().isHorizontal() ? getAxisPadding() : 0.0;
        final double paddingY = getSide().isVertical() ? getAxisPadding() : 0.0;
        // for relative positioning of axes drawn on top of the main canvas
        final double axisCentre = getCenterAxisPosition();
        final AxisLabelOverlapPolicy overlapPolicy = getOverlapPolicy();
        final double tickLabelGap = getTickLabelGap();
        final double tickLabelRotation = getTickLabelRotation();
        int counter = 0;

        // save css-styled label parameters
        gc.save();
        // N.B. important: translate by padding ie. canvas is +padding larger on
        // all size compared to region
        gc.translate(paddingX, paddingY);
        // N.B. streams, filter, and forEach statements have been evaluated and
        // appear to give no (or negative) performance for little/arguable
        // readability improvement (CG complexity from 40 -> 28, but mere
        // numerical number and not actual readability), thus sticking to 'for'
        // loops

        // apply best-guess tick mark font and color (minimises the need of
        // setting this for individual actual
        // tickMarks)
        if (!tickMarks.isEmpty()) {
            final TickMark firstTick = tickMarks.get(0);
            gc.setFont(firstTick.getFont());
            gc.setFill(firstTick.getFill());
            // gc.setStroke(tickMark.getStroke());
            // gc.setLineWidth(tickMark.getStrokeWidth());
            gc.setGlobalAlpha(firstTick.getOpacity());
        }

        switch (getSide()) {
        case LEFT:
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.setTextBaseline(VPos.CENTER);
            for (final TickMark tickMark : tickMarks) {
                final double position = tickMark.getPosition();
                if (!tickMark.isVisible()) {
                    // skip invisible labels
                    continue;
                }

                final double x = axisWidth - tickLength - tickLabelGap;
                final double y = position;
                drawTickMarkLabel(gc, x, y, tickLabelRotation, tickMark);
            }
            break;

        case RIGHT:
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setTextBaseline(VPos.CENTER);
            for (final TickMark tickMark : tickMarks) {
                final double position = tickMark.getPosition();
                if (!tickMark.isVisible()) {
                    // skip invisible labels
                    continue;
                }

                final double x = tickLength + tickLabelGap;
                final double y = position;
                drawTickMarkLabel(gc, x, y, tickLabelRotation, tickMark);
            }

            break;

        case TOP:
            // special alignment treatment if axes labels are to be rotated
            if (tickLabelRotation % 360 == 0) {
                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.BOTTOM);
            } else {
                // pivoting point to left-bottom label corner
                gc.setTextAlign(TextAlignment.LEFT);
                gc.setTextBaseline(VPos.BOTTOM);
            }

            for (final TickMark tickMark : tickMarks) {
                final double position = tickMark.getPosition();
                if (!tickMark.isVisible()) {
                    // skip invisible labels
                    continue;
                }

                final double x = position;
                double y = axisHeight - tickLength - tickLabelGap;
                switch (overlapPolicy) {
                case DO_NOTHING:
                    drawTickMarkLabel(gc, x, y, tickLabelRotation, tickMark);
                    break;
                case NARROW_FONT:
                    drawTickMarkLabel(gc, x, y, tickLabelRotation, tickMark);
                    break;
                case SHIFT_ALT:
                    if (isLabelOverlapping()) {
                        y -= counter % 2 * tickLabelGap + counter % 2 * tickMark.getFont().getSize();
                    }
                    drawTickMarkLabel(gc, x, y, tickLabelRotation, tickMark);
                    break;
                case FORCED_SHIFT_ALT:
                    y -= counter % 2 * tickLabelGap + counter % 2 * tickMark.getFont().getSize();
                    drawTickMarkLabel(gc, x, y, tickLabelRotation, tickMark);
                    break;
                default:
                case SKIP_ALT:
                    if (counter % 2 == 0 || !isLabelOverlapping()) {
                        drawTickMarkLabel(gc, x, y, tickLabelRotation, tickMark);
                    }
                    break;
                }
                // drawTickMarkLabel(gc, x, y, tickLabelRotation, tickMark);
            }
            break;

        case BOTTOM:
            // special alignment treatment if axes labels are to be rotated
            if (tickLabelRotation % 360 == 0) {
                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.TOP);
            } else {
                // pivoting point to left-top label corner
                gc.setTextAlign(TextAlignment.LEFT);
                gc.setTextBaseline(VPos.TOP);
            }

            counter = 0;
            for (final TickMark tickMark : tickMarks) {
                final double position = tickMark.getPosition();
                if (!tickMark.isVisible()) {
                    // skip invisible labels
                    continue;
                }

                final double x = position;
                double y = tickLength + tickLabelGap;
                switch (overlapPolicy) {
                case DO_NOTHING:
                    drawTickMarkLabel(gc, x, y, tickLabelRotation, tickMark);
                    break;
                case NARROW_FONT:
                    drawTickMarkLabel(gc, x, y, tickLabelRotation, tickMark);
                    break;
                case SHIFT_ALT:
                    if (isLabelOverlapping()) {
                        y += counter % 2 * tickLabelGap + counter % 2 * tickMark.getFont().getSize();
                    }
                    drawTickMarkLabel(gc, x, y, tickLabelRotation, tickMark);
                    break;
                case FORCED_SHIFT_ALT:
                    y += counter % 2 * tickLabelGap + counter % 2 * tickMark.getFont().getSize();
                    drawTickMarkLabel(gc, x, y, tickLabelRotation, tickMark);
                    break;
                default:
                case SKIP_ALT:
                    if (counter % 2 == 0 || !isLabelOverlapping()) {
                        drawTickMarkLabel(gc, x, y, tickLabelRotation, tickMark);
                    }
                    break;
                }
                // drawTickMarkLabel(gc, x, y, tickLabelRotation, tickMark);
                counter++;
            }
            break;

        case CENTER_VER:
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setTextBaseline(VPos.CENTER);
            for (final TickMark tickMark : tickMarks) {
                final double position = tickMark.getPosition();
                if (!tickMark.isVisible()) {
                    // skip invisible labels
                    continue;
                }

                final double x = axisCentre * axisWidth + tickLength + tickLabelGap;
                final double y = position;
                drawTickMarkLabel(gc, x, y, tickLabelRotation, tickMark);
            }

            break;

        case CENTER_HOR:
            // special alignment treatment if axes labels are to be rotated
            if (tickLabelRotation % 360 == 0) {
                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.TOP);
            } else {
                // pivoting point to left-top label corner
                gc.setTextAlign(TextAlignment.LEFT);
                gc.setTextBaseline(VPos.TOP);
            }

            for (final TickMark tickMark : tickMarks) {
                final double position = tickMark.getPosition();
                if (!tickMark.isVisible()) {
                    // skip invisible labels
                    continue;
                }

                final double x = position;
                double y = axisCentre * axisHeight + tickLength + tickLabelGap;
                switch (overlapPolicy) {
                case DO_NOTHING:
                    drawTickMarkLabel(gc, x, y, tickLabelRotation, tickMark);
                    break;
                case NARROW_FONT:
                    drawTickMarkLabel(gc, x, y, tickLabelRotation, tickMark);
                    break;
                case SHIFT_ALT:
                    if (isLabelOverlapping()) {
                        y += counter % 2 * tickLabelGap + counter % 2 * tickMark.getFont().getSize();
                    }
                    drawTickMarkLabel(gc, x, y, tickLabelRotation, tickMark);
                    break;
                case FORCED_SHIFT_ALT:
                    y += counter % 2 * tickLabelGap + counter % 2 * tickMark.getFont().getSize();
                    drawTickMarkLabel(gc, x, y, tickLabelRotation, tickMark);
                    break;
                default:
                case SKIP_ALT:
                    if (counter % 2 == 0 || !isLabelOverlapping()) {
                        drawTickMarkLabel(gc, x, y, tickLabelRotation, tickMark);
                    }
                    break;
                }
            }
            break;

        default:
            break;
        }

        gc.restore();
    }

    protected void drawAxisLabel(final GraphicsContext gc, final double x, final double y, final Text label) {
        gc.save();
        gc.setTextAlign(label.getTextAlignment());
        gc.setFont(label.getFont());
        gc.setFill(label.getFill());
        gc.setStroke(label.getStroke());
        gc.setLineWidth(label.getStrokeWidth());
        gc.translate(x, y);
        gc.rotate(label.getRotate());
        gc.fillText(label.getText(), 0, 0);
        gc.restore();

    }

    protected double measureTickMarkLength(final double major) {
        // N.B. this is a known performance hot-spot -> start optimisation here
        final TickMark tick = getNewTickMark(major, 0.0 /* NA */, getTickMarkLabel(major));
        return getSide().isHorizontal() ? tick.getWidth() : tick.getHeight();
    }

    /**
     * Computes the preferred tick unit based on the upper/lower bounds and the
     * length of the axis in screen coordinates.
     *
     * @param axisLength
     *            the length in screen coordinates
     * @return the tick unit
     */
    public abstract double computePreferredTickUnit(final double axisLength);

    protected double getMaxTickLabelWidth(final List<TickMark> tickMarks) {
        return tickMarks == null || tickMarks.isEmpty() ? 0.0
                : tickMarks.stream().mapToDouble(TickMark::getWidth).max().getAsDouble();
    }

    protected double getMaxTickLabelHeight(final List<TickMark> tickMarks) {
        return tickMarks == null || tickMarks.isEmpty() ? 0.0
                : tickMarks.stream().mapToDouble(TickMark::getHeight).max().getAsDouble();
    }

    protected void drawAxisLabel(final GraphicsContext gc, final double axisLength, final double axisWidth,
            final double axisHeight, final Text axisLabel, final ObservableList<TickMark> tickMarks,
            final double tickLength) {

        final double paddingX = getSide().isHorizontal() ? getAxisPadding() : 0.0;
        final double paddingY = getSide().isVertical() ? getAxisPadding() : 0.0;
        final boolean isHorizontal = getSide().isHorizontal();
        final double tickLabelGap = getTickLabelGap();
        final double axisLabelGap = getAxisLabelGap();

        // for relative positioning of axes drawn on top of the main canvas
        final double axisCentre = getCenterAxisPosition();
        double labelPosition;
        double labelGap;
        switch (axisLabel.getTextAlignment()) {
        case LEFT:
            labelPosition = 0.0;
            labelGap = +tickLabelGap;
            break;
        case RIGHT:
            labelPosition = 1.0;
            labelGap = -tickLabelGap;
            break;
        case CENTER:
        case JUSTIFY:
        default:
            labelPosition = 0.5;
            labelGap = 0.0;
            break;
        }

        // find largest tick label size (width for horizontal axis, height for
        // vertical axis)
        final double tickLabelSize = isHorizontal ? maxLabelHeight : maxLabelWidth;
        final double shiftedLabels = getOverlapPolicy() == AxisLabelOverlapPolicy.SHIFT_ALT && isLabelOverlapping()
                || getOverlapPolicy() == AxisLabelOverlapPolicy.FORCED_SHIFT_ALT ? tickLabelSize + tickLabelGap : 0.0;

        // save css-styled label parameters
        gc.save();
        gc.translate(paddingX, paddingY);
        // N.B. streams, filter, and forEach statements have been evaluated and
        // appear to give no (or negative) performance for little/arguable
        // readability improvement (CG complexity from 40 -> 28, but mere
        // numerical number and not actual readability), thus sticking to 'for'
        // loops
        switch (getSide()) {
        case LEFT: {
            // gc.setTextBaseline(VPos.BOTTOM);
            gc.setTextBaseline(VPos.BASELINE);
            final double x = axisWidth - tickLength - 2 * tickLabelGap - tickLabelSize - axisLabelGap - shiftedLabels;
            final double y = (1.0 - labelPosition) * axisHeight - labelGap;
            axisLabel.setRotate(-90);
            drawAxisLabel(gc, x, y, axisLabel);
        }
            break;

        case RIGHT: {
            gc.setTextBaseline(VPos.TOP);
            axisLabel.setRotate(-90);
            final double x = tickLength + tickLabelGap + tickLabelSize + axisLabelGap + shiftedLabels;
            final double y = (1.0 - labelPosition) * axisHeight - labelGap;
            drawAxisLabel(gc, x, y, axisLabel);
        }
            break;

        case TOP: {
            gc.setTextBaseline(VPos.BOTTOM);
            final double x = labelPosition * axisWidth + labelGap;
            final double y = axisHeight - tickLength - tickLabelGap - tickLabelSize - axisLabelGap - shiftedLabels;
            drawAxisLabel(gc, x, y, axisLabel);
        }
            break;

        case BOTTOM: {
            gc.setTextBaseline(VPos.TOP);
            final double x = labelPosition * axisWidth + labelGap;
            final double y = tickLength + tickLabelGap + tickLabelSize + axisLabelGap + shiftedLabels;
            drawAxisLabel(gc, x, y, axisLabel);
        }
            break;

        case CENTER_VER: {
            gc.setTextBaseline(VPos.TOP);
            axisLabel.setRotate(-90);
            final double x = axisCentre * axisWidth - tickLength - tickLabelGap - tickLabelSize - axisLabelGap
                    - shiftedLabels;
            final double y = (1.0 - labelPosition) * axisHeight - labelGap;
            drawAxisLabel(gc, x, y, axisLabel);

        }
            break;

        case CENTER_HOR: {
            gc.setTextBaseline(VPos.TOP);
            final double x = labelPosition * axisWidth + labelGap;
            final double y = axisCentre * axisHeight + tickLength + tickLabelGap + tickLabelSize + axisLabelGap
                    + shiftedLabels;
            drawAxisLabel(gc, x, y, axisLabel);
        }
            break;

        default:
            break;
        }

        gc.restore();
    }

    /**
     * Checks if two consecutive tick mark labels overlaps.
     *
     * @param side
     *            side of the Axis
     * @param m1
     *            first tick mark
     * @param m2
     *            second tick mark
     * @param gap
     *            minimum space between labels
     * @return true if labels overlap
     */
    private boolean isTickLabelsOverlap(final Side side, final TickMark m1, final TickMark m2, final double gap) {
        if (!m1.isVisible() || !m2.isVisible()) {
            return false;
        }
        final double m1Size = side.isHorizontal() ? m1.getWidth() : m1.getHeight();
        final double m2Size = side.isHorizontal() ? m2.getWidth() : m2.getHeight();
        final double m1Start = m1.getPosition() - m1Size / 2;
        final double m1End = m1.getPosition() + m1Size / 2;
        final double m2Start = m2.getPosition() - m2Size / 2;
        final double m2End = m2.getPosition() + m2Size / 2;
        return side.isVertical() ? m1Start - m2End <= gap : m2Start - m1End <= gap;
    }

    /**
     * Get the string label name for a tick mark with the given value
     *
     * @param value
     *            The value to format into a tick label string
     * @return A formatted string for the given value
     */
    @Override
    public String getTickMarkLabel(final double value) {
        // convert value according to scale factor
        final double scaledValue = value / getUnitScaling();

        final StringConverter<Number> formatter = getTickLabelFormatter();
        if (formatter != null) {
            return formatter.toString(scaledValue);
        }
        // use AxisLabelFormatter based implementation
        return getAxisLabelFormatter().toString(scaledValue);
    }

    @Override
    public void addListener(final InvalidationListener listener) {
        Objects.requireNonNull(listener, "InvalidationListener must not be null");
        // N.B. suppress duplicates
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(final InvalidationListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void setAutoNotifaction(final boolean flag) {
        autoNotification = flag;
    }

    @Override
    public boolean isAutoNotification() {
        return autoNotification;
    }

    /**
     * Notifies listeners that the data has been invalidated. If the data is
     * added to the chart, it triggers repaint.
     */
    @Override
    public void fireInvalidated() {
        if (!autoNotification || listeners.isEmpty()) {
            return;
        }

        if (Platform.isFxApplicationThread()) {
            executeFireInvalidated();
        } else {
            Platform.runLater(this::executeFireInvalidated);
        }
    }

    protected void executeFireInvalidated() {
        // N.B. explicit copy of listeners to avoid multi-threading
        // race-conditions
        for (final InvalidationListener listener : new ArrayList<>(listeners)) {
            listener.invalidated(this);
        }
    }

    // some protection overwrites
    /**
     * Get the display position along this axis for a given value. If the value
     * is not in the current range, the returned value will be an extrapolation
     * of the display position.
     *
     * @param value
     *            The data value to work out display position for
     * @return display position
     */
    @Override
    public double getDisplayPosition(final double value) {
        return cachedOffset + (value - currentLowerBound.get()) * getScale();
    }

    /**
     * Get the display position of the zero line along this axis.
     *
     * @return display position or Double.NaN if zero is not in current range;
     */
    @Override
    public double getZeroPosition() {
        if (0 < getLowerBound() || 0 > getUpperBound()) {
            return Double.NaN;
        }
        // noinspection unchecked
        return getDisplayPosition(Double.valueOf(0));
    }

    @Override
    public void setLowerBound(final double value) {
        if (isLogAxis() && (value <= 0 || !Double.isFinite(value))) {
            if (getUpperBound() > 0) {
                super.setLowerBound(getUpperBound() / 1.0E6);
            }
            return;
        }
        super.setLowerBound(value);
    }

    @Override
    public void setUpperBound(final double value) {
        if (isLogAxis() && (value <= 0 || !Double.isFinite(value))) {
            if (getLowerBound() >= 0) {
                super.setUpperBound(getLowerBound() * 1.0E6);
            }
            return;
        }
        super.setUpperBound(value);
    }

}
