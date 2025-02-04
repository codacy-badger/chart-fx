package de.gsi.chart.renderer.spi;

import de.gsi.chart.axes.Axis;
import de.gsi.chart.data.DataSet;
import de.gsi.chart.data.DataSetError;
import de.gsi.chart.data.spi.DoubleDataSet;
import de.gsi.chart.data.spi.DoubleErrorDataSet;
import de.gsi.chart.renderer.Renderer;
import de.gsi.chart.utils.ProcessingProfiler;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;

/**
 * @param <X>
 *            generic object type for x-Axis data type
 * @param <Y>
 *            generic object type for y-Axis data type
 * @author rstein TODO: generics and fluent design propagation also for derived
 *         interfaces & classes
 * @param <R>
 *            renderer generics
 */
public abstract class AbstractDataSetManagement<R extends Renderer> implements Renderer {
    // private final ObservableList<? extends DataSet> datasets =
    // FXCollections.observableArrayList();
    private final ObservableList<DataSet> datasets = FXCollections.observableArrayList();
    protected BooleanProperty showInLegend = new SimpleBooleanProperty(this, "showInLegend", true);

    @Override
    public ObservableList<DataSet> getDatasets() {
        return datasets;
    }

    private final ObservableList<Axis> axesList = FXCollections.observableArrayList();

    @Override
    public ObservableList<Axis> getAxes() {
        return axesList;
    }

    public Axis getFirstAxis(final Orientation orientation) {
        for (final Axis axis : getAxes()) {
            if (axis.getSide() == null) {
                continue;
            }
            switch (orientation) {
            case VERTICAL:
                if (axis.getSide().isVertical()) {
                    return axis;
                }
                break;
            case HORIZONTAL:
            default:
                if (axis.getSide().isHorizontal()) {
                    return axis;
                }
                break;
            }
        }
        return null;
    }

    // public ObservableList<? extends DataSet> getDatasets() {
    // return datasets;
    // }

    /**
     * @return the instance of this AbstractDataSetManagement.
     */
    protected abstract R getThis();

    /**
     * Sets whether DataSets attached to this renderer shall be shown in the
     * legend
     *
     * @return true (default) if data sets are supposed to be drawn
     */
    @Override
    public final BooleanProperty showInLegendProperty() {
        return showInLegend;
    }

    /**
     * Sets whether DataSets attached to this renderer shall be shown in the
     * legend
     *
     * @return true (default) if data sets are supposed to be drawn
     */
    @Override
    public boolean showInLegend() {
        return showInLegend.get();
    }

    /**
     * Sets whether DataSets attached to this renderer shall be shown in the
     * legend
     *
     * @param state
     *            true (default) if data sets are supposed to be drawn
     * @return the renderer class
     */
    @Override
    public R setShowInLegend(final boolean state) {
        showInLegend.set(state);
        return getThis();
    }

    @Override
    public ObservableList<DataSet> getDatasetsCopy() {
        return getDatasetsCopy(getDatasets());
    }

    protected ObservableList<DataSet> getDatasetsCopy(final ObservableList<DataSet> localDataSets) {
        final long start = ProcessingProfiler.getTimeStamp();
        final ObservableList<DataSet> dataSets = FXCollections.observableArrayList();
        for (final DataSet dataSet : localDataSets) {
            if (dataSet instanceof DataSetError) {
                final DataSetError dataSetError = (DataSetError) dataSet;
                dataSets.add(AbstractDataSetManagement.getErrorDataSetCopy(dataSetError));
            } else {
                dataSets.add(AbstractDataSetManagement.getDataSetCopy(dataSet));
            }
        }
        ProcessingProfiler.getTimeDiff(start);
        return dataSets;
    }

    protected static void copyMetaData(final DataSet from, final DataSet to) {
        to.setStyle(from.getStyle());
    }

    protected static final DoubleDataSet getDataSetCopy(final DataSet dataSet) {
        final int nLength = dataSet.getDataCount();
        final DoubleDataSet ret = new DoubleDataSet(dataSet.getName(), nLength);

        ret.setAutoNotifaction(false);
        dataSet.lock();

        if (dataSet instanceof DoubleDataSet) {
            final DoubleDataSet doubleDataSet = (DoubleDataSet) dataSet;
            // known data set implementation, may use faster array copy

            final double[] xValues = doubleDataSet.getXValues();
            final double[] yValues = doubleDataSet.getYValues();
            ret.set(xValues, yValues);

            ret.getDataLabelProperty().putAll(doubleDataSet.getDataLabelProperty());
            ret.getDataStyleProperty().putAll(doubleDataSet.getDataStyleProperty());
        } else {
            // generic implementation that works with all DataSetError
            // implementation
            for (int i = 0; i < nLength; i++) {
                ret.set(i, dataSet.getX(i), dataSet.getY(i));

                final String label = dataSet.getDataLabel(i);
                if (label != null) {
                    ret.getDataLabelProperty().put(i, label);
                }
                final String style = ret.getDataLabel(i);
                if (style != null) {
                    ret.getDataStyleProperty().put(i, style);
                }
            }
        }
        AbstractDataSetManagement.copyMetaData(dataSet, ret);
        dataSet.unlock();
        ret.fireInvalidated();
        ret.setAutoNotifaction(true);
        return ret;
    }

    protected static final DoubleErrorDataSet getErrorDataSetCopy(final DataSetError dataSet) {
        final int nLength = dataSet.getDataCount();
        final DoubleErrorDataSet ret = new DoubleErrorDataSet(dataSet.getName(), nLength);

        ret.setAutoNotifaction(false);
        dataSet.lock();
        if (dataSet instanceof DoubleErrorDataSet) {
            final DoubleErrorDataSet doubleErrorDataSet = (DoubleErrorDataSet) dataSet;
            // known data set implementation, may use faster array copy

            final double[] xValues = doubleErrorDataSet.getXValues();
            final double[] yValues = doubleErrorDataSet.getYValues();
            final double[] yErrorsNeg = doubleErrorDataSet.getYErrorsNegative();
            final double[] yErrorsPos = doubleErrorDataSet.getYErrorsPositive();
            ret.set(xValues, yValues, yErrorsNeg, yErrorsPos);

            ret.getDataLabelProperty().putAll(doubleErrorDataSet.getDataLabelProperty());
            ret.getDataStyleProperty().putAll(doubleErrorDataSet.getDataStyleProperty());
        } else {
            // generic implementation that works with all DataSetError
            // implementation
            for (int i = 0; i < nLength; i++) {
                ret.set(i, dataSet.getX(i), dataSet.getY(i), dataSet.getYErrorNegative(i),
                        dataSet.getYErrorPositive(i));
                final String label = ret.getDataLabel(i);
                if (label != null) {
                    ret.getDataLabelProperty().put(i, label);
                }
                final String style = ret.getDataLabel(i);
                if (style != null) {
                    ret.getDataStyleProperty().put(i, style);
                }
            }
        }
        AbstractDataSetManagement.copyMetaData(dataSet, ret);
        dataSet.unlock();
        ret.setAutoNotifaction(true);
        ret.fireInvalidated();

        return ret;
    }

}
