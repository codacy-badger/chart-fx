package de.gsi.math.functions;

import de.gsi.math.TMath;

public class BetaFunction extends AbstractFunction1D implements Function1D {

    /**
     * initialise (Euler's) Beta function
     * parameter order:
     * parameter[0] = par (default: 1.0)
     *
     * @param name function name
     * @param parameter 0:par
     */
    public BetaFunction(final String name, final double[] parameter) {
        super(name, new double[1]);
        setParameterName(0, "par");
        setParameterValue(0, 1);

        if (parameter == null) {
            return;
        }

        for (int i = 0; i < Math.min(parameter.length, 1); i++) {
            setParameterValue(i, parameter[0]);
        }
    }

    /**
     * initialise (Euler's) Beta function
     * parameter order:
     * parameter[0] = par (default: 1.0)
     *
     * @param name function name
     */
    public BetaFunction(final String name) {
        this(name, null);
    }

    @Override
    public double getValue(final double x) {
        return TMath.Beta(x, fparameter[0]);
    }
}
