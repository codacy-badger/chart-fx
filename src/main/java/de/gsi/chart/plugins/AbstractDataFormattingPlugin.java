package de.gsi.chart.plugins;

import de.gsi.chart.Chart;
import de.gsi.chart.XYChart;
import de.gsi.chart.axes.Axis;
import de.gsi.chart.axes.spi.Axes;
import de.gsi.chart.axes.spi.MetricPrefix;
import de.gsi.chart.data.spi.Tuple;
import de.gsi.chart.ui.geometry.Side;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Orientation;
import javafx.scene.chart.ValueAxis;
import javafx.util.StringConverter;

/**
 * An abstract plugin with associated formatters for X and Y value of the data. For details see
 * {@link #formatData(Axis, Tuple)}.
 *
 * @author Grzegorz Kruk
 * @author rstein
 */
public abstract class AbstractDataFormattingPlugin extends ChartPlugin {

    /**
     * Creates a new instance of AbstractDataIndicator.
     */
    protected AbstractDataFormattingPlugin() {
        chartProperty().addListener((obs, oldChart, newChart) -> {
            if (newChart != null) {
                if (!(newChart instanceof XYChart)) {
                    throw new IllegalArgumentException(
                            "cannot use chart of type '" + newChart.getClass().getSimpleName() + "' for this plug-ing");
                }

                defaultXValueFormatter = AbstractDataFormattingPlugin
                        .createDefaultFormatter(newChart.getFirstAxis(Orientation.HORIZONTAL));
                defaultYValueFormatter = AbstractDataFormattingPlugin
                        .createDefaultFormatter(newChart.getFirstAxis(Orientation.VERTICAL));
            }
        });
    }

    private final ObjectProperty<StringConverter<Number>> xValueFormatter = new SimpleObjectProperty<>(this,
            "xValueFormatter");

    /**
     * StringConverter used to format X values. If {@code null} a default will be used.
     *
     * @return the X value formatter property
     */
    public final ObjectProperty<StringConverter<Number>> xValueFormatterProperty() {
        return xValueFormatter;
    }

    /**
     * Returns the value of the {@link #xValueFormatterProperty()}.
     *
     * @return the X value formatter
     */
    public final StringConverter<Number> getXValueFormatter() {
        return xValueFormatterProperty().get();
    }

    /**
     * Sets the value of the {@link #xValueFormatterProperty()}.
     *
     * @param formatter the X value formatter
     */
    public final void setXValueFormatter(final StringConverter<Number> formatter) {
        xValueFormatterProperty().set(formatter);
    }

    private final ObjectProperty<StringConverter<Number>> yValueFormatter = new SimpleObjectProperty<>(this,
            "yValueFormatter");

    /**
     * StringConverter used to format Y values. If {@code null} a default will be used.
     *
     * @return the Y value formatter property
     */
    public final ObjectProperty<StringConverter<Number>> yValueFormatterProperty() {
        return yValueFormatter;
    }

    /**
     * Returns the value of the {@link #xValueFormatterProperty()}.
     *
     * @return the X value formatter
     */
    public final StringConverter<Number> getYValueFormatter() {
        return yValueFormatterProperty().get();
    }

    /**
     * Sets the value of the {@link #xValueFormatterProperty()}.
     *
     * @param formatter the X value formatter
     */
    public final void setYValueFormatter(final StringConverter<Number> formatter) {
        yValueFormatterProperty().set(formatter);
    }

    private StringConverter<Number> defaultXValueFormatter;
    private StringConverter<Number> defaultYValueFormatter;

    private static StringConverter<Number> createDefaultFormatter(final Axis axis) {
        // if (axis instanceof Axis) {
        // final de.gsi.chart.axes.spi.format.DefaultFormatter numberConverter
        // = new
        // de.gsi.chart.axes.spi.format.DefaultFormatter(
        // axis);
        // return numberConverter;
        // }
        // if (axis instanceof NumberAxis) { //TODO: re-enable
        // return (StringConverter<Number>) new
        // NumberAxis.DefaultFormatter((NumberAxis) axis);
        // }
        return new AbstractDataFormattingPlugin.DefaultFormatter<>();
    }

    /**
     * Formats the data to be displayed by this plugin. Uses the specified {@link #xValueFormatterProperty()} and
     * {@link #yValueFormatterProperty()} to obtain the corresponding formatters. If it is {@code null} and the axis is
     * a {@code ValueAxis} - the method will use {@link ValueAxis#getTickLabelFormatter() tick label formatter}. If this
     * one is also not initialized - a default formatter is used.
     * <p>
     * Can be overridden to modify formatting of the data.
     *
     * @param data the data point to be formatted
     * @return formatted data
     */
    protected String formatData(final Chart chart, final Tuple<Number, Number> data) {
        if (chart.getAxes().size() == 2) {
            // special case of only two axes
            final Axis xAxis = chart.getFirstAxis(Orientation.HORIZONTAL);
            final Axis yAxis = chart.getFirstAxis(Orientation.VERTICAL);
            return getXValueFormatter(xAxis).toString(data.getXValue()) + ", "
                    + getYValueFormatter(yAxis).toString(data.getYValue());
        }

        // any other axes
        final StringBuilder result = new StringBuilder();
        for (final Axis axis : chart.getAxes()) {
            final Side side = axis.getSide();
            if (side == null) {
                continue;
            }

            final String axisPrimaryLabel = axis.getLabel();
            String axisUnit = axis.getUnit();
            final String axisPrefix = MetricPrefix.getShortPrefix(axis.getUnitScaling());
            final boolean isAutoScaling = axis.getAutoUnitScaling();
            if (isAutoScaling) {
                if (axisUnit == null) {
                    axisUnit = " a.u.";
                }
            }

            result.append(axisPrimaryLabel).append(" = ");
            result.append(side.isHorizontal() ? getXValueFormatter(axis).toString(data.getXValue())
                    : getYValueFormatter(axis).toString(data.getYValue()));
            if (axisUnit != null) {
                result.append(axisPrimaryLabel).append(" [").append(axisPrefix).append(axisUnit).append("]");
            }
            result.append("\n");
        }

        return result.toString();
    }

    private StringConverter<Number> getXValueFormatter(final Axis xAxis) {
        return getValueFormatter(xAxis, getXValueFormatter(), defaultXValueFormatter);
    }

    private StringConverter<Number> getYValueFormatter(final Axis yAxis) {
        return getValueFormatter(yAxis, getYValueFormatter(), defaultYValueFormatter);
    }

    private StringConverter<Number> getValueFormatter(final Axis axis, final StringConverter<Number> formatter,
            final StringConverter<Number> defaultFormatter) {
        StringConverter<Number> valueFormatter = formatter;
        if (valueFormatter == null && Axes.isNumericAxis(axis)) {
            valueFormatter = Axes.toNumericAxis(axis).getTickLabelFormatter();
        }
        if (valueFormatter == null) {
            valueFormatter = defaultFormatter;
        }
        return valueFormatter;
    }

    private static class DefaultFormatter<T> extends StringConverter<T> {

        @Override
        public String toString(final T value) {
            return String.valueOf(value);
        }

        @Override
        public final T fromString(final String string) {
            throw new UnsupportedOperationException();
        }
    }
}
