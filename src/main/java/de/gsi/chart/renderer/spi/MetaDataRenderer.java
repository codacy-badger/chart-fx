package de.gsi.chart.renderer.spi;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;

import de.gsi.chart.Chart;
import de.gsi.chart.axes.Axis;
import de.gsi.chart.data.DataSet;
import de.gsi.chart.data.DataSetMetaData;
import de.gsi.chart.renderer.Renderer;
import de.gsi.chart.ui.geometry.Side;
import de.gsi.chart.utils.ProcessingProfiler;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class MetaDataRenderer extends AbstractMetaDataRendererParameter<MetaDataRenderer> implements Renderer {
    protected BorderPane borderPane = new BorderPane();
    protected FlowPane messageBox = new FlowPane();
    protected HBox infoBox = new InfoHBox();
    protected HBox warningBox = new InfoHBox();
    protected HBox errorBox = new InfoHBox();
    protected Chart chart;

    public MetaDataRenderer(final Chart chart) {
        super();
        this.chart = chart;
        updateCSS();
        messageBox.getChildren().addAll(errorBox, warningBox, infoBox);
        messageBox.setMouseTransparent(true);
        messageBox.setPrefWidth(1000);
        messageBox.setCache(true);
        // HBox.setHgrow(messageBox, Priority.SOMETIMES);
        // VBox.setVgrow(messageBox, Priority.SOMETIMES);

        chart.getCanvasForeground().getChildren().add(borderPane);
        final ChangeListener<Number> canvasChange = (ch, oldVal, newVal) -> borderPane
                .setPrefSize(chart.getCanvasForeground().getWidth(), chart.getCanvas().getHeight());

        chart.getCanvas().widthProperty().addListener(canvasChange);
        chart.getCanvas().heightProperty().addListener(canvasChange);

        setInfoBoxSide(Side.TOP);

        // SvgImageLoaderFactory.install();
        // // SvgImageLoaderFactory.install(new PrimitiveDimensionProvider());
    }

    @Override
    protected MetaDataRenderer getThis() {
        return this;
    }

    public BorderPane getBorderPaneOnCanvas() {
        return borderPane;
    }

    /**
     *
     * @return FlowPane containing the Info-, Warning- and Error-Boxes
     */
    public FlowPane getMessageBox() {
        return messageBox;
    }

    /**
     *
     * @return box that is being filled with Info messages
     */
    public HBox getInfoBox() {
        return infoBox;
    }

    /**
     *
     * @return box that is being filled with Warning messages
     */
    public HBox getWarningBox() {
        return warningBox;
    }

    /**
     *
     * @return box that is being filled with Error messages
     */
    public HBox getErrorBox() {
        return errorBox;
    }

    protected List<String> oldInfoMessages;
    protected List<String> oldWarningMessages;
    protected List<String> oldErrorMessages;

    @Override
    public void render(final GraphicsContext gc, final Chart chart, final int dataSetOffset,
            final ObservableList<DataSet> datasets) {
        final long start = ProcessingProfiler.getTimeStamp();

        final ObservableList<DataSet> allDataSets = chart.getAllDatasets();
        final boolean singleDS = allDataSets.size() <= 1;
        final List<DataSet> metaDataSets = getDataSetsWithMetaData(allDataSets);

        final List<String> infoMessages = isShowInfoMessages() ? extractMessages(metaDataSets, singleDS, MsgType.INFO)
                : new ArrayList<>();
        final List<String> warningMessages = isShowWarningMessages()
                ? extractMessages(metaDataSets, singleDS, MsgType.WARNING)
                : new ArrayList<>();
        final List<String> errorMessages = isShowErrorMessages()
                ? extractMessages(metaDataSets, singleDS, MsgType.ERROR)
                : new ArrayList<>();

        if (!infoMessages.equals(oldInfoMessages)) {
            oldInfoMessages = infoMessages;
            infoBox.getChildren().clear();
            if (!infoMessages.isEmpty()) {
                final VBox msgs = new VBox();
                infoBox.getChildren().addAll(new ImageView(imgIconInfo), msgs);

                for (final String text : infoMessages) {
                    final MetaLabel info = new MetaLabel(text);
                    msgs.getChildren().add(info);
                }
            }
        }

        if (!warningMessages.equals(oldWarningMessages)) {
            oldWarningMessages = warningMessages;
            warningBox.getChildren().clear();
            if (!warningMessages.isEmpty()) {
                final VBox msgs = new VBox();
                warningBox.getChildren().addAll(new ImageView(imgIconWarning), msgs);

                for (final String text : warningMessages) {
                    final MetaLabel info = new MetaLabel(text);
                    msgs.getChildren().add(info);
                }
            }
        }

        if (!errorMessages.equals(oldErrorMessages)) {
            oldErrorMessages = errorMessages;
            if (!errorMessages.isEmpty()) {
                final VBox msgs = new VBox();
                for (final String text : errorMessages) {
                    final MetaLabel info = new MetaLabel(text);
                    msgs.getChildren().add(info);
                }
                errorBox.getChildren().setAll(new ImageView(imgIconError), msgs);
            } else {
                errorBox.getChildren().clear();
            }
        }

        ProcessingProfiler.getTimeDiff(start);
    }

    protected class MetaLabel extends Label {

        public MetaLabel(final String text) {
            super(text);
            setMouseTransparent(true);
            setMinSize(100, 20);
            setCache(true);
        }

    }

    protected enum MsgType {
        INFO, WARNING, ERROR;
    }

    private List<String> extractMessages(List<DataSet> metaDataSets, boolean singleDS, MsgType msgType) {
        final List<String> list = new ArrayList<>();

        for (final DataSet dataSet : metaDataSets) {
            if (!(dataSet instanceof DataSetMetaData)) {
                continue;
            }
            final String dataSetName = dataSet.getName();
            final DataSetMetaData metaData = (DataSetMetaData) dataSet;

            List<String> msg;
            switch (msgType) {
            case ERROR:
                msg = metaData.getErrorList();
                break;
            case WARNING:
                msg = metaData.getWarningList();
                break;
            case INFO:
            default:
                msg = metaData.getInfoList();
                break;
            }

            for (final String info : msg) {
                if (singleDS) {
                    // just one applicable data set
                    list.add(info);
                } else {
                    // if duplicates, then add list with
                    // 'InfoMsg(DataSet::Name)'
                    list.add(info + " (" + dataSetName + ")");
                }
            }
        }

        return list;
    }

    protected List<DataSet> getDataSetsWithMetaData(List<DataSet> dataSets) {
        final List<DataSet> list = new ArrayList<>();
        for (final DataSet dataSet : dataSets) {
            if (!(dataSet instanceof DataSetMetaData)) {
                continue;
            }
            list.add(dataSet);
        }

        return list;
    }

    // ******************************* class specific properties **********

    protected final BooleanProperty drawOnCanvas = new SimpleBooleanProperty(this, "drawOnCanvas", true) {
        boolean oldValue = true;

        @Override
        public void set(boolean newValue) {
            if (oldValue == newValue) {
                return;
            }
            super.set(newValue);
            oldValue = newValue;
            updateInfoBoxLocation();
        }
    };

    public boolean isDrawOnCanvas() {
        return drawOnCanvas.get();
    }

    public void setDrawOnCanvas(boolean state) {
        drawOnCanvas.set(state);
    }

    public BooleanProperty drawOnCanvasProperty() {
        return drawOnCanvas;
    }

    protected final ObjectProperty<Side> infoBoxSide = new SimpleObjectProperty<Side>(this, "infoBoxSide", Side.TOP) {
        Side oldSide = null;

        @Override
        public void set(final Side side) {
            if (side == null) {
                throw new InvalidParameterException("side must not be null");
            }

            if (oldSide != null && oldSide == side) {
                return;
            }
            super.set(side);
            oldSide = side;

            updateInfoBoxLocation();
        }
    };

    protected void updateInfoBoxLocation() {
        final Side side = getInfoBoxSide();

        // remove old pane
        borderPane.getChildren().remove(messageBox);
        for (final Side s : Side.values()) {
            chart.getTitleLegendPane(s).getChildren().remove(messageBox);
        }

        if (isDrawOnCanvas()) {
            switch (side) {
            case RIGHT:
                messageBox.setMaxWidth(300);
                messageBox.setPrefWidth(200);
                borderPane.setRight(messageBox);
                break;
            case LEFT:
                messageBox.setMaxWidth(300);
                messageBox.setPrefWidth(200);
                borderPane.setLeft(messageBox);
                break;
            case BOTTOM:
                messageBox.setPrefWidth(1000);
                messageBox.setMaxWidth(2000);
                borderPane.setBottom(messageBox);
                break;
            case TOP:
            default:
                messageBox.setMaxWidth(2000);
                messageBox.setPrefWidth(1000);
                borderPane.setTop(messageBox);
                break;
            }
        } else {
            chart.getTitleLegendPane(side).getChildren().add(messageBox);
        }
        // chart.requestLayout();
    }

    /**
     * whether renderer should draw info box in Side side, ...
     *
     * @return Side
     */
    public final Side getInfoBoxSide() {
        return infoBoxSideProperty().get();
    }

    /**
     * whether renderer should draw info box in Side side, ...
     *
     * @param side
     *            the side to draw
     * @return itself (fluent design)
     */
    public final MetaDataRenderer setInfoBoxSide(final Side side) {
        infoBoxSideProperty().set(side);
        return getThis();
    }

    /**
     * whether renderer should draw info box in Side side, ...
     *
     * @return property
     */
    public final ObjectProperty<Side> infoBoxSideProperty() {
        return infoBoxSide;
    }

    @Override
    public ObservableList<DataSet> getDatasets() {
        return FXCollections.observableArrayList();
    }

    @Override
    public ObservableList<DataSet> getDatasetsCopy() {
        return FXCollections.observableArrayList();
    }

    @Override
    public BooleanProperty showInLegendProperty() {
        return null;
    }

    @Override
    public boolean showInLegend() {
        return false;
    }

    @Override
    public Renderer setShowInLegend(boolean state) {
        return getThis();
    }

    @Override
    public ObservableList<Axis> getAxes() {
        return FXCollections.observableArrayList();
    }

    class InfoHBox extends HBox {
        public InfoHBox() {
            super();
            setMouseTransparent(true);

            // adjust size to 0 if there are no messages to show
            getChildren().addListener((ListChangeListener<Node>) ch -> {
                if (getChildren().isEmpty()) {
                    setMinWidth(0);
                    setSpacing(0);
                    setPadding(Insets.EMPTY);
                } else {
                    setPadding(new Insets(5, 5, 5, 5));
                    setMinWidth(200);
                    setSpacing(5);
                }
            });
        }
    }

    @Override
    public Canvas drawLegendSymbol(DataSet dataSet, int dsIndex, int width, int height) {
        // not applicable for this class
        return null;
    }
}
