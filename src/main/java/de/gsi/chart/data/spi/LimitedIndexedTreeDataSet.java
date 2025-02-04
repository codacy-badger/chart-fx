package de.gsi.chart.data.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import de.gsi.chart.data.DataSetError;
import de.gsi.chart.data.utils.trees.IndexedNavigableSet;
import de.gsi.chart.data.utils.trees.IndexedTreeSet;
import de.gsi.chart.utils.AssertUtils;

/**
 * DataSet implementation based on a sorted indexed TreeDataSets.
 * This tree data set is sorted (allows on-the-fly insertions), indexed (required for the chart interface to
 * efficiently parse through the data ('efficient' for most 'normal' implementations), limited queue (to limit the
 * maximum number samples stored), and limited in time (based on 'x').
 *
 * @see de.gsi.chart.data.DataSet
 * @see de.gsi.chart.data.DataSetError
 * @author rstein
 */
public class LimitedIndexedTreeDataSet extends AbstractErrorDataSet<LimitedIndexedTreeDataSet> {
    protected IndexedNavigableSet<DataAtom> data = new IndexedTreeSet<>();
    protected int maxQueueSize = Integer.MAX_VALUE;
    protected double maxLength = Double.MAX_VALUE;

    /**
     * Creates a new instance of <code>DefaultDataSet</code>.
     *
     * @param name
     *            name of this DataSet.
     * @param maxQueueSize maximum number of samples that is being stored
     * @throws IllegalArgumentException
     *             if <code>name</code> is <code>null</code>
     */
    public LimitedIndexedTreeDataSet(final String name, final int maxQueueSize) {
        super(name);
        setErrorType(ErrorType.XY);
        this.maxQueueSize = maxQueueSize;
    }

