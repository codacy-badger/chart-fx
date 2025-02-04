package de.gsi.chart.data.spi;

import java.util.Arrays;

import de.gsi.chart.data.DataSet;
import de.gsi.chart.data.DataSetError;
import de.gsi.chart.utils.AssertUtils;
import de.gsi.math.ArrayUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.scene.transform.Scale;

public class DoubleErrorDataSet extends AbstractErrorDataSet<DoubleErrorDataSet> implements DataSetError {

    protected ObservableMap<Integer, String> dataLabels = FXCollections.observableHashMap();
    protected ObservableMap<Integer, String> dataStyles = FXCollections.observableHashMap();
    protected double[] xValues;
    protected double[] yValues;
    protected double[] yErrorsPos;
    protected double[] yErrorsNeg;
    protected int dataMaxIndex; // <= xValues.length, stores the actually used
                                // data array size

    /**
     * Creates a new instance of <code>DoubleErrorDataSet</code>.
     *
     * @param name name of this DataSet.
     * @throws IllegalArgumentException if <code>name</code> is <code>null</code>
     */
    public DoubleErrorDataSet(final String name) {
        this(name, 0);
    }

    /**
     * Creates a new instance of <code>DoubleErrorDataSet</code>.
     *
     * @param name name of this DataSet.
     * @param initalSize maximum capacity of buffer
     * @throws IllegalArgumentException if <code>name</code> is <code>null</code>
     */
    public DoubleErrorDataSet(final String name, final int initalSize) {
        super(name);
        AssertUtils.gtEqThanZero("initalSize", initalSize);
        xValues = new double[initalSize];
        yValues = new double[initalSize];
        yErrorsPos = new double[initalSize];
        yErrorsNeg = new double[initalSize];
        setErrorType(ErrorType.Y_ASYMMETRIC);
        dataMaxIndex = 0;
        // computeLimits();
    }

    /**
     * Creates a new instance of <code>DoubleErrorDataSet</code>. X coordinates are equal to data points indices. <br>
     *
     * @param name name of this data set.
     * @param yValues Y coordinates
     * @param nData how many data points are relevant to be taken
     * @throws IllegalArgumentException if any of parameters is <code>null</code>
     */
    public DoubleErrorDataSet(final String name, final double[] yValues, final int nData) {
        this(name, Math.min(yValues.length, nData));
        dataMaxIndex = Math.min(yValues.length, nData);
        System.arraycopy(yValues, 0, this.yValues, 0, dataMaxIndex);
        for (Integer i = 0; i < dataMaxIndex; i++) {
            xValues[i] = i;
        }

        ArrayUtils.fillArray(yErrorsPos, 0.0);
        ArrayUtils.fillArray(yErrorsNeg, 0.0);
        computeLimits();
    }

    /**
     * Creates a new instance of <code>DoubleErrorDataSet</code>. X coordinates are equal to data points indices. <br>
     *
     * @param name name of this data set.
     * @param yValues Y coordinates
     * @throws IllegalArgumentException if any of parameters is <code>null</code>
     */
    public DoubleErrorDataSet(final String name, final double[] yValues) {
        this(name, yValues, yValues.length);
    }

    /**
     * Creates a new instance of <code>DoubleErrorDataSet</code>. X coordinates are equal to data points indices. <br>
     *
     * @param name name of this data set.
     * @param xValues x coordinates
     * @param yValues Y coordinates
     * @param nData how many data points are relevant to be taken
     * @throws IllegalArgumentException if any of parameters is <code>null</code>
     */
    public DoubleErrorDataSet(final String name, final double[] xValues, final double[] yValues, final int nData) {
        this(name, Math.min(xValues.length, Math.min(yValues.length, nData)));
        AssertUtils.notNull("X data", xValues);
        AssertUtils.notNull("Y data", yValues);
        // dataMaxIndex = xValues.length;
        final int errorMin = Math.min(yErrorsPos.length, yErrorsNeg.length);
        dataMaxIndex = Math.min(xValues.length, Math.min(yValues.length, errorMin));
        AssertUtils.equalDoubleArrays(xValues, yValues, dataMaxIndex);
        if (dataMaxIndex != 0) {
            System.arraycopy(xValues, 0, this.xValues, 0, dataMaxIndex);
            System.arraycopy(yValues, 0, this.yValues, 0, dataMaxIndex);
            ArrayUtils.fillArray(yErrorsPos, 0.0);
            ArrayUtils.fillArray(yErrorsNeg, 0.0);
        }
        computeLimits();
    }

