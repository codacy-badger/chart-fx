package de.gsi.chart.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.gsi.chart.XYChartCss;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;

/**
 * Some helper routines to parse CSS-style formatting attributes
 *
 * @author rstein
 */
public final class StyleParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(StyleParser.class);
    private static final int DEFAULT_FONT_SIZE = 18;
    private static final String DEFAULT_FONT = "Helvetia";

    private StyleParser() {

    }

    /**
     * spits input string, converts keys and values to lower case, and replaces '"' and ''' if any
     *
     * @param style the input style string
     * @return the sanitised map
     */
    public static Map<String, String> splitIntoMap(final String style) {
        final ConcurrentHashMap<String, String> retVal = new ConcurrentHashMap<>();
        if (style == null) {
            return retVal;
        }
        final String[] keyVals = style.toLowerCase().replaceAll("\\s+", "").split("[;,]");
        for (final String keyVal : keyVals) {
            final String[] parts = keyVal.split("[=:]", 2);
            if (parts == null || parts[0] == null || parts.length <= 1) {
                continue;
            }

            retVal.put(parts[0], parts[1].replaceAll("[\"\']", ""));
        }

        return retVal;
    }

    public static String mapToString(final Map<String, String> map) {
        String ret = "";
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
			final String value = entry.getValue();
            if (value != null) {
                ret = ret.concat(key).concat("=").concat(value).concat(";");
            }
        }
        return ret;
    }

    public static String getPropertyValue(final String style, final String key) {
        if (style == null || key == null) {
            return null;
        }

        final Map<String, String> map = StyleParser.splitIntoMap(style);

        return map.get(key.toLowerCase());
    }

    public static Color getColorPropertyValue(final String style, final String key) {
        if (style == null || key == null) {
            return null;
        }

        final Map<String, String> map = StyleParser.splitIntoMap(style);
        final String value = map.get(key.toLowerCase());
        if (value == null) {
            return null;
        }

        try {
            return Color.web(value);
        } catch (final IllegalArgumentException ex) {
            StyleParser.LOGGER.error("could not parse color description for '" + key + "'='" + value + "' returning null", ex);
            return null;
        }
    }

    public static Integer getIntegerPropertyValue(final String style, final String key) {
        if (style == null || key == null) {
            return null;
        }

        final Map<String, String> map = StyleParser.splitIntoMap(style);
        final String value = map.get(key.toLowerCase());
        if (value == null) {
            return null;
        }

        try {
            return Integer.decode(value);
        } catch (final NumberFormatException ex) {
            StyleParser.LOGGER.error("could not parse integer description for '" + key + "'='" + value + "' returning null", ex);
            return null;
        }
    }

    public static Double getFloatingDecimalPropertyValue(final String style, final String key) {
        if (style == null || key == null) {
            return null;
        }

        final Map<String, String> map = StyleParser.splitIntoMap(style);
        final String value = map.get(key.toLowerCase());
        if (value == null) {
            return null;
        }

        try {
            return Double.parseDouble(value);
        } catch (final NumberFormatException ex) {
            StyleParser.LOGGER.error("could not parse integer description for '" + key + "'='" + value + "' returning null", ex);
            return null;
        }
    }

    public static double[] getFloatingDecimalArrayPropertyValue(final String style, final String key) {
        if (style == null || key == null) {
            return null;
        }

        final Map<String, String> map = StyleParser.splitIntoMap(style);
        final String value = map.get(key.toLowerCase());
        if (value == null) {
            return null;
        }

        try {
            final String[] splitValues = value.split(",");
            if (splitValues == null || splitValues.length == 0) {
                return null;
            }
            final double[] retArray = new double[splitValues.length];
            for (int i = 0; i < splitValues.length; i++) {
                retArray[i] = Double.parseDouble(splitValues[i]);
            }
            return retArray;
        } catch (final NumberFormatException ex) {
            StyleParser.LOGGER.error("could not parse integer description for '" + key + "'='" + value + "' returning null", ex);
            return null;
        }
    }

    public static Boolean getBooleanPropertyValue(final String style, final String key) {
        if (style == null || key == null) {
            return null;
        }

        final Map<String, String> map = StyleParser.splitIntoMap(style);
        final String value = map.get(key.toLowerCase());
        if (value == null) {
            return null;
        }

        try {
            return Boolean.parseBoolean(value);
        } catch (final NumberFormatException ex) {
            StyleParser.LOGGER.error("could not parse boolean description for '" + key + "'='" + value + "' returning null", ex);
            return null;
        }
    }

    public static Font getFontPropertyValue(final String style) {
        if (style == null) {
            return Font.font(StyleParser.DEFAULT_FONT, StyleParser.DEFAULT_FONT_SIZE);
        }

        try {
            double fontSize = StyleParser.DEFAULT_FONT_SIZE;
            final Double fontSizeObj = StyleParser.getFloatingDecimalPropertyValue(style, XYChartCss.FONT_SIZE);
            if (fontSizeObj != null) {
                fontSize = fontSizeObj;
            }

            FontWeight fontWeight = null;
            final String fontW = StyleParser.getPropertyValue(style, XYChartCss.FONT_WEIGHT);
            if (fontW != null) {
                fontWeight = FontWeight.findByName(fontW);
            }

            FontPosture fontPosture = null;
            final String fontP = StyleParser.getPropertyValue(style, XYChartCss.FONT_POSTURE);
            if (fontP != null) {
                fontPosture = FontPosture.findByName(fontP);
            }

            final String font = StyleParser.getPropertyValue(style, XYChartCss.FONT);
            if (font == null) {
                return Font.font(StyleParser.DEFAULT_FONT, fontWeight, fontPosture, fontSize);
            }

            return Font.font(font, fontWeight, fontPosture, fontSize);

        } catch (final NumberFormatException ex) {
            StyleParser.LOGGER.error("could not parse font description style='" + style + "' returning default font", ex);
            return Font.font(StyleParser.DEFAULT_FONT, StyleParser.DEFAULT_FONT_SIZE);
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(final String[] args) {
        final String testStyle = " color1 = blue; stroke= 0; bool1=true; color2 = red2, unclean=\"a\', index1=2,index2=0xFE, float1=10e7, float2=10.333";

        System.out.println("colour parser1 = " + StyleParser.getPropertyValue(testStyle, "color1"));
        System.out.println("colour parser2 = " + StyleParser.getColorPropertyValue(testStyle, "color2"));
        System.out.println("int parser1 = " + StyleParser.getIntegerPropertyValue(testStyle, "index1"));
        System.out.println("int parser2 = " + StyleParser.getIntegerPropertyValue(testStyle, "index2"));
        System.out.println("float parser1 = " + StyleParser.getFloatingDecimalPropertyValue(testStyle, "float1"));
        System.out.println("float parser2 = " + StyleParser.getFloatingDecimalPropertyValue(testStyle, "float2"));
        System.out.println("boolean parser1 = " + StyleParser.getBooleanPropertyValue(testStyle, "bool1"));
    }
}
