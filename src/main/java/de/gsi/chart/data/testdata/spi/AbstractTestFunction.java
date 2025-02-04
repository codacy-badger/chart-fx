package de.gsi.chart.data.testdata.spi;

import de.gsi.chart.data.spi.AbstractErrorDataSet;
import de.gsi.chart.data.testdata.TestDataSet;

/**
 * abstract error data set for graphical testing purposes
 *
 * @author rstein
 */
public abstract class AbstractTestFunction<D extends AbstractTestFunction<D>> extends AbstractErrorDataSet<D>
        implements TestDataSet<D> {

    private double[] data;
    private final Object dataLock = new Object();

    public AbstractTestFunction(final String name, final int count) {
        super(name);
        // this part needs to be adjusted to you internal applications data
        // transfer/management likings
        data = generateY(count);
    }

    @Override
    public D update() {
        lock().setAutoNotifaction(false);
        synchronized (dataLock) {
            data = generateY(data.length);
        }
        computeLimits();
        return setAutoNotifaction(true).unlock().fireInvalidated();
    }

    @Override
    public int getDataCount() {
        synchronized (dataLock) {
            return data.length;
        }
    }

    @Override
    public double getX(final int index) {
        // returns the i-th index as horizontal X axis value
        return index;
    }

    @Override
    public double getY(final int index) {
        // include for example dimension sanity checks
        synchronized (dataLock) {
            if (index < 0 || index >= getDataCount()) {
                return getUndefValue();
            }

            return data[index];
        }
    }

    @Override
    public double getXErrorNegative(final int index) {
        return AbstractTestFunction.getXError();
    }

    @Override
    public double getXErrorPositive(final int index) {
        return AbstractTestFunction.getXError();
    }

    @Override
    public double getYErrorNegative(final int index) {
        return AbstractTestFunction.getYError();
    }

    @Override
    public double getYErrorPositive(final int index) {
        return AbstractTestFunction.getYError();
    }

    private static double getXError() {
        return 0.1;
    }

    private static double getYError() {
        return 0.1;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.XY_ASYMMETRIC;
    }

    @Override
    public String getStyle(final int index) {
        return null;
    }

    @Override
    public double[] generateX(final int count) {
        final double[] retVal = new double[count];
        for (int i = 0; i < count; i++) {
            retVal[i] = i;
        }
        return retVal;
    }

}