    /**
     * Creates a new instance of <code>DoubleErrorDataSet</code>. X coordinates are equal to data points indices. <br>
     *
     * @param name name of this data set.
     * @param xValues x coordinates
     * @param yValues Y coordinates
     * @throws IllegalArgumentException if any of parameters is <code>null</code>
     */
    public DoubleErrorDataSet(final String name, final double[] xValues, final double[] yValues) {
        this(name, xValues, yValues, xValues.length);
    }

    /**
     * <p>
     * Creates a new instance of <code>DoubleErrorDataSet</code>.
     * </p>
     *
     * @param name name of this data set.
     * @param xValues X coordinates
     * @param yValues Y coordinates
     * @param yErrorsNeg Y negative coordinate error
     * @param yErrorsPos Y positive coordinate error
     * @param nData how many data points are relevant to be taken
     * @throws IllegalArgumentException if any of parameters is <code>null</code> or if arrays with coordinates have
     *             different lengths
     */
    public DoubleErrorDataSet(final String name, final double[] xValues, final double[] yValues,
            final double[] yErrorsNeg, final double[] yErrorsPos, final int nData) {
        this(name, Math.min(xValues.length, Math.min(yValues.length, nData)));
        AssertUtils.notNull("X data", xValues);
        AssertUtils.notNull("Y data", yValues);
        AssertUtils.notNull("Y error pos", yErrorsPos);
        AssertUtils.notNull("Y error neg", yErrorsNeg);
        // dataMaxIndex = xValues.length;
        final int errorMin = Math.min(Math.min(yErrorsPos.length, yErrorsNeg.length), nData);
        dataMaxIndex = Math.min(xValues.length, Math.min(yValues.length, errorMin));
        AssertUtils.equalDoubleArrays(xValues, yValues, dataMaxIndex);
        AssertUtils.equalDoubleArrays(xValues, yErrorsPos, dataMaxIndex);
        AssertUtils.equalDoubleArrays(xValues, yErrorsNeg, dataMaxIndex);
        if (dataMaxIndex != 0) {
            System.arraycopy(xValues, 0, this.xValues, 0, dataMaxIndex);
            System.arraycopy(yValues, 0, this.yValues, 0, dataMaxIndex);
            System.arraycopy(yErrorsPos, 0, this.yErrorsPos, 0, dataMaxIndex);
            System.arraycopy(yErrorsNeg, 0, this.yErrorsNeg, 0, dataMaxIndex);
        }
        computeLimits();
    }

    public ObservableMap<Integer, String> getDataLabelProperty() {
        return dataLabels;
    }

    public ObservableMap<Integer, String> getDataStyleProperty() {
        return dataStyles;
    }

    @Override
    public double[] getXValues() {
        return xValues;
    }

    @Override
    public double[] getYValues() {
        return yValues;
    }

    @Override
    public double[] getYErrorsPositive() {
        return yErrorsPos;
    }

    @Override
    public double[] getYErrorsNegative() {
        return yErrorsNeg;
    }

    @Override
    public int getDataCount() {
        return Math.min(dataMaxIndex, xValues.length);
    }

    public DoubleErrorDataSet clearData() {
        lock();

        dataMaxIndex = 0;
        Arrays.fill(xValues, 0.0);
        Arrays.fill(yValues, 0.0);
        Arrays.fill(yErrorsPos, 0.0);
        Arrays.fill(yErrorsNeg, 0.0);
        dataLabels.isEmpty();
        dataStyles.isEmpty();

        xRange.empty();
        yRange.empty();

        unlock();
        fireInvalidated();
        return this;
    }

    @Override
    public double getX(final int index) {
        return xValues[index];
    }

    @Override
    public double getY(final int index) {
        return yValues[index];
    }

    @Override
    public double getXErrorNegative(final int index) {
        return 0;
    }

    @Override
    public double getXErrorPositive(final int index) {
        return 0;
    }

    @Override
    public double getYErrorNegative(final int index) {
        return yErrorsNeg[index];
    }

