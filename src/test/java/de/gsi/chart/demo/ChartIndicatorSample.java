package de.gsi.chart.demo;

import java.time.ZoneOffset;
import java.util.Timer;
import java.util.TimerTask;

import de.gsi.chart.XYChart;
import de.gsi.chart.axes.spi.DefaultNumericAxis;
import de.gsi.chart.axes.spi.format.DefaultTimeFormatter;
import de.gsi.chart.data.spi.FifoDoubleErrorDataSet;
import de.gsi.chart.data.testdata.spi.RandomDataGenerator;
import de.gsi.chart.plugins.DataPointTooltip;
import de.gsi.chart.plugins.EditAxis;
import de.gsi.chart.plugins.Panner;
import de.gsi.chart.plugins.ParameterMeasurements;
import de.gsi.chart.plugins.XRangeIndicator;
import de.gsi.chart.plugins.XValueIndicator;
import de.gsi.chart.plugins.YRangeIndicator;
import de.gsi.chart.plugins.YValueIndicator;
import de.gsi.chart.plugins.Zoomer;
import de.gsi.chart.renderer.ErrorStyle;
import de.gsi.chart.renderer.datareduction.DefaultDataReducer;
import de.gsi.chart.renderer.spi.ErrorDataSetRenderer;
import de.gsi.chart.ui.geometry.Side;
import de.gsi.chart.utils.ProcessingProfiler;
import de.gsi.chart.utils.SimplePerformanceMeter;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class ChartIndicatorSample extends Application {
    private static final int DEBUG_UPDATE_RATE = 1000;
    private static final int MIN_PIXEL_DISTANCE = 0; // 0: just drop points that
                                                     // are drawn on the same
                                                     // pixel
    private static final int N_SAMPLES = 3000; // default: 1000000
    private static final int UPDATE_DELAY = 1000; // [ms]
    private static final int UPDATE_PERIOD = 40; // [ms]
    private static final int BUFFER_CAPACITY = 750; // 750 samples @ 25 Hz <->
                                                    // 30 s
    private static final double MAX_DISTANCE = ChartIndicatorSample.BUFFER_CAPACITY * ChartIndicatorSample.UPDATE_PERIOD
            * 1e-3 * 0.90;

    public final FifoDoubleErrorDataSet rollingBufferDipoleCurrent = new FifoDoubleErrorDataSet("dipole current [A]",
            ChartIndicatorSample.BUFFER_CAPACITY, ChartIndicatorSample.MAX_DISTANCE);
    public final FifoDoubleErrorDataSet rollingBufferBeamIntensity = new FifoDoubleErrorDataSet("beam intensity [ppp]",
            ChartIndicatorSample.BUFFER_CAPACITY, ChartIndicatorSample.MAX_DISTANCE);
    final FifoDoubleErrorDataSet rollingSine = new FifoDoubleErrorDataSet("sine [A]",
            ChartIndicatorSample.BUFFER_CAPACITY, ChartIndicatorSample.MAX_DISTANCE);
    private final ErrorDataSetRenderer beamIntensityRenderer = new ErrorDataSetRenderer();
    private final ErrorDataSetRenderer dipoleCurrentRenderer = new ErrorDataSetRenderer();

    private Timer timer;
    private long startTime;

    protected void initErrorDataSetRenderer(final ErrorDataSetRenderer eRenderer) {
        eRenderer.setErrorType(ErrorStyle.ERRORSURFACE);
        eRenderer.setDashSize(ChartIndicatorSample.MIN_PIXEL_DISTANCE); // plot
                                                                        // pixel-to-pixel
                                                                        // distance
        eRenderer.setDrawMarker(false);
        final DefaultDataReducer reductionAlgorithm = (DefaultDataReducer) eRenderer.getRendererDataReducer();
        reductionAlgorithm.setMinPointPixelDistance(ChartIndicatorSample.MIN_PIXEL_DISTANCE);
    }

    @Override
    public void start(final Stage primaryStage) {
        ProcessingProfiler.debugProperty().set(false);

        final BorderPane root = new BorderPane();
        final Scene scene = new Scene(root, 1800, 400);
        root.setCenter(initComponents(scene));

        startTime = ProcessingProfiler.getTimeStamp();
        primaryStage.setTitle(this.getClass().getSimpleName());
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(evt -> System.exit(0));
        primaryStage.show();
        ProcessingProfiler.getTimeDiff(startTime, "for showing");

    }

    public BorderPane initComponents(final Scene scene) {
        final BorderPane root = new BorderPane();
        generateData();
        initErrorDataSetRenderer(beamIntensityRenderer);
        initErrorDataSetRenderer(dipoleCurrentRenderer);

        final DefaultNumericAxis xAxis1 = new DefaultNumericAxis();
        final DefaultNumericAxis xAxis2 = new DefaultNumericAxis();
        final DefaultNumericAxis yAxis1 = new DefaultNumericAxis("beam intensity", "ppp");
        final DefaultNumericAxis yAxis2 = new DefaultNumericAxis("dipole current", "A");
        xAxis2.setAnimated(false);
        yAxis2.setSide(Side.RIGHT);
        yAxis2.setAutoUnitScaling(true);
        yAxis2.setAutoRanging(true);
        yAxis2.setAnimated(false);
        // N.B. it's important to set secondary axis on the 2nd renderer before
        // adding the renderer to the chart
        dipoleCurrentRenderer.getAxes().add(yAxis2);

        final XYChart chart = new XYChart(xAxis1, yAxis1);
        chart.legendVisibleProperty().set(true);
        chart.setAnimated(false);
        chart.getXAxis().setLabel("time 1");
        chart.getXAxis().setAutoRanging(true);
        chart.getYAxis().setLabel("beam intensity");
        chart.getYAxis().setAutoRanging(true);
        chart.getYAxis().setSide(Side.LEFT);
        chart.getRenderers().set(0, beamIntensityRenderer);
        chart.getRenderers().add(dipoleCurrentRenderer);

        chart.getPlugins().add(new ParameterMeasurements());
        chart.getPlugins().add(new EditAxis());
        // chart.getPlugins().add(new CrosshairIndicator());
        chart.getPlugins().add(new DataPointTooltip());
        chart.getPlugins().add(new Panner());
        final Zoomer zoom = new Zoomer();
        zoom.setSliderVisible(false);
        chart.getPlugins().add(zoom);

        final double minX = rollingBufferDipoleCurrent.getXMin();
        final double maxX = rollingBufferDipoleCurrent.getXMax();
        final double minY1 = rollingBufferBeamIntensity.getYMin();
        final double maxY1 = rollingBufferBeamIntensity.getYMax();
        final double minY2 = rollingBufferDipoleCurrent.getYMin();
        final double maxY2 = rollingBufferDipoleCurrent.getYMax();
        final double rangeX = maxX - minX;
        final double rangeY1 = maxY1 - minY1;
        final double rangeY2 = maxY2 - minY2;

        final XRangeIndicator xRange = new XRangeIndicator(xAxis1, minX + 0.1 * rangeX, minX + 0.2 * rangeX, "range-X");
        chart.getPlugins().add(xRange);
        xRange.upperBoundProperty().bind(xAxis1.upperBoundProperty().subtract(0.1));
        xRange.lowerBoundProperty().bind(xAxis1.upperBoundProperty().subtract(1.0));

        final YRangeIndicator yRange1 = new YRangeIndicator(yAxis1, minY1 + 0.1 * rangeY1, minY1 + 0.2 * rangeY1,
                "range-Y1");
        chart.getPlugins().add(yRange1);

        final YRangeIndicator yRange2 = new YRangeIndicator(yAxis2, 2100, 2200, "range-Y2 (2100-2200 A)");
        chart.getPlugins().add(yRange2);

        final XValueIndicator xValueIndicator = new XValueIndicator(xAxis1, minX + 0.5 * rangeX, "mid-range label -X");
        chart.getPlugins().add(xValueIndicator);
//        xValueIndicator.valueProperty().bind(xAxis1.lowerBoundProperty().add(5));

        final YValueIndicator yValueIndicator1 = new YValueIndicator(yAxis1, minY1 + 0.5 * rangeY1,
                "mid-range label -Y1");
        chart.getPlugins().add(yValueIndicator1);

        final YValueIndicator yValueIndicator2 = new YValueIndicator(yAxis2, minY2 + 0.2 * rangeY2,
                "mid-range label -Y2");
        chart.getPlugins().add(yValueIndicator2);

        beamIntensityRenderer.getDatasets().add(rollingBufferBeamIntensity);
        dipoleCurrentRenderer.getDatasets().add(rollingBufferDipoleCurrent);
        dipoleCurrentRenderer.getDatasets().add(rollingSine);

        xAxis1.setAutoRangeRounding(false);
        xAxis2.setAutoRangeRounding(false);
        xAxis1.setTickLabelRotation(45);
        xAxis2.setTickLabelRotation(45);
        xAxis1.invertAxis(false);
        xAxis2.invertAxis(false);
        xAxis1.setTimeAxis(true);
        xAxis2.setTimeAxis(true);

        // set localised time offset
        if (xAxis1.isTimeAxis() && xAxis1.getAxisLabelFormatter() instanceof DefaultTimeFormatter) {
            final DefaultTimeFormatter axisFormatter = (DefaultTimeFormatter) xAxis1.getAxisLabelFormatter();

            axisFormatter.setTimeZoneOffset(ZoneOffset.UTC);
            axisFormatter.setTimeZoneOffset(ZoneOffset.ofHoursMinutes(5, 0));
        }

        yAxis1.setForceZeroInRange(true);
        yAxis2.setForceZeroInRange(true);
        yAxis1.setAutoRangeRounding(true);
        yAxis2.setAutoRangeRounding(true);

        final TimerTask task = new TimerTask() {
            int updateCount = 0;

            @Override
            public void run() {
                Platform.runLater(() -> {
                    generateData();

                    if (updateCount % 20 == 0) {
                        System.out.println("update iteration #" + updateCount);
                    }

                    // if (updateCount % 40 == 0) {
                    // //test dynamic left right axis change
                    // yAxis2.setSide(yAxis2.getSide().equals(Side.RIGHT)?Side.LEFT:Side.RIGHT);
                    // }

                    // if ((updateCount+20) % 40 == 0) {
                    // //test dynamic bottom top axis change
                    // xAxis1.setSide(xAxis1.getSide().equals(Side.BOTTOM)?Side.TOP:Side.BOTTOM);
                    // }

                    updateCount++;
                });
            }
        };

        root.setTop(getHeaderBar(scene, task));

        startTime = ProcessingProfiler.getTimeStamp();
        ProcessingProfiler.getTimeDiff(startTime, "adding data to chart");

        startTime = ProcessingProfiler.getTimeStamp();
        root.setCenter(chart);

        ProcessingProfiler.getTimeDiff(startTime, "adding chart into StackPane");

        return root;
    }

    private HBox getHeaderBar(final Scene scene, final TimerTask task) {

        final Button newDataSet = new Button("new DataSet");
        newDataSet.setOnAction(evt -> Platform.runLater(task));

        final Button startTimer = new Button("timer");
        startTimer.setOnAction(evt -> {
            rollingBufferBeamIntensity.reset();
            rollingBufferDipoleCurrent.reset();
            if (timer == null) {
                timer = new Timer();
                timer.scheduleAtFixedRate(task, UPDATE_DELAY, UPDATE_PERIOD);
            } else {
                timer.cancel();
                timer = null;
            }
        });

        // H-Spacer
        final Region spacer = new Region();
        spacer.setMinWidth(Region.USE_PREF_SIZE);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // JavaFX and Chart Performance metrics
        final SimplePerformanceMeter meter = new SimplePerformanceMeter(scene, DEBUG_UPDATE_RATE);

        final Label fxFPS = new Label();
        fxFPS.setFont(Font.font("Monospaced", 12));
        final Label chartFPS = new Label();
        chartFPS.setFont(Font.font("Monospaced", 12));
        final Label cpuLoadProcess = new Label();
        cpuLoadProcess.setFont(Font.font("Monospaced", 12));
        final Label cpuLoadSystem = new Label();
        cpuLoadSystem.setFont(Font.font("Monospaced", 12));
        meter.fxFrameRateProperty().addListener((ch, o, n) -> {
            final String fxRate = String.format("%4.1f", meter.getFxFrameRate());
            final String actualRate = String.format("%4.1f", meter.getActualFrameRate());
            final String cpuProcess = String.format("%5.1f", meter.getProcessCpuLoad());
            final String cpuSystem = String.format("%5.1f", meter.getSystemCpuLoad());
            fxFPS.setText(String.format("%-6s: %4s %s", "JavaFX", fxRate, "FPS, "));
            chartFPS.setText(String.format("%-6s: %4s %s", "Actual", actualRate, "FPS, "));
            cpuLoadProcess.setText(String.format("%-11s: %4s %s", "Process-CPU", cpuProcess, "%"));
            cpuLoadSystem.setText(String.format("%-11s: %4s %s", "System -CPU", cpuSystem, "%"));
        });

        return new HBox(newDataSet, startTimer, spacer, new VBox(fxFPS, chartFPS),
                new VBox(cpuLoadProcess, cpuLoadSystem));
    }

    private static double square(final double frequency, final double t) {
        final double sine = 100 * Math.sin(2.0 * Math.PI * frequency * t);
        final double squarePoint = Math.signum(sine);
        return squarePoint >= 0 ? squarePoint : 0.0;
    }

    private static double sine(final double frequency, final double t) {
        return Math.sin(2.0 * Math.PI * frequency * t);
    }

    private static double rampFunctionDipoleCurrent(final double t) {
        final int second = (int) Math.floor(t);
        final double subSecond = t - second;
        double offset = 0.3;

        double y = 100 * ChartIndicatorSample.sine(1, subSecond - offset);

        // every 5th cycle is a booster mode cycle
        if (second % 5 == 0) {
            offset = 0.1;
            y = 100 * Math.pow(ChartIndicatorSample.sine(1.5, subSecond - offset), 2);
        }

        if (y <= 0 || subSecond < offset) {
            y = 0;
        }
        return y + 10;

    }

    private static double rampFunctionBeamIntensity(final double t) {
        final int second = (int) Math.floor(t);
        final double subSecond = t - second;
        double offset = 0.3;
        final double y = (1 - 0.1 * subSecond) * 1e9;
        double gate = ChartIndicatorSample.square(2, subSecond - offset)
                * ChartIndicatorSample.square(1, subSecond - offset);

        // every 5th cycle is a booster mode cycle
        if (second % 5 == 0) {
            offset = 0.1;
            gate = Math.pow(ChartIndicatorSample.square(3, subSecond - offset), 2);
        }

        if (gate <= 0 || subSecond < offset) {
            gate = 0;
        }

        return gate * y;

    }

    private void generateData() {
        startTime = ProcessingProfiler.getTimeStamp();
        final double now = System.currentTimeMillis() / 1000.0 + 1; // N.B. '+1'
                                                                    // to check
                                                                    // for
                                                                    // resolution

        if (rollingBufferDipoleCurrent.getDataCount() == 0) {
            rollingBufferBeamIntensity.setAutoNotifaction(false);
            rollingBufferDipoleCurrent.setAutoNotifaction(false);
            rollingSine.setAutoNotifaction(false);
            for (int n = ChartIndicatorSample.N_SAMPLES; n > 0; n--) {
                final double t = now - n * ChartIndicatorSample.UPDATE_PERIOD / 1000.0;
                final double y = 25 * ChartIndicatorSample.rampFunctionDipoleCurrent(t);
                final double y2 = 100 * ChartIndicatorSample.rampFunctionBeamIntensity(t);
                final double ey = 1;
                rollingBufferDipoleCurrent.add(t, y, ey, ey);
                rollingBufferBeamIntensity.add(
                        t + ChartIndicatorSample.UPDATE_PERIOD / 1000.0 * RandomDataGenerator.random(), y2, ey, ey);
                rollingSine.add(t + 1 + ChartIndicatorSample.UPDATE_PERIOD / 1000.0 * RandomDataGenerator.random(),
                        y * 0.8, ey, ey);
            }
            rollingBufferBeamIntensity.setAutoNotifaction(true);
            rollingBufferDipoleCurrent.setAutoNotifaction(true);
            rollingSine.setAutoNotifaction(true);
        } else {
            rollingBufferDipoleCurrent.setAutoNotifaction(false);
            final double t = now;
            final double y = 25 * ChartIndicatorSample.rampFunctionDipoleCurrent(t);
            final double y2 = 100 * ChartIndicatorSample.rampFunctionBeamIntensity(t);
            final double ey = 1;
            rollingBufferDipoleCurrent.add(t, y, ey, ey);
            rollingBufferBeamIntensity.add(t, y2, ey, ey);
            final double val = 1500 + 1000.0 * Math.sin(Math.PI * 2 * 0.1 * t);
            rollingSine.add(t + 1, val, ey, ey);
            rollingBufferDipoleCurrent.setAutoNotifaction(true);
        }

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