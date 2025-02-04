package de.gsi.math.demo.utils;

import java.util.ArrayList;
import java.util.List;

import de.gsi.chart.XYChart;
import de.gsi.chart.axes.spi.DefaultNumericAxis;
import de.gsi.chart.plugins.DataPointTooltip;
import de.gsi.chart.plugins.EditAxis;
import de.gsi.chart.plugins.Panner;
import de.gsi.chart.plugins.ParameterMeasurements;
import de.gsi.chart.plugins.TableViewer;
import de.gsi.chart.plugins.Zoomer;
import de.gsi.chart.renderer.ErrorStyle;
import de.gsi.chart.renderer.LineStyle;
import de.gsi.chart.renderer.spi.ErrorDataSetRenderer;
import de.gsi.chart.ui.geometry.Side;
import javafx.scene.layout.Priority;
import jfxtras.scene.layout.HBox;
import jfxtras.scene.layout.VBox;

/**
 * Short hand extension/configuration of the standard XYChart functionalities to
 * make the samples more readable
 * 
 * @author rstein
 */
public class DemoChart extends XYChart {
    private List<DefaultNumericAxis> yAxes = new ArrayList<>();
    private List<ErrorDataSetRenderer> renderer = new ArrayList<>();

    public DemoChart() {
        this(1);
    }

    public DemoChart(final int nAxes) {
        super(new DefaultNumericAxis("x-axis"), new DefaultNumericAxis("y-axis"));

        if (nAxes <= 0) {
            throw new IllegalArgumentException("nAxes= " + nAxes + " must be >=1");
        }

        ErrorDataSetRenderer defaultRenderer = (ErrorDataSetRenderer) getRenderers().get(0);
        defaultRenderer.setPolyLineStyle(LineStyle.NORMAL);
        defaultRenderer.setErrorType(ErrorStyle.ERRORCOMBO);
        renderer.add(defaultRenderer);

        getYAxis().setAutoRangePadding(0.05);
        yAxes.add(getYAxis());

        for (int i = 1; i < nAxes; i++) {
            DefaultNumericAxis yAxis = new DefaultNumericAxis("y-axis" + i);
            yAxis.setAutoRangePadding(0.05);
            yAxis.setSide(Side.RIGHT);
            yAxes.add(yAxis);

            ErrorDataSetRenderer newRenderer = new ErrorDataSetRenderer();
            newRenderer.getAxes().addAll(getXAxis(), yAxis);
            getRenderers().add(newRenderer);
            renderer.add(newRenderer);
        }

        getPlugins().add(new ParameterMeasurements());
        getPlugins().add(new Zoomer());
        getPlugins().add(new Panner());
        getPlugins().add(new TableViewer());
        getPlugins().add(new EditAxis());
        getPlugins().add(new DataPointTooltip());

        VBox.setVgrow(this, Priority.ALWAYS);
        HBox.setHgrow(this, Priority.ALWAYS);
    }

    @Override
    public DefaultNumericAxis getXAxis() {
        return (DefaultNumericAxis) super.getXAxis();
    }

    @Override
    public DefaultNumericAxis getYAxis() {
        return (DefaultNumericAxis) super.getYAxis();
    }

    public DefaultNumericAxis getYAxis(final int index) {
        return yAxes.get(index);
    }

    public ErrorDataSetRenderer getRenderer() {
        return renderer.get(0);
    }

    public ErrorDataSetRenderer getRenderer(final int index) {
        return renderer.get(index);
    }

}
