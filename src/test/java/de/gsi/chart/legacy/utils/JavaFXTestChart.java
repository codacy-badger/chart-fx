package de.gsi.chart.legacy.utils;

import java.util.ArrayList;
import java.util.List;

import de.gsi.chart.data.testdata.spi.SineFunction;
import de.gsi.chart.legacy.RollingBufferLegacySample;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

public class JavaFXTestChart extends AbstractTestApplication implements ChartTestCase {
    protected int nSamples = MAX_DATA_POINTS_1HZ;
    protected SineFunction testFunction = new SineFunction("test", nSamples, true);
    NumberAxis xAxis = new NumberAxis();
    NumberAxis yAxis = new NumberAxis("irrelevant y-axis - JavaFX Chart", -1.1, +1.1, 0.2);
    protected final LineChart<Number, Number> lineChart;
    protected XYChart.Series<Number, Number> series1 = new XYChart.Series<>();

    @Override
    public void initChart() {
        test = new JavaFXTestChart();
    }

    public JavaFXTestChart() {

        xAxis.setLabel("x-axis (JavaFX Chart)");
        xAxis.setAutoRanging(false);
        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(-1.1);
        yAxis.setUpperBound(+1.1);

        // Create a LineChart
        lineChart = new LineChart<Number, Number>(xAxis, yAxis) {
            // Override to remove symbols on each data point
            @Override
            protected void dataItemAdded(final Series<Number, Number> series, final int itemIndex,
                    final Data<Number, Number> item) {
            }
        };
        lineChart.setAnimated(false);
        lineChart.setHorizontalGridLinesVisible(true);
        lineChart.setVerticalGridLinesVisible(true);
        lineChart.setCreateSymbols(false);
        lineChart.getXAxis().setAnimated(false);
        lineChart.getYAxis().setAnimated(false);
        lineChart.setHorizontalZeroLineVisible(false);
        lineChart.getStylesheets()
                .add(RollingBufferLegacySample.class.getResource("RollingBufferLegacy.css").toExternalForm());
        series1.setName("test data [a.u.]");
        lineChart.getData().add(series1);
        lineChart.setLegendVisible(false);

        setNumberOfSamples(MAX_DATA_POINTS_25HZ);
        updateDataSet();
    }

    @Override
    public Node getChart(int nSamples) {

        return lineChart;
    }

    @Override
    public void updateDataSet() {
        final double[] x = testFunction.generateX(nSamples);
        final double[] y = testFunction.generateY(nSamples);

        List<XYChart.Data<Number, Number>> newData = new ArrayList<>();
        for (int i = 0; i < nSamples; i++) {
            newData.add(new XYChart.Data<>(x[i], y[i]));
        }
        Platform.runLater(() -> series1.getData().setAll(newData));
    }

    @Override
    public void setNumberOfSamples(int nSamples) {
        this.nSamples = nSamples;
        testFunction = new SineFunction("test", nSamples, true);
        xAxis.setUpperBound(nSamples - 1.0);
        xAxis.setLowerBound(0);
        xAxis.setTickUnit(nSamples / 20.0);
        updateDataSet();
    }

    public static void main(final String[] args) {
        Application.launch(args);
    }
}