    @Override
    public double getYErrorPositive(final int index) {
        return yErrorsPos[index];
    }

    public DoubleErrorDataSet set(final int index, final double x, final double y, final double yErrorNeg,
            final double yErrorPos) {
        lock();

        try {
            if (index < dataMaxIndex) {
                xValues[index] = x;
                yValues[index] = y;
                yErrorsPos[index] = yErrorPos;
                yErrorsNeg[index] = yErrorNeg;
                // dataMaxIndex = Math.max(index, dataMaxIndex);
            } else {
                this.add(x, y, yErrorNeg, yErrorPos);
            }

            xRange.add(x);
            yRange.add(y - yErrorNeg);
            yRange.add(y + yErrorPos);
        } finally {
            unlock();
        }
        fireInvalidated();
        return this;
    }

    /**
     * Add point to the DoublePoints object
     *
     * @param x the new x coordinate
     * @param y the new y coordinate
     * @param yErrorNeg the +dy error
     * @param yErrorPos the -dy error
     * @return itself
     */
    public DoubleErrorDataSet add(final double x, final double y, final double yErrorNeg, final double yErrorPos) {
        lock();

        // enlarge array if necessary
        if (dataMaxIndex > xValues.length - 1) {
            final double[] xValuesNew = new double[xValues.length + 1];
            final double[] yValuesNew = new double[yValues.length + 1];
            final double[] yErrorsNegNew = new double[yValues.length + 1];
            final double[] yErrorsPosNew = new double[yValues.length + 1];

            System.arraycopy(xValues, 0, xValuesNew, 0, xValues.length);
            System.arraycopy(yValues, 0, yValuesNew, 0, yValues.length);
            System.arraycopy(yErrorsNeg, 0, yErrorsNegNew, 0, yValues.length);
            System.arraycopy(yErrorsPos, 0, yErrorsPosNew, 0, yValues.length);

            xValues = xValuesNew;
            yValues = yValuesNew;
            yErrorsPos = yErrorsPosNew;
            yErrorsNeg = yErrorsNegNew;
        }

        xValues[dataMaxIndex] = x;
        yValues[dataMaxIndex] = y;
        yErrorsPos[dataMaxIndex] = yErrorNeg;
        yErrorsNeg[dataMaxIndex] = yErrorPos;
        dataMaxIndex++;

        xRange.add(x);
        yRange.add(y - yErrorNeg);
        yRange.add(y + yErrorPos);

        unlock();
        fireInvalidated();
        return this;
    }

    public DoubleErrorDataSet remove(final int fromIndex, final int toIndex) {
        lock();
        AssertUtils.indexInBounds(fromIndex, getDataCount(), "fromIndex");
        AssertUtils.indexInBounds(toIndex, getDataCount(), "toIndex");
        AssertUtils.indexOrder(fromIndex, "fromIndex", toIndex, "toIndex");
        final int diffLength = toIndex - fromIndex;

        // TODO: performance-critical memory/cpu tradeoff
        // check whether this really needed (keeping the data and reducing just
        // the dataMaxIndex costs some memory but saves new allocation
        final int newLength = xValues.length - diffLength;
        final double[] xValuesNew = new double[newLength];
        final double[] yValuesNew = new double[newLength];
        final double[] yErrorsNegNew = new double[newLength];
        final double[] yErrorsPosNew = new double[newLength];
        System.arraycopy(xValues, 0, xValuesNew, 0, fromIndex);
        System.arraycopy(yValues, 0, yValuesNew, 0, fromIndex);
        System.arraycopy(yErrorsNeg, 0, yErrorsNegNew, 0, fromIndex);
        System.arraycopy(yErrorsPos, 0, yErrorsPosNew, 0, fromIndex);
        System.arraycopy(xValues, toIndex, xValuesNew, fromIndex, newLength - fromIndex);
        System.arraycopy(yValues, toIndex, yValuesNew, fromIndex, newLength - fromIndex);
        System.arraycopy(yErrorsNeg, toIndex, yErrorsNegNew, fromIndex, newLength - fromIndex);
        System.arraycopy(yErrorsPos, toIndex, yErrorsPosNew, fromIndex, newLength - fromIndex);
        xValues = xValuesNew;
        yValues = yValuesNew;
        yErrorsPos = yErrorsPosNew;
        yErrorsNeg = yErrorsNegNew;
        dataMaxIndex = Math.max(0, dataMaxIndex - diffLength);

        xRange.empty();
        yRange.empty();

        unlock();
        fireInvalidated();
        return this;
    }

