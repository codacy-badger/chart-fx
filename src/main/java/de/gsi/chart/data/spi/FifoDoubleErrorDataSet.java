package de.gsi.chart.data.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.gsi.chart.data.DataSetError;
import de.gsi.chart.utils.ProcessingProfiler;

/**
 * @author rstein
 */
public class FifoDoubleErrorDataSet extends AbstractErrorDataSet<DoubleErrorDataSet> implements DataSetError {

    private static final Logger LOGGER = LoggerFactory.getLogger(FifoDoubleErrorDataSet.class);
    protected LimitedQueue<DataBlob> data;
    protected double maxDistance = Double.MAX_VALUE;

    public class LimitedQueue<E> extends ArrayList<E> {

        private static final long serialVersionUID = -5751322669709687363L;
        private final int limit;

        public LimitedQueue(final int limit) {
            this.limit = limit;
            if (limit < 1) {
                throw new IllegalArgumentException("Queue limit must be greater than 0");
            }
        }

        @Override
        public boolean add(final E o) {
            final boolean r = super.add(o);
            if (size() > limit) {
                super.remove(0);
            }
            return r;
        }

    }

    protected class DataBlob extends DoublePointError {

        String style;
        String tag;

        DataBlob(final double x, final double y, final double errorX, final double errorY, final String tag,
                final String style) {
            super(x, y, errorX, errorY);
            this.tag = tag;
            this.style = style;
        }

        DataBlob(final double x, final double y, final double errorX, final double errorY) {
            this(x, y, errorX, errorY, null, null);
        }

        public String getStyle() {
            return style;
        }

        public String getDataLabel() {
            return tag;
        }
    }

    /**
     * Creates a new instance of <code>FifoDoubleErrorDataSet</code>.
     *
     * @param name name of this DataSet.
     * @param initalSize maximum circular buffer capacity
     * @throws IllegalArgumentException if <code>name</code> is <code>null</code>
     */
    public FifoDoubleErrorDataSet(final String name, final int initalSize) {
        this(name, initalSize, Double.MAX_VALUE);

    }

    /**
     * Creates a new instance of <code>FifoDoubleErrorDataSet</code>.
     *
     * @param name name of this DataSet.
     * @param initalSize maximum circular buffer capacity
     * @throws IllegalArgumentException if <code>name</code> is <code>null</code>
     */
    public FifoDoubleErrorDataSet(final String name, final int initalSize, final double maxDistance) {
        super(name);
        if (initalSize <= 0) {
            throw new IllegalArgumentException("negative or zero initalSize = " + initalSize);
        }
        if (maxDistance <= 0) {
            throw new IllegalArgumentException("negative or zero maxDistance = " + maxDistance);
        }
        this.maxDistance = maxDistance;
        data = new LimitedQueue<>(initalSize);
    }

    public double getMaxDistance() {
        return maxDistance;
    }

    public void setMaxDistance(final double maxDistance) {
        this.maxDistance = maxDistance;
    }

    @Override
    public double getX(final int index) {
        return data.get(index).getX();
    }

    @Override
    public double getY(final int index) {
        return data.get(index).getY();
    }

    @Override
    public String getStyle(final int index) {
        return data.get(index).getStyle();
    }

    @Override
    public double getXErrorNegative(final int index) {
        return data.get(index).getErrorX();
    }

    @Override
    public double getXErrorPositive(final int index) {
        return data.get(index).getErrorX();
    }

    @Override
    public double getYErrorNegative(final int index) {
        return data.get(index).getErrorY();
    }

    @Override
    public double getYErrorPositive(final int index) {
        return data.get(index).getErrorY();
    }

    @Override
    public String getDataLabel(final int index) {
        return data.get(index).getDataLabel();
    }

