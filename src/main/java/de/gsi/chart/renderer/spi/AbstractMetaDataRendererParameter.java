package de.gsi.chart.renderer.spi;

import java.security.InvalidParameterException;

import de.gsi.chart.renderer.spi.utils.ChartIconFactory;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

public abstract class AbstractMetaDataRendererParameter <R extends AbstractMetaDataRendererParameter<R>>  {
	protected static final String STYLE_CLASS_LABELLED_MARKER = "chart-meta-data";
	protected static final String DEFAULT_FONT = "Helvetia";
	protected static final int DEFAULT_FONT_SIZE = 18;
    protected static final Color DEFAULT_GRID_LINE_COLOR = Color.GREEN;
    protected static final double DEFAULT_GRID_LINE_WIDTH = 1;
    protected static final double[] DEFAULT_GRID_DASH_PATTERM = { 3.0, 3.0 };
    protected final StringProperty style = new SimpleStringProperty(this, "style", null);
    protected Paint strokeColorMarker = AbstractMetaDataRendererParameter.DEFAULT_GRID_LINE_COLOR;
    protected double strokeLineWidthMarker = AbstractMetaDataRendererParameter.DEFAULT_GRID_LINE_WIDTH;
    protected double[] strokeDashPattern = AbstractMetaDataRendererParameter.DEFAULT_GRID_DASH_PATTERM;


    protected Image imgIconInfo = ChartIconFactory.getInfoIcon();
    protected Image imgIconWarning = ChartIconFactory.getWarningIcon();
    protected Image imgIconError = ChartIconFactory.getErrorIcon();



    /**
     * @return the instance of this AbstractMetaDataRendererParameter.
     */
    protected abstract R getThis();


    protected final DoubleProperty iconSize = new SimpleDoubleProperty(this, "drawOnPane", 10.0) {

    	@Override
    	public void set(double newSize) {
    		if (newSize <=0) {
    			throw new InvalidParameterException("size should be >= 0, requested = " + newSize);
    		}
    		super.set(newSize);

    		imgIconInfo = ChartIconFactory.getInfoIcon(newSize, newSize);
    		imgIconWarning = ChartIconFactory.getWarningIcon(newSize, newSize);
    		imgIconError = ChartIconFactory.getErrorIcon(newSize, newSize);
    	}
    };

    protected final BooleanProperty showInfoMessages = new SimpleBooleanProperty(this, "showInfoMessages", true);

    public boolean isShowInfoMessages() {
    	return showInfoMessages.get();
    }

    public void setshowInfoMessages(boolean state) {
    	showInfoMessages.set(state);
    }

    public BooleanProperty showInfoMessagesProperty() {
    	return showInfoMessages;
    }

    protected final BooleanProperty showWarningMessages = new SimpleBooleanProperty(this, "showWarningMessages", true);

    public boolean isShowWarningMessages() {
    	return showWarningMessages.get();
    }

    public void setshowWarningMessages(boolean state) {
    	showWarningMessages.set(state);
    }

    public BooleanProperty showWarningMessagesProperty() {
    	return showWarningMessages;
    }

    protected final BooleanProperty showErrorMessages = new SimpleBooleanProperty(this, "showErrorMessages", true);

    public boolean isShowErrorMessages() {
    	return showErrorMessages.get();
    }

    public void setshowErrorMessages(boolean state) {
    	showErrorMessages.set(state);
    }

    public BooleanProperty showErrorMessagesProperty() {
    	return showErrorMessages;
    }

    public StringProperty styleProperty() {
        return style;
    }

    public String getStyle() {
        return style.get();
    }

    public R setStyle(final String newStyle) {
        style.set(newStyle);
        return getThis();
    }




    // ******************************* CSS Style Stuff *********************

    public final R updateCSS() {
        // TODO add/complete CSS parser

        // parse CSS based definitions
        // find definition for STYLE_CLASS_LABELLED_MARKER
        // parse
        strokeColorMarker = AbstractMetaDataRendererParameter.DEFAULT_GRID_LINE_COLOR;
        strokeLineWidthMarker = AbstractMetaDataRendererParameter.DEFAULT_GRID_LINE_WIDTH;
        strokeDashPattern = AbstractMetaDataRendererParameter.DEFAULT_GRID_DASH_PATTERM;

        if (getStyle() != null) {
            // parse user-specified marker
        }

        return getThis();
    }


}