    /**
     * Creates a new instance of <code>DefaultDataSet</code>.
     *
     * @param name
     *            name of this DataSet.
     * @param maxQueueSize maximum number of samples that is being stored
     * @param maxLength maximum length between first and last sample
     * @throws IllegalArgumentException
     *             if <code>name</code> is <code>null</code>
     */
    public LimitedIndexedTreeDataSet(final String name, final int maxQueueSize, final double maxLength) {
        super(name);
        setErrorType(ErrorType.XY);
        this.maxQueueSize = maxQueueSize;
        this.maxLength = maxLength;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public LimitedIndexedTreeDataSet setMaxQueueSize(final int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
        return this;
    }

    public double getMaxLength() {
        return maxLength;
    }

    public LimitedIndexedTreeDataSet setMaxLength(final double maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    public LimitedIndexedTreeDataSet reset() {
        lock();
        getData().clear();
        unlock();
        return this;
    }

    public void expire() {
        lock();
        expire(data.last().getX());
        unlock();
    }

    public void expire(final double now) {
        lock();
        try {
            DataAtom first = data.first();
            if (first == null) {
                return;
            }
            for (; data.size() > maxQueueSize || now - first.getX() > maxLength; first = data.first()) {
                data.remove(first);
            }
            computeLimits();
        } catch (final NoSuchElementException cannotDoAnythingHere) {
            // cannot do anything here
        } finally {
            unlock();
        }
    }

    public IndexedNavigableSet<DataAtom> getData() {
        return data;
    }

    @Override
    public int getDataCount() {
        return data.size();
    }

    /**
     * @return the x coordinate
     */
    @Override
    public double getX(final int i) {
        return data.get(i).getX();
    }

    /**
     * @return the y coordinate
     */
    @Override
    public double getY(final int i) {
        return data.get(i).getY();
    }

    /**
     * @see DataSetError#getXErrorNegative(int)
     * @return the negative error of the x coordinate
     */
    @Override
    public double getXErrorNegative(final int index) {
        return data.get(index).getErrorX();
    }

    /**
     * @see DataSetError#getXErrorPositive(int)
     * @return the positive error of the x coordinate
     */
    @Override
    public double getXErrorPositive(final int index) {
        return data.get(index).getErrorX();
    }

    /**
     * @see DataSetError#getYErrorNegative(int)
     * @return the negative error of the y coordinate
     */
    @Override
    public double getYErrorNegative(final int index) {
        return data.get(index).getErrorY();
    }

    /**
     * @see DataSetError#getYErrorPositive(int)
     * @return the positive error of the y coordinate
     */
    @Override
    public double getYErrorPositive(final int index) {
        return data.get(index).getErrorY();
    }

    /**
     * Sets the point with index to the new coordinate
     *
     * @param index
     *            the point index of the data set
     * @param x
     *            the horizontal coordinate of the data point
     * @param y
     *            the vertical coordinate of the data point
     * @return itself
     */
    public LimitedIndexedTreeDataSet set(final int index, final double x, final double y) {
        return set(index, x, y, 0, 0);
    }

    public LimitedIndexedTreeDataSet clearData() {
        lock().setAutoNotifaction(false);

        data.clear();
        xRange.empty();
        yRange.empty();

        return setAutoNotifaction(true).unlock().fireInvalidated();
    }

    /**
     * Sets the point with index to the new coordinate
     *
     * @param index
     *            the point index of the data set
     * @param x
     *            the horizontal coordinate of the data point
     * @param y
     *            the vertical coordinate of the data point
     * @param dx
     *            the horizontal error
     * @param dy
     *            the vertical error N.B. assumes symmetric errors
     * @return itself
     */
    public LimitedIndexedTreeDataSet set(final int index, final double x, final double y, final double dx,
            final double dy) {
        lock();
        data.get(index).set(x, y, dy, dy);

        xRange.add(x - dx);
        xRange.add(x + dx);
        yRange.add(y - dy);
        yRange.add(y + dy);
        expire();

        return unlock().fireInvalidated();
    }

    /**
     * <p>
     * Initialises the data set with specified data.
     * </p>
     * Note: The method copies values from specified double arrays.
     *
     * @param xValues
     *            X coordinates
     * @param yValues
     *            Y coordinates
     * @param xErrors
     *            symmetric X coordinate errors
     * @param yErrors
     *            symmetric Y coordinate errors
     * @param count
     *            number of points to be taken from specified arrays.
     * @return itself
     */
    public LimitedIndexedTreeDataSet set(final double[] xValues, final double[] yValues, final double[] xErrors,
            final double[] yErrors, final int count) {
        lock().setAutoNotifaction(false);

        AssertUtils.notNull("X coordinates", xValues);
        AssertUtils.notNull("Y coordinates", yValues);

        if (xValues.length < count || yValues.length < count || xErrors.length < count || yErrors.length < count) {
            throw new IllegalArgumentException("Arrays with coordinates must have length >= count!");
        }

        for (int i = 0; i < xValues.length; i++) {
            final double x = xValues[i];
            final double y = yValues[i];
            final double dx = xErrors[i];
            final double dy = yValues[i];
            xRange.add(x - dx);
            xRange.add(x + dx);
            yRange.add(y - dy);
            yRange.add(y + dy);
            data.add(new DataAtom(x, y, dx, dy));
        }
        expire();

        return setAutoNotifaction(true).unlock().fireInvalidated();
    }

    /**
     * <p>
     * Initialises the data set with specified data.
     * </p>
     * Note: The method copies values from specified double arrays.
     *
     * @param xValues
     *            X coordinates
     * @param yValues
     *            Y coordinates
     * @param count
     *            number of points to be taken from specified arrays.
     * @return itself
     */
    public LimitedIndexedTreeDataSet set(final double[] xValues, final double[] yValues, final int count) {
        return this.set(xValues, yValues, new double[count], new double[count], count);
    }

    /**
     * <p>
     * Initialises the data set with specified data.
     * </p>
     * Note: The method copies values from specified double arrays.
     *
     * @param xValues
     *            X coordinates
     * @param yValues
     *            Y coordinates
     * @param yErrors
     *            symmetric Y coordinate errors
     * @param count
     *            number of points to be taken from specified arrays.
     * @return itself
     */
    public LimitedIndexedTreeDataSet set(final double[] xValues, final double[] yValues, final double[] yErrors,
            final int count) {
        return this.set(xValues, yValues, new double[count], yErrors, count);
    }

    /**
     * <p>
     * Initialises the data set with specified data.
     * </p>
     * Note: The method copies values from specified double arrays.
     *
     * @param xValues
     *            X coordinates
     * @param yValues
     *            Y coordinates
     * @return itself
     */
    public LimitedIndexedTreeDataSet set(final double[] xValues, final double[] yValues) {
        final int ndim = xValues.length;
        return this.set(xValues, yValues, new double[ndim], new double[ndim], ndim);
    }

    /**
     * @param x
     *            coordinate
     * @param y
     *            coordinate
     * @return itself
     */
    public LimitedIndexedTreeDataSet add(final double x, final double y) {
        return add(x, y, 0, 0);
    }

    /**
     * add new point
     *
     * @param x
     *            horizontal point coordinate
     * @param y
     *            vertical point coordinate
     * @param ex
     *            horizontal point error
     * @param ey
     *            vertical point error Note: point errors are expected to be
     *            positive
     * @return itself
     */
    public LimitedIndexedTreeDataSet add(final double x, final double y, final double ex, final double ey) {
        lock();

        data.add(new DataAtom(x, y, ex, ey));
        xRange.add(x - ex);
        xRange.add(x + ex);
        yRange.add(y - ey);
        yRange.add(y + ey);
        expire();

        return unlock().fireInvalidated();
    }

    /**
     * Adds data points to this data set. <br>
     * If <code>usingXValues</code> flag is set to false - array with X
     * coordinates is not taken into account (may be <code>null</code>)
     * otherwise both arrays must be non-null and have the same length.
     *
     * @param xValues
     *            X coordinates
     * @param yValues
     *            Y coordinates
     * @return itself
     */
    public LimitedIndexedTreeDataSet add(final double[] xValues, final double[] yValues) {
        return this.add(xValues, yValues, new double[yValues.length], new double[yValues.length]);
    }

    /**
     * Adds data points to this data set. <br>
     * If <code>usingXValues</code> flag is set to false - array with X
     * coordinates is not taken into account (may be <code>null</code>)
     * otherwise both arrays must be non-null and have the same length.
     *
     * @param xValues
     *            X coordinates
     * @param yValues
     *            Y coordinates
     * @param xErrors
     *            horizontal errors
     * @param yErrors
     *            vertical errors
     * @return itself
     */
    public LimitedIndexedTreeDataSet add(final double[] xValues, final double[] yValues, final double[] xErrors,
            final double[] yErrors) {
        lock().setAutoNotifaction(false);
        AssertUtils.notNull("X data", xValues);
        AssertUtils.notNull("X error data", xErrors);
        AssertUtils.notNull("Y data", yValues);
        AssertUtils.notNull("Y error data", yValues);

        for (int i = 0; i < xValues.length; i++) {
            final double x = xValues[i];
            final double y = yValues[i];
            final double ex = xErrors[i];
            final double ey = yErrors[i];
            data.add(new DataAtom(x, y, ex, ey));

            xRange.add(x - ex);
            xRange.add(x + ex);
            yRange.add(y - ey);
            yRange.add(y + ey);
        }
        expire();

        return setAutoNotifaction(true).unlock().fireInvalidated();
    }

    /*
     * (non-Javadoc)
     *
     * @see de.gsi.chart.DataSet#remove(int, int)
     */
    public LimitedIndexedTreeDataSet remove(final int fromIndex, final int toIndex) {
        lock().setAutoNotifaction(false);
        AssertUtils.indexInBounds(fromIndex, getDataCount(), "fromIndex");
        AssertUtils.indexInBounds(toIndex, getDataCount(), "toIndex");
        AssertUtils.indexOrder(fromIndex, "fromIndex", toIndex, "toIndex");

        final List<DataAtom> toRemove = new ArrayList<>();
        for (int i = fromIndex; i < toIndex; i++) {
            toRemove.add(data.get(i));
        }
        data.removeAll(toRemove);

        xRange.setMax(Double.NaN);
        yRange.setMax(Double.NaN);

        return setAutoNotifaction(true).unlock().fireInvalidated();
    }

    /**
     * Removes from this data set points with specified indices.
     *
     * @param indices
     *            array of indicices to be removed
     * @return itself
     */
    public LimitedIndexedTreeDataSet remove(final int[] indices) {
        lock().setAutoNotifaction(false);
        AssertUtils.notNull("Indices array", indices);
        if (indices.length == 0) {
            return unlock();
        }

        final List<DataAtom> tupleTobeRemovedReferences = new ArrayList<>();
        for (final int indexToRemove : indices) {
            tupleTobeRemovedReferences.add(data.get(indexToRemove));
        }
        data.removeAll(tupleTobeRemovedReferences);

        xRange.setMax(Double.NaN);
        yRange.setMax(Double.NaN);
        super.computeLimits();

        return setAutoNotifaction(true).unlock().fireInvalidated();
    }

    /**
     * adds a custom new data label for a point The label can be used as a
     * category name if CategoryStepsDefinition is used or for annotations
     * displayed for data points.
     *
     * @param index
     *            of the data point
     * @param label
     *            for the data point specified by the index
     * @return the previously set label or <code>null</code> if no label has
     *         been specified
     */
    public String addDataLabel(final int index, final String label) {
        final String old = data.get(index).dataLabel;
        data.get(index).dataLabel = label;
        return old;
    }

    /**
     * remove a custom data label for a point The label can be used as a
     * category name if CategoryStepsDefinition is used or for annotations
     * displayed for data points.
     *
     * @param index
     *            of the data point
     * @return the previously set label or <code>null</code> if no label has
     *         been specified
     */
    public String removeDataLabel(final int index) {
        final String old = data.get(index).dataLabel;
        data.get(index).dataLabel = null;
        return old;
    }

    /**
     * Returns label of a data point specified by the index. The label can be
     * used as a category name if CategoryStepsDefinition is used or for
     * annotations displayed for data points.
     *
     * @param index
     *            of the data label
     * @return data point label specified by the index or <code>null</code> if
     *         no label has been specified
     */
    @Override
    public String getDataLabel(final int index) {
        final String dataLabel = data.get(index).getLabel();
        if (dataLabel != null) {
            return dataLabel;
        }

        return super.getDataLabel(index);
    }

    /**
     * A string representation of the CSS style associated with this specific
     * {@code DataSet} data point. @see #getStyle()
     *
     * @param index
     *            the index of the specific data point
     * @return the previously set style or <code>null</code> if no label has
     *         been specified
     */
    public String addDataStyle(final int index, final String style) {
        final String old = data.get(index).dataStyle;
        data.get(index).dataStyle = style;
        return old;
    }

    /**
     * A string representation of the CSS style associated with this specific
     * {@code DataSet} data point. @see #getStyle()
     *
     * @param index
     *            the index of the specific data point
     * @return the previously set style or <code>null</code> if no label has
     *         been specified
     */
    public String removeStyle(final int index) {
        final String old = data.get(index).dataStyle;
        data.get(index).dataStyle = null;
        return old;
    }

    /**
     * A string representation of the CSS style associated with this specific
     * {@code DataSet} data point. @see #getStyle()
     *
     * @param index
     *            the index of the specific data point
     * @return user-specific data set style description (ie. may be set by user)
     */
    @Override
    public String getStyle(final int index) {
        return data.get(index).getStyle();
    }

    protected class DataAtom implements Comparable<DataAtom> {
        protected double x; // horizontal value
        protected double y; // vertical value
        protected double ex; // positive error on X
        protected double ey; // positive error on Y

        protected String dataLabel;
        protected String dataStyle;

        DataAtom(final double x, final double y, final double ex, final double ey) {
            this.x = x;
            this.y = y;
            this.ex = ex;
            this.ey = ey;
        }

        void set(final double x, final double y, final double ex, final double ey) {
            this.x = x;
            this.y = y;
            this.ex = ex;
            this.ey = ey;
        }

        double getX() {
            return x;
        }

        double getY() {
            return y;
        }

        double getErrorX() {
            return ex;
        }

        double getErrorY() {
            return ey;
        }

        String getLabel() {
            return dataLabel;
        }

        String getStyle() {
            return dataStyle;
        }

        @Override
        public int compareTo(final DataAtom other) {
            if (this.getX() < other.getX()) {
                return -1;
            } else if (this.getX() > other.getX()) {
                return +1;
            }
            return 0;
        }
    }
}
