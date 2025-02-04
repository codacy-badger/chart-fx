package de.gsi.chart.demo;

import de.gsi.chart.XYChart;
import de.gsi.chart.axes.AxisLabelOverlapPolicy;
import de.gsi.chart.axes.spi.DefaultNumericAxis;
import de.gsi.chart.data.spi.DefaultErrorDataSet;
import de.gsi.chart.plugins.DataPointTooltip;
import de.gsi.chart.plugins.EditAxis;
import de.gsi.chart.plugins.Zoomer;
import de.gsi.chart.utils.ProcessingProfiler;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * @author rstein
 */
public class TimeAxisSample extends Application {
    private static final int N_SAMPLES = 10000; // default: 10000

    @Override
    public void start(final Stage primaryStage) {
        ProcessingProfiler.debugProperty().set(false);
        final BorderPane root = new BorderPane();
        final Scene scene = new Scene(root, 800, 600);

        final DefaultNumericAxis xAxis1 = new DefaultNumericAxis();
        xAxis1.setOverlapPolicy(AxisLabelOverlapPolicy.SKIP_ALT);
        final DefaultNumericAxis yAxis1 = new DefaultNumericAxis();

        final XYChart chart = new XYChart(xAxis1, yAxis1);
        chart.legendVisibleProperty().set(true);
        chart.getPlugins().add(new Zoomer());
        chart.getPlugins().add(new EditAxis());
        chart.getPlugins().add(new DataPointTooltip());
        // set them false to make the plot faster
        chart.setAnimated(false);

        xAxis1.setAutoRangeRounding(false);
        xAxis1.invertAxis(true);
        xAxis1.setTimeAxis(true);
        yAxis1.setAutoRangeRounding(true);

        final DefaultErrorDataSet dataSet = new DefaultErrorDataSet("TestData");

        generateData(dataSet);

        long startTime = ProcessingProfiler.getTimeStamp();
        chart.getDatasets().add(dataSet);
        ProcessingProfiler.getTimeDiff(startTime, "adding data to chart");

        startTime = ProcessingProfiler.getTimeStamp();
        root.setCenter(chart);
        ProcessingProfiler.getTimeDiff(startTime, "adding chart into StackPane");

        startTime = ProcessingProfiler.getTimeStamp();
        primaryStage.setTitle(this.getClass().getSimpleName());
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(evt -> System.exit(0));
        primaryStage.show();
        ProcessingProfiler.getTimeDiff(startTime, "for showing");

    }

    private void generateData(final DefaultErrorDataSet dataSet) {
        final long startTime = ProcessingProfiler.getTimeStamp();

        dataSet.setAutoNotifaction(false);
        dataSet.getDataProperty().clear();
        final double now = System.currentTimeMillis() / 1000.0 + 1; // N.B. '+1'
                                                                    // to check
                                                                    // for
                                                                    // resolution
        for (int n = 0; n < TimeAxisSample.N_SAMPLES; n++) {
            double t = now + n * 10;
            t *= +1;
            final double y = 100 * Math.cos(Math.PI * t * 0.0005) + 0 * 0.001 * (t - now) + 0 * 1e4;
            final double ex = 0.1;
            final double ey = 10;
            dataSet.add(t, y, ex, ey);
        }
        dataSet.setAutoNotifaction(true);

        Platform.runLater(dataSet::fireInvalidated);
        ProcessingProfiler.getTimeDiff(startTime, "adding data into DataSet");
    }

    /**
     * @param args
     *            the command line arguments
     */
    public static void main(final String[] args) {
        Application.launch(args);
    }
}