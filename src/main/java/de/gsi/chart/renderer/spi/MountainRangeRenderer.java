package de.gsi.chart.renderer.spi;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import de.gsi.chart.Chart;
import de.gsi.chart.XYChart;
import de.gsi.chart.axes.Axis;
import de.gsi.chart.data.DataSet;
import de.gsi.chart.data.DataSet3D;
import de.gsi.chart.data.DataSetError;
import de.gsi.chart.renderer.ErrorStyle;
import de.gsi.chart.renderer.Renderer;
import de.gsi.chart.utils.AssertUtils;
import de.gsi.chart.utils.ProcessingProfiler;
import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.canvas.GraphicsContext;

/**
 * @author rstein
 */
public class MountainRangeRenderer extends ErrorDataSetRenderer implements Renderer {

    //private static final Logger LOGGER = LoggerFactory.getLogger(MountainRangeRenderer.class);
    protected DoubleProperty mountainRangeOffset = new SimpleDoubleProperty(this, "mountainRangeOffset", 0.5);
    private final ObservableList<ErrorDataSetRenderer> renderers = FXCollections.observableArrayList();
    final ObservableList<DataSet> empty = FXCollections.observableArrayList();
    private final WeakHashMap<Double, Integer> xWeakIndexMap = new WeakHashMap<>();
    private final WeakHashMap<Double, Integer> yWeakIndexMap = new WeakHashMap<>();
    private double zRangeMin = 0.0;
    private double zRangeMax = 0.0;
    private double mountainRaingeExtra = 0.0;

    public MountainRangeRenderer(final double mountainRangeOffset) {
        this();
        setMountainRangeOffset(mountainRangeOffset);
    }

    public MountainRangeRenderer() {
        super();
        // renderers.add(this);
        setDrawMarker(false);
        setDrawBars(false);
        setErrorType(ErrorStyle.NONE);
        xWeakIndexMap.clear();
        yWeakIndexMap.clear();
    }

    @Override
    public void render(final GraphicsContext gc, final Chart chart, final int dataSetOffset,
            final ObservableList<DataSet> datasets) {
        final long start = ProcessingProfiler.getTimeStamp();
        if (!(chart instanceof XYChart)) {
            throw new InvalidParameterException(
                    "must be derivative of XYChart for renderer - " + this.getClass().getSimpleName());
        }
        final XYChart xyChart = (XYChart) chart;

        if (!(xyChart.getYAxis() instanceof Axis)) {
            throw new InvalidParameterException("y Axis not a Axis derivative, yAxis = " + xyChart.getYAxis());
        }
        final Axis yAxis = xyChart.getYAxis();

        // make local copy and add renderer specific data sets
        final List<DataSet> localDataSetList = new ArrayList<>(datasets);
        localDataSetList.addAll(getDatasets());

        // render in reverse order
        for (int dataSetIndex = localDataSetList.size() - 1; dataSetIndex >= 0; dataSetIndex--) {
            final DataSet dataSet = localDataSetList.get(dataSetIndex);

            // detect and fish-out DataSet3D, ignore others
            if (dataSet instanceof DataSet3D) {
                dataSet.lock();
                final DataSet3D mData = (DataSet3D) dataSet;
                xWeakIndexMap.clear();
                yWeakIndexMap.clear();
                yAxis.setAutoGrowRanging(true);
                zRangeMin = mData.getZRange().getMin();
                zRangeMax = mData.getZRange().getMax();
                mountainRaingeExtra = MountainRangeRenderer.this.getMountainRangeOffset();
                yAxis.setLowerBound(zRangeMin);
                yAxis.setUpperBound(zRangeMax * (1.0 + mountainRaingeExtra));
                yAxis.forceRedraw();

                final int yCountMax = mData.getYDataCount();
                checkAndRecreateRenderer(yCountMax);

                for (int index = yCountMax - 1; index > 0; index--) {
                    final MountainRangeRenderer.Demux3dTo2dDataSet dataSet2D = new Demux3dTo2dDataSet(mData, index);
                    renderers.get(index).getDatasets().setAll(dataSet2D);
                    renderers.get(index).render(gc, chart, 0, empty);
                }

                dataSet.unlock();
            }

        }

        // super.render(gc, chart, empty);
        ProcessingProfiler.getTimeDiff(start);
    }

    private void checkAndRecreateRenderer(final int nRenderer) {
        if (renderers.size() == nRenderer) {
            // all OK
            return;
        }

        if (nRenderer > renderers.size()) {
            for (int i = renderers.size(); i < nRenderer; i++) {
                final ErrorDataSetRenderer newRenderer = new ErrorDataSetRenderer();
                newRenderer.bind(this);
                // do not show history sets in legend (single exception to
                // binding)
                newRenderer.showInLegendProperty().unbind();
                newRenderer.setShowInLegend(false);
                renderers.add(newRenderer);
            }
            return;
        }

        // require less renderer -> remove first until we have the right number
        // needed
        while (nRenderer < renderers.size()) {
            renderers.remove(0);
        }
    }

