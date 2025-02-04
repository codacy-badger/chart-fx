package de.gsi.math.matrix;

public interface Matrix extends Cloneable, java.io.Serializable {

    /**
     * Get row dimension
     * 
     * @return m, the number of rows.
     */
    public int getRowDimension();

    /**
     * Get column dimension.
     * 
     * @return n, the number of columns.
     */
    public int getColumnDimension();

    /**
     * @param i
     * @param j
     * @return value of matrix element
     */
    public double get(int i, int j);

    /**
     * @param i
     * @param j
     * @param val
     */
    public void set(int i, int j, double val);

    /**
     * @return copy of matrix
     */
    public Matrix copy();

    public void checkMatrixDimensions(Matrix B);
}