    /**
     * <p>
     * Initialises the data set with specified data.
     * </p>
     * Note: The method copies values from specified double arrays.
     *
     * @param xValues X coordinates
     * @param yValues Y coordinates
     */
    public DoubleErrorDataSet add(final double[] xValues, final double[] yValues, final double[] yErrorsNeg,
            final double[] yErrorsPos) {
        lock();

        AssertUtils.notNull("X coordinates", xValues);
        AssertUtils.notNull("Y coordinates", yValues);
        AssertUtils.notNull("Y error neg", yErrorsNeg);
        AssertUtils.notNull("Y error pos", yErrorsPos);
        AssertUtils.equalDoubleArrays(xValues, yValues);
        AssertUtils.equalDoubleArrays(xValues, yErrorsNeg);
        AssertUtils.equalDoubleArrays(xValues, yErrorsPos);

        final int newLength = this.getDataCount() + xValues.length;
        // need to allocate new memory
        if (newLength > this.xValues.length) {
            final double[] xValuesNew = new double[newLength];
            final double[] yValuesNew = new double[newLength];
            final double[] yErrorsNegNew = new double[newLength];
            final double[] yErrorsPosNew = new double[newLength];

            // copy old data
            System.arraycopy(this.xValues, 0, xValuesNew, 0, getDataCount());
            System.arraycopy(this.yValues, 0, yValuesNew, 0, getDataCount());
            System.arraycopy(yErrorsNeg, 0, yErrorsNegNew, 0, getDataCount());
            System.arraycopy(yErrorsPos, 0, yErrorsPosNew, 0, getDataCount());

            this.xValues = xValuesNew;
            this.yValues = yValuesNew;
            this.yErrorsNeg = yErrorsNegNew;
            this.yErrorsPos = yErrorsPosNew;
        }

        // N.B. getDataCount() should equal dataMaxIndex here
        System.arraycopy(xValues, 0, this.xValues, getDataCount(), newLength - getDataCount());
        System.arraycopy(yValues, 0, this.yValues, getDataCount(), newLength - getDataCount());
        System.arraycopy(yErrorsNeg, 0, this.yErrorsNeg, getDataCount(), newLength - getDataCount());
        System.arraycopy(yErrorsPos, 0, this.yErrorsPos, getDataCount(), newLength - getDataCount());

        dataMaxIndex = Math.max(0, dataMaxIndex + xValues.length);
        computeLimits();

        unlock();
        fireInvalidated();
        return this;
    }

    /**
     * <p>
     * Initialises the data set with specified data.
     * </p>
     * Note: The method copies values from specified double arrays.
     *
     * @param xValues X coordinates
     * @param yValues Y coordinates
     * @param copy true: makes an internal copy, false: use the pointer as is (saves memory allocation
     */
    public DoubleErrorDataSet set(final double[] xValues, final double[] yValues, final double[] yErrorsNeg,
            final double[] yErrorsPos, final boolean copy) {
        lock();
        AssertUtils.notNull("X coordinates", xValues);
        AssertUtils.notNull("Y coordinates", yValues);
        AssertUtils.notNull("Y error neg", yErrorsNeg);
        AssertUtils.notNull("Y error pos", yErrorsPos);
        final int errorMin = Math.min(yErrorsPos.length, yErrorsNeg.length);
        dataMaxIndex = Math.min(xValues.length, Math.min(yValues.length, errorMin));
        AssertUtils.equalDoubleArrays(xValues, yValues, dataMaxIndex);
        AssertUtils.equalDoubleArrays(xValues, yErrorsNeg, dataMaxIndex);
        AssertUtils.equalDoubleArrays(xValues, yErrorsPos, dataMaxIndex);

        if (!copy) {
            this.xValues = xValues;
            this.yValues = yValues;
            this.yErrorsNeg = yErrorsNeg;
            this.yErrorsPos = yErrorsPos;
            // dataMaxIndex = xValues.length;
            computeLimits();
            unlock();
            fireInvalidated();
            return this;
        }

        if (xValues.length == this.xValues.length) {
            System.arraycopy(xValues, 0, this.xValues, 0, getDataCount());
            System.arraycopy(yValues, 0, this.yValues, 0, getDataCount());
            System.arraycopy(yErrorsNeg, 0, this.yErrorsNeg, 0, getDataCount());
            System.arraycopy(yErrorsPos, 0, this.yErrorsPos, 0, getDataCount());
        } else {
            /*
             * copy into new arrays, forcing array length to be equal to the
             * xValues length
             */
            this.xValues = Arrays.copyOf(xValues, xValues.length);
            this.yValues = Arrays.copyOf(yValues, xValues.length);
            this.yErrorsNeg = Arrays.copyOf(yErrorsNeg, xValues.length);
            this.yErrorsPos = Arrays.copyOf(yErrorsPos, xValues.length);
        }
        // dataMaxIndex = xValues.length;
        computeLimits();

        unlock();
        fireInvalidated();
        return this;
    }

