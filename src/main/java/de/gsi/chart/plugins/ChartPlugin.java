package de.gsi.chart.plugins;

import java.util.LinkedList;
import java.util.List;

import de.gsi.chart.Chart;
import de.gsi.chart.XYChart;
import de.gsi.chart.axes.Axis;
import de.gsi.chart.data.spi.Tuple;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.InputEvent;
import javafx.scene.input.MouseEvent;
import javafx.util.Pair;

/**
 * Represents an add-on to a Chart that can either annotate/decorate the chart or perform some interactions with it.
 * <p>
 * Concrete plugin implementations may add custom nodes to the chart via {@link #getChartChildren()} which returns an
 * observable and modifiable list of nodes that will be added to the {@code XYChartPane} on top of charts.
 * <p>
 * Plugins may also listen and react to events (e.g. mouse events) generated on the {@code XYChartPane} via
 * {@link #registerInputEventHandler(EventType, EventHandler)} method.
 * <p>
 * When plugin is added to the {@code XYChartPane}, it is bound to it via {@link #chartPaneProperty()}, therefore plugin
 * implementations may observe it and be notified when they are added or removed from the {@code XYChartPane}.
 *
 * @author Grzegorz Kruk
 * @author braeun - modified to be able to use XYChart class
 * @author rstein - modified to new Chart, XYChart API
 */
public abstract class ChartPlugin {

    private final ObservableList<Node> chartChildren = FXCollections.observableArrayList();
    private final List<Pair<EventType<? extends InputEvent>, EventHandler<? extends InputEvent>>> mouseEventHandlers = new LinkedList<>();

    /**
     * The associated {@link Chart}. Initialised when the plug-in is added to the Chart, set to {@code null} when
     * removed.
     */
    private final ObjectProperty<Chart> chart = new SimpleObjectProperty<>(this, "chart");

    /**
     * The associated {@link Chart}. Initialised when the plug-in is added to the Chart, set to {@code null} when
     * removed.
     *
     * @return the chart property
     */
    public final ObjectProperty<Chart> chartProperty() {
        return chart;
    }

    /**
     * Returns the value of the {@link #chartProperty()}.
     *
     * @return the associated Chart or {@code null}
     */
    public final Chart getChart() {
        return chartProperty().get();
    }

    /**
     * Called by the {@link Chart} when the plugin is added to it.
     *
     * @param chartPane the chart pane
     */
    public final void setChart(final Chart chart) {
        chartProperty().set(chart);
    }

    // whether to add (or not) the corresponding control buttons to chart tool
    // bar
    private final BooleanProperty addButtonsToToolBar = new SimpleBooleanProperty(this, "addButtonsToToolBar", true);

    /**
     * When {@code true} the corresponding control buttons are added to the chart tool bar
     *
     * @return the sliderVisible property
     * @see #getRangeSlider()
     */
    public final BooleanProperty addButtonsToToolBarProperty() {
        return addButtonsToToolBar;
    }

    /**
     * Sets the value of the {@link #addButtonsToToolBarProperty()}.
     *
     * @param state if {@code true} the corresponding control buttons are added to the chart tool bar
     */
    public final void setAddButtonsToToolBar(final boolean state) {
        addButtonsToToolBarProperty().set(state);
    }

    /**
     * Returns the value of the {@link #addButtonsToToolBarProperty()}.
     *
     * @return {@code true} the corresponding control buttons are added to the chart tool bar
     */
    public final boolean isAddButtonsToToolBar() {
        return addButtonsToToolBarProperty().get();
    }

