package de.gsi.math;

/**
 * class implementing natural cubic splines according to: http://en.wikipedia.org/wiki/Spline_(mathematics)
 * 
 * @author rstein
 */
public class Spline {

    private int fsrcPos = 0;
    private int fnPoints = 0;
    private double[] fx, fy; // input data
    private double[] fA, fB, fC; // interpolation coefficients

    private double[] fboundCond1 = new double[2];
    private double[] fboundCondN = new double[2];

    /**
     * default constructor
     * 
     * @param x array of horizontal coordinates
     * @param y array of vertical coordinates Both arrays are expected to be sorted. The horizontal array should not
     *            contain entries with the same value. The default dimension is derived from the
     * @see #Spline(double[] x, double[] y, int length, int scrPos)
     */
    public Spline(double[] x, double[] y) {
        this(x, y, x.length, 0);
    }

    /**
     * @param x array of horizontal coordinates
     * @param y array of vertical coordinates
     * @param length length of the data
     * @param scrPos first index of the data Both arrays are expected to be sorted. The horizontal array should not
     *            contain entries with the same value. It is implicitly required that: 0 <= srcPos <= srcPos+length <
     *            x.length and length>3
     */
    public Spline(double[] x, double[] y, int length, int scrPos) {
        fsrcPos = scrPos;
        fnPoints = length;
        fx = new double[length];
        fy = new double[length];

        fA = new double[length - 1];
        fB = new double[length - 1];
        fC = new double[length - 1];
        System.arraycopy(x, fsrcPos, fx, 0, length);
        System.arraycopy(y, fsrcPos, fy, 0, length);
        SetupBoundaryConditions();
        CalcCoefficients();
    }

    /**
     * set type 1 boundary coefficients (vanishing second order moment)
     */
    private void SetupBoundaryConditions() {
        fboundCond1[0] = 0.0f;
        fboundCond1[1] = 0.0f; // = 2.0f * x''(0)
        fboundCondN[0] = 0.0f;
        fboundCondN[1] = 0.0f; // = 2.0f * x''(N-1)
    }

    /**
     * computation of natural cubic spline coefficients
     */
    private void CalcCoefficients() {

        double dx1, dx2;
        double dy1, dy2;

        dx1 = fx[1] - fx[0];
        dy1 = fy[1] - fy[0];
        for (int i = 1; i < fnPoints - 1; i++) {
            dx2 = fx[i + 1] - fx[i];
            dy2 = fy[i + 1] - fy[i];
            fC[i] = dx2 / (dx1 + dx2);
            fB[i] = 1.0f - fC[i];
            fA[i] = 6.0f * (dy2 / dx2 - dy1 / dx1) / (dx1 + dx2);
            dx1 = dx2;
            dy1 = dy2;
        }
        fC[0] = -fboundCond1[0] / 2.0f;
        fB[0] = fboundCond1[1] / 2.0f;
        fA[0] = 0.0f;

        for (int i = 1; i < fnPoints - 1; i++) {
            double p = fB[i] * fC[i - 1] + 2.0f;
            fC[i] = -fC[i] / p;
            fB[i] = (fA[i] - fB[i] * fB[i - 1]) / p;
        }

        //
        dy1 = (fboundCondN[1] - fboundCondN[0] * fB[fnPoints - 2]) / (fboundCondN[0] * fC[fnPoints - 2] + 2.0f);

        for (int i = fnPoints - 2; i >= 0; i--) {
            dx1 = fx[i + 1] - fx[i];
            dy2 = fC[i] * dy1 + fB[i];
            fA[i] = (dy1 - dy2) / (6.0f * dx1);
            fB[i] = dy2 / 2.0f;
            fC[i] = (fy[i + 1] - fy[i]) / dx1 - dx1 * (fB[i] + dx1 * fA[i]);
            dy1 = dy2;
        }
    }

    /**
     * returns the cubic-spline interpolated value at x
     * 
     * @param x
     * @return cubic-spline interpolated value at x
     */
    public double getValue(double x) {
        if (fnPoints < 2)
            return 0.0;

        // search for index i such that mX[i] <= x < mX[i],
        // or '0' if x < mX[0], and N-2 if x >= mX[N-2].
        int left = 0;
        int right = fnPoints - 1;
        while (left + 1 < right) {
            int middle = (left + right) >> 1;
            if (fx[middle] <= x) {
                left = middle;
            } else {
                right = middle;
            }
        }
        int i = left;

        double t = (x - fx[i]);

        final double t2 = t * t;
        final double t3 = t2 * t;
        return fy[i] + fA[i] * t3 + fB[i] * t2 + fC[i] * t;
    }

    /**
     * Debug out-put of the spline fix-points and coefficients
     */
    public void printCoefficients() {
        for (int i = 0; i < fnPoints - 1; i++) {
            System.out.println("(x,y)[" + i + "] = (" + fx[i] + "," + fy[i] + ")");
            System.out.println("(a,b,c) = (" + fA[i] + "," + fB[i] + "," + fC[i] + ")");
        }
    }

    @Override
    public String toString() {
        return "Spline-" + fnPoints + "-points";
    }
}