    @Override
    public int getDataCount() {
        return data.size();
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
    public FifoDoubleErrorDataSet add(final double x, final double y, final double yErrorNeg, final double yErrorPos) {
        return add(x, y, yErrorNeg, yErrorPos, null);
    }

    /**
     * Add point to the DoublePoints object
     *
     * @param x the new x coordinate
     * @param y the new y coordinate
     * @param yErrorNeg the +dy error
     * @param yErrorPos the -dy error
     * @param tag the data tag
     * @return itself
     */
    public FifoDoubleErrorDataSet add(final double x, final double y, final double yErrorNeg, final double yErrorPos,
            final String tag) {
        return add(x, y, yErrorNeg, yErrorPos, tag, null);
    }

    /**
     * Add point to the DoublePoints object
     *
     * @param x the new x coordinate
     * @param y the new y coordinate
     * @param yErrorNeg the +dy error
     * @param yErrorPos the -dy error
     * @param tag the data tag
     * @param style the data point style
     * @return itself
     */
    public FifoDoubleErrorDataSet add(final double x, final double y, final double yErrorNeg, final double yErrorPos,
            final String tag, final String style) {
        lock();
        final boolean notifyState = isAutoNotification();

        setAutoNotifaction(false);
        data.add(new DataBlob(x, y, yErrorNeg, yErrorPos, tag, style));
        // remove old fields
        expire(x);

        setAutoNotifaction(notifyState);
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
     * @param xValues the new x coordinates
     * @param yValues the new y coordinates
     * @param yErrorsNeg the +dy errors
     * @param yErrorsPos the -dy errors
     * @return itself
     */
    public FifoDoubleErrorDataSet add(final double[] xValues, final double[] yValues, final double[] yErrorsNeg,
            final double[] yErrorsPos) {
        lock();
        final boolean notifyState = isAutoNotification();

        setAutoNotifaction(!notifyState);
        for (int i = 0; i < xValues.length; i++) {
            this.add(xValues[i], yValues[i], yErrorsNeg[i], yErrorsPos[i]);
        }
        setAutoNotifaction(notifyState);
        unlock();
        fireInvalidated();
        return this;
    }

    /**
     * expire data points that are older than now minus length of the buffer, notifies a 'fireInvalidated()' in case
     * data has been removed
     *
     * @param now the newest time-stamp
     * @return number of items that have been removed
     */
    public int expire(final double now) {
        lock();
        xRange.empty();
        yRange.empty();
        final List<DataBlob> toRemoveList = new ArrayList<>();
        // for (final DataBlob blob : (LimitedQueue<DataBlob>) data.clone()) {
        for (final DataBlob blob : data) {
            final double x = blob.getX();
            final double y = blob.getX();

            if (Double.isFinite(x) && Double.isFinite(y)) {
                if (Math.abs(now - x) > maxDistance) {
                    // data.remove(blob);
                    toRemoveList.add(blob);
                } else {

                    xRange.add(x + blob.getErrorX());
                    xRange.add(x - blob.getErrorX());
                    xRange.add(y + blob.getErrorY());
                    xRange.add(y - blob.getErrorY());
                }
            } else {
                // data.remove(blob);
                toRemoveList.add(blob);
            }
        }

        data.removeAll(toRemoveList);
        computeLimits();
        // computeLimits(); // N.B. already computed above
        final int dataPointsToRemove = toRemoveList.size();
        unlock();
        if (dataPointsToRemove != 0) {
            fireInvalidated();
        }
        return dataPointsToRemove;
    }

    public void reset() {
        data.clear();
        fireInvalidated();
    }

    /**
     * meant for testing/illustrating usage
     *
     * @param args the command line arguments
     */
    public static void main(final String[] args) {
        final int bufferLength = 10;
        final int fillBufferLength = 35;
        final FifoDoubleErrorDataSet buffer1 = new FifoDoubleErrorDataSet("test", bufferLength, 10);
        final CircularDoubleErrorDataSet buffer2 = new CircularDoubleErrorDataSet("test", bufferLength);

        for (int i = 0; i < fillBufferLength; i++) {

            final double delta = i >= 13 ? 5 : 0;
            final double t = i + delta;
            if (i == 13) {
                System.err.println("jump in time by +" + delta);
            }
            buffer1.add(t, i, 0, 0);
            buffer2.add(t, i, 0, 0);
            final int max1 = Math.max(0, buffer1.getDataCount() - 1);
            final int max2 = Math.max(0, buffer2.getDataCount() - 1);
            final String msg = String.format("%2d - [ %2d vs. %2d , %2d vs. %2d] - length = %2d vs %2d", i,
                    (int) buffer1.getX(0), (int) buffer2.getX(0), (int) buffer1.getX(max1), (int) buffer2.getX(max2),
                    max1, max2);
            System.err.println(msg);
        }

        // some simple performance tests
        ProcessingProfiler.debugProperty().set(true);
        final long start = ProcessingProfiler.getTimeStamp();
        final FifoDoubleErrorDataSet buffer3 = new FifoDoubleErrorDataSet("test", 1000, 100);
        for (int i = 0; i < 10000; i++) {
            buffer3.add(i, 1.0, 0.0, 0.0);
        }
        ProcessingProfiler.getTimeDiff(start, "init and write 10k times");

        final Timer timer = new Timer();

        timer.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                System.gc();
            }
        }, 5, 2000);
        while (true) {
            buffer3.add(System.currentTimeMillis() * 1e-3, 1.0, 0.0, 0.0);
        }
    }
}