    /**
     * Returns the <code>mountainRangeOffset</code>.
     *
     * @return the <code>mountainRangeOffset</code>, i.e. vertical offset between subsequent data sets
     */
    public final double getMountainRangeOffset() {
        return mountainRangeOffset.get();
    }

    /**
     * Sets the <code>dashSize</code> to the specified value. The dash is the horizontal line painted at the ends of the
     * vertical line. It is not painted if set to 0.
     *
     * @param mountainRangeOffset t<code>mountainRangeOffset</code>, i.e. vertical offset between subsequent data sets
     * @return itself (fluent design)
     */
    public final MountainRangeRenderer setMountainRangeOffset(final double mountainRangeOffset) {
        AssertUtils.gtEqThanZero("mountainRangeOffset", mountainRangeOffset);
        this.mountainRangeOffset.setValue(mountainRangeOffset);
        return this;
    }

    public final DoubleProperty mountainRangeOffsetProperty() {
        return mountainRangeOffset;
    }

    private class Demux3dTo2dDataSet implements DataSetError {

        private final DataSet3D dataSet;
        private final int yIndex;
        private final int yMax;
        private double yShift;

        public Demux3dTo2dDataSet(final DataSet3D sourceDataSet, final int selectedYIndex) {
            super();
            dataSet = sourceDataSet;
            yIndex = selectedYIndex;
            yMax = dataSet.getYDataCount();
            yShift = 0.0; // just temporarily, will be recomputed
            getYMax(); // #NOPMD locally needed to initialise, cannot be
                       // overwritten by user
        }

        @Override
        public void addListener(final InvalidationListener listener) {
            // null implementation, N.B. not needed in this local context
        }

        @Override
        public void removeListener(final InvalidationListener listener) {
            // null implementation, N.B. not needed in this local context
        }

        @Override
        public String getName() {
            return dataSet.getName() + ":slice#" + yIndex;
        }

        @Override
        public DataSet lock() {
            // empty implementation since the superordinate DataSet3D lock is
            // being held/protecting this data set
            return this;
        }

        @Override
        public DataSet unlock() {
            // empty implementation since the superordinate DataSet3D lock is
            // being held/protecting this data set
            return this;
        }

        @Override
        public DataSet setAutoNotifaction(final boolean flag) {
            return dataSet.setAutoNotifaction(flag);
        }

        @Override
        public boolean isAutoNotification() {
            return dataSet.isAutoNotification();
        }

        @Override
        public int getDataCount() {
            return dataSet.getXDataCount();
        }

        @Override
        public int getDataCount(final double xmin, final double xmax) {
            return dataSet.getDataCount(xmin, xmax);
        }

        @Override
        public double getX(final int i) {
            return dataSet.getX(i);
        }

        @Override
        public double getY(final int i) {
            return dataSet.getZ(i, yIndex) + yShift;
        }

        @Override
        public Double getUndefValue() {
            return dataSet.getUndefValue();
        }

        @Override
        public int getXIndex(final double x) {
            // added computation of hash since this is recomputed quite often
            // (and the same) for each slice
            Integer ret = xWeakIndexMap.get(x);
            if (ret == null) {
                ret = dataSet.getXIndex(x);
                xWeakIndexMap.put(x, ret);
            }
            return ret;
        }

        @Override
        public int getYIndex(final double y) {
            // added computation of hash since this is recomputed quite often
            // (and the same) for each slice
            Integer ret = yWeakIndexMap.get(y);
            if (ret == null) {
                ret = dataSet.getYIndex(y);
                yWeakIndexMap.put(y, ret);
            }
            return ret;
        }

        @Override
        public double getXMin() {
            return dataSet.getXMin();
        }

        @Override
        public double getXMax() {
            return dataSet.getXMax();
        }

        @Override
        public double getYMin() {
            return dataSet.getZRange().getMin();
        }

        @Override
        public double getYMax() {
            yShift = mountainRaingeExtra * zRangeMax * yIndex / yMax;
            return zRangeMax * (1 + mountainRaingeExtra);
        }

        @Override
        public String getDataLabel(final int index) {
            return dataSet.getDataLabel(index);
        }

        @Override
        public StringProperty styleClassProperty() {
            return dataSet.styleClassProperty();
        }

        @Override
        public String getStyle() {
            return dataSet.getStyle();
        }

        @Override
        public DataSet setStyle(final String style) {
            return dataSet.setStyle(style);
        }

        @Override
        public StringProperty styleProperty() {
            return dataSet.styleProperty();
        }

        @Override
        public ErrorType getErrorType() {
            return ErrorType.Y;
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
            return 0;
        }

        @Override
        public double getYErrorPositive(final int index) {
            return 0;
        }

        @Override
        public String getStyle(final int index) {
            return null;
        }

    }

}
