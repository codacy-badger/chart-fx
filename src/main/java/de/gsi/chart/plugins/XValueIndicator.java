package de.gsi.chart.plugins;

import de.gsi.chart.axes.Axis;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.input.MouseEvent;

/**
 * A vertical line drawn on the plot area, indicating specified X value, with an optional {@link #textProperty() text
 * label} describing the value.
 * <p>
 * Style Classes (from least to most specific):
 * <ul>
 * <li><b>Label:</b> {@code value-indicator-label, x-value-indicator-label, x-value-indicator-label[index]}</li>
 * <li><b>Line:</b> {@code value-indicator-line, x-value-indicator-line, x-value-indicator-line[index]}</li>
 * </ul>
 * where {@code [index]} corresponds to the index (zero based) of this indicator instance added to the
 * {@code XYChartPane}. For example class {@code x-value-indicator-label1} can be used to style label of the second
 * instance of this indicator added to the chart pane.
 * </p>
 *
 * @author mhrabia
 */
public class XValueIndicator extends AbstractSingleValueIndicator {

    /**
     * Creates a new instance of the indicator.
     *
     * @param axis the axis this indicator is associated with
     * @param value a X value to be indicated
     */
    public XValueIndicator(final Axis axis, final double value) {
        this(axis, value, null);
    }

    /**
     * Creates a new instance of the indicator.
     *
     * @param axis the axis this indicator is associated with
     * @param value a X value to be indicated
     * @param text the text to be shown by the label. Value of {@link #textProperty()}.
     */
    public XValueIndicator(final Axis axis, final double value, final String text) {
        super(axis, value, text);
        triangle.getPoints().setAll(0.0, 0.0, -8.0, -8.0, 8.0, -8.0);
        setLabelPosition(0.04);

        pickLine.setOnMouseDragged(this::handleDragMouseEvent);
        triangle.setOnMouseDragged(this::handleDragMouseEvent);
        label.setOnMouseDragged(this::handleDragMouseEvent);
    }

    protected void handleDragMouseEvent(final MouseEvent mouseEvent) {
        Point2D c = getChart().getPlotArea().sceneToLocal(mouseEvent.getSceneX(), mouseEvent.getSceneY());
        final double xPosData = getNumericAxis().getValueForDisplay(c.getX() + dragDelta.x);
        if (getNumericAxis().isValueOnAxis(xPosData)) {
            valueProperty().set(xPosData);
        }

        mouseEvent.consume();
        layoutChildren();
    }

    @Override
    void updateStyleClass() {
        setStyleClasses(label, "x-", AbstractSingleValueIndicator.STYLE_CLASS_LABEL);
        setStyleClasses(line, "x-", AbstractSingleValueIndicator.STYLE_CLASS_LINE);
        setStyleClasses(triangle, "x-", AbstractSingleValueIndicator.STYLE_CLASS_MARKER);
    }

    @Override
    public void layoutChildren() {
        if (getChart() == null) {
            return;
        }

        final Bounds plotAreaBounds = getChart().getCanvas().getBoundsInLocal();
        final double minX = plotAreaBounds.getMinX();
        final double maxX = plotAreaBounds.getMaxX();
        final double minY = plotAreaBounds.getMinY();
        final double maxY = plotAreaBounds.getMaxY();
        final double xPos = minX + getChart().getFirstAxis(Orientation.HORIZONTAL).getDisplayPosition(getValue());

        if (xPos < minX || xPos > maxX) {
            getChartChildren().clear();
        } else {
            layoutLine(xPos, minY, xPos, maxY);
            layoutMarker(xPos, minY + 1.5 * AbstractSingleValueIndicator.TRIANGLE_HALF_WIDTH, xPos, maxY);
            layoutLabel(new BoundingBox(xPos, minY, 0, maxY - minY), AbstractSingleValueIndicator.MIDDLE_POSITION,
                    getLabelPosition());
        }

    }
}
