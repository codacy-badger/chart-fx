package de.gsi.chart.axes.spi.format;

import java.text.DecimalFormat;
import java.text.ParseException;

import de.gsi.chart.axes.Axis;
import de.gsi.chart.axes.TickUnitSupplier;
import de.gsi.chart.utils.NumberFormatter;
import javafx.util.StringConverter;

/**
 * @author rstein
 */
public class DefaultLogFormatter extends AbstractFormatter {
    private static final TickUnitSupplier DEFAULT_TICK_UNIT_SUPPLIER = new DefaultTickUnitSupplier();
    private static final String FORMAT_SMALL_SCALE = "0.######";
    private static final String FORMAT_LARGE_SCALE = "0.0E0";
    public static final int DEFAULT_SMALL_LOG_AXIS = 4; // [orders of
                                                        // magnitude], e.g. '4'
                                                        // <-> [1,10000]
    private final DecimalFormat formatterSmall = new DecimalFormat(DefaultLogFormatter.FORMAT_SMALL_SCALE);
    private final DecimalFormat formatterLarge = new DecimalFormat(DefaultLogFormatter.FORMAT_LARGE_SCALE);
    private MyDecimalFormat formatter = new MyDecimalFormat(formatterSmall);

    /**
     * Construct a DefaultFormatter for the given NumberAxis
     *
     * @param axis
     *            The axis to format tick marks for
     */
    public DefaultLogFormatter(final Axis axis) {
        super(axis);

    }

    /**
     * Construct a DefaultFormatter for the given NumberAxis
     */
    public DefaultLogFormatter() {
        super();
        setTickUnitSupplier(DefaultLogFormatter.DEFAULT_TICK_UNIT_SUPPLIER);
    }

    @Override
    protected void rangeUpdated() {
        final boolean smallScale = getLogRange() <= DefaultLogFormatter.DEFAULT_SMALL_LOG_AXIS;
        final DecimalFormat oldFormatter = formatter.getFormatter();

        if (smallScale) {
            formatter.setFormatter(formatterSmall);
            if (!formatter.equals(oldFormatter)) {
                labelCache.clear();
            }
            return;
        }
        formatter.setFormatter(formatterLarge);
        if (!formatter.equals(oldFormatter)) {
            labelCache.clear();
        }
    }

    @Override
    protected double getLogRange() {
        return Math.abs(Math.log10(rangeMin)) + Math.abs(Math.log10(rangeMax));
    }

    @Override
    public String toString(final Number object) {
        return labelCache.get(formatter, object.doubleValue());
    }

    @Override
    public Number fromString(final String string) {
        return null;
    }

    private class MyDecimalFormat extends StringConverter<Number> implements NumberFormatter {
        DecimalFormat formatter;

        public MyDecimalFormat(final DecimalFormat formatter) {
            super();
            this.formatter = formatter;
        }

        private void setFormatter(final DecimalFormat formatter) {
            this.formatter = formatter;
        }

        private DecimalFormat getFormatter() {
            return this.formatter;
        }

        @Override
        public int getPrecision() {
            return 0;
        }

        @Override
        public NumberFormatter setPrecision(int precision) {
            return this;
        }

        @Override
        public boolean isExponentialForm() {
            return false;
        }

        @Override
        public NumberFormatter setExponentialForm(boolean state) {
            return this;
        }

        @Override
        public String toString(double val) {
            return formatter.format(val);
        }

        @Override
        public String toString(Number object) {
            return formatter.format(object.doubleValue());
        }

        @Override
        public Number fromString(String source) {
            try {
                return formatter.parse(source);
            } catch (ParseException e) {
                return Double.NaN;
            }
        }

    }

}