    /**
     * <p>
     * Initialises the data set with specified data.
     * </p>
     * Note: The method copies values from specified double arrays.
     *
     * @param xValues X coordinates
     * @param yValues Y coordinates
     */
    public DoubleErrorDataSet set(final double[] xValues, final double[] yValues, final double[] yErrorsNeg,
            final double[] yErrorsPos) {
        return set(xValues, yValues, yErrorsNeg, yErrorsPos, true);
    }

    public DoubleErrorDataSet set(final DataSet other) {
        return set(other, true);
    }

    public DoubleErrorDataSet set(final DataSet other, final boolean copy) {
        if (other instanceof DataSetError) {
            this.set(other.getXValues(), other.getYValues(), ((DataSetError) other).getYErrorsNegative(),
                    ((DataSetError) other).getYErrorsPositive(), copy);
        } else {
            final int count = other.getDataCount();
            this.set(other.getXValues(), other.getYValues(), new double[count], new double[count], copy);
        }
        return this;
    }

    /**
     * adds a custom new data label for a point The label can be used as a category name if CategoryStepsDefinition is
     * used or for annotations displayed for data points.
     *
     * @param index of the data point
     * @param label for the data point specified by the index
     * @return the previously set label or <code>null</code> if no label has been specified
     */
    public String addDataLabel(final int index, final String label) {
        return dataLabels.put(index, label);
    }

    /**
     * remove a custom data label for a point The label can be used as a category name if CategoryStepsDefinition is
     * used or for annotations displayed for data points.
     *
     * @param index of the data point
     * @param label for the data point specified by the index
     * @return the previously set label or <code>null</code> if no label has been specified
     */
    public String removeDataLabel(final int index) {
        return dataLabels.remove(index);
    }

    /**
     * Returns label of a data point specified by the index. The label can be used as a category name if
     * CategoryStepsDefinition is used or for annotations displayed for data points.
     *
     * @param index of the data label
     * @return data point label specified by the index or <code>null</code> if no label has been specified
     * @see CategoryStepsDefinition
     * @see #setCategory(boolean)
     * @see Scale#setCategory(DataSet)
     */
    @Override
    public String getDataLabel(final int index) {
        final String dataLabel = dataLabels.get(index);
        if (dataLabel != null) {
            return dataLabel;
        }

        return super.getDataLabel(index);
    }

    /**
     * A string representation of the CSS style associated with this specific {@code DataSet} data point. @see
     * #getStyle()
     *
     * @param index the index of the specific data point
     * @return itself (fluent interface)
     */
    public String addDataStyle(final int index, final String style) {
        return dataStyles.put(index, style);
    }

    /**
     * A string representation of the CSS style associated with this specific {@code DataSet} data point. @see
     * #getStyle()
     *
     * @param index the index of the specific data point
     * @return itself (fluent interface)
     */
    public String removeStyle(final int index) {
        return dataStyles.remove(index);
    }

    /**
     * A string representation of the CSS style associated with this specific {@code DataSet} data point. @see
     * #getStyle()
     *
     * @param index the index of the specific data point
     * @return user-specific data set style description (ie. may be set by user)
     */
    @Override
    public String getStyle(final int index) {
        return dataStyles.get(index);
    }
}