    /**
     * Creates a new instance of the ChartPlugin.
     */
    protected ChartPlugin() {
        chartProperty().addListener((obs, oldChart, newChart) -> {
            if (oldChart != null) {
                removeEventHandlers(oldChart.getPlotArea());
                removeEventHandlers(oldChart.getPlotBackground());
            }
            if (newChart != null) {
                addEventHandlers(newChart.getPlotArea());
                addEventHandlers(newChart.getPlotBackground());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private <T extends InputEvent> void removeEventHandlers(final Node node) {
        if (node == null) {
            return;
        }
        for (final Pair<EventType<? extends InputEvent>, EventHandler<? extends InputEvent>> pair : mouseEventHandlers) {
            final EventType<T> type = (EventType<T>) pair.getKey();
            final EventHandler<T> handler = (EventHandler<T>) pair.getValue();
            node.removeEventHandler(type, handler);
            if (node.getScene() != null) {
                node.getScene().removeEventFilter(type, handler);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends InputEvent> void addEventHandlers(final Node node) {
        if (node == null) {
            return;
        }
        for (final Pair<EventType<? extends InputEvent>, EventHandler<? extends InputEvent>> pair : mouseEventHandlers) {
            final EventType<T> type = (EventType<T>) pair.getKey();
            final EventHandler<T> handler = (EventHandler<T>) pair.getValue();
            node.addEventHandler(type, handler);
            node.sceneProperty().addListener((ch, o, n) -> {
                if (o == n) {
                    return;
                }
                if (o != null) {
                    o.removeEventHandler(type, handler);
                }

                if (n != null) {
                    n.addEventHandler(type, handler);
                }
            });
        }
    }

    /**
     * Registers event handlers that should be added to the {@code XYChartPane} node when the plugin is added to the
     * pane and are removed once the plugin is removed from the pane.
     *
     * @param eventType the event type on which the handler should be called
     * @param handler the event handler to be added to the chart
     */
    protected final void registerInputEventHandler(final EventType<? extends InputEvent> eventType,
            final EventHandler<? extends InputEvent> handler) {
        mouseEventHandlers.add(new Pair<>(eventType, handler));
    }

    /**
     * Returns a list containing nodes that should be added to the list of child nodes of the associated XYChart's plot
     * area children.
     * <p>
     * The method should be used by concrete implementations to add nodes that should be added to the chart area.
     *
     * @return non-null list of nodes to be added to the chart's plot area
     */
    public final ObservableList<Node> getChartChildren() {
        return chartChildren;
    }

    /**
     * Optional method that allows the plug-in to react in case the size of the {@link XYChartPane} that it belongs to
     * has changed.
     */
    public void layoutChildren() { // #NOPMD
        // empty by default
    }

    /**
     * Converts mouse location within the scene to the location relative to the plot area.
     *
     * @param event mouse event
     * @return location within the plot area
     */
    protected final Point2D getLocationInPlotArea(final MouseEvent event) {
        final Point2D mouseLocationInScene = new Point2D(event.getSceneX(), event.getSceneY());
        final Chart chart = getChart();
        if (!(chart instanceof XYChart)) {
            return null;
        }
        final XYChart xyChart = (XYChart) chart;
        final double xInAxis = ((Node) xyChart.getXAxis()).sceneToLocal(mouseLocationInScene).getX();
        final double yInAxis = ((Node) xyChart.getYAxis()).sceneToLocal(mouseLocationInScene).getY();
        return new Point2D(xInAxis, yInAxis);
    }

    /**
     * Converts given point in data coordinates to a point in display coordinates.
     *
     * @param yAxis the corresponding y-axis (number axis)
     * @param x the X coordinate (can be generic non-number, ie. CategoryAxis)
     * @param y the Y coordinate on the yAxis (Number based)
     * @return corresponding display point within the plot area
     */
    protected final Point2D toDisplayPoint(final Axis yAxis, final double x, final double y) {
        return new Point2D(((XYChart) getChart()).getXAxis().getDisplayPosition(x), yAxis.getDisplayPosition(y));
    }

    /**
     * Converts given display point within the plot area coordinates to the corresponding data point within data
     * coordinates.
     *
     * @param yAxis the number-based y axis
     * @param displayPoint the display point to be converted
     * @return the data point
     */
    protected final Tuple<Number, Number> toDataPoint(final Axis yAxis, final Point2D displayPoint) {
        final Chart chart = getChart();
        if (!(chart instanceof XYChart)) {
            return null;
        }
        final XYChart xyChart = (XYChart) chart;
        return new Tuple<>(xyChart.getXAxis().getValueForDisplay(displayPoint.getX()),
                yAxis.getValueForDisplay(displayPoint.getY()));
        // TODO: rstein: check whether this is correct (bug potential for
        // casting error)
    }
}
