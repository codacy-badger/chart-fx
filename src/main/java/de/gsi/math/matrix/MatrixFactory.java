package de.gsi.math.matrix;

public class MatrixFactory {

	/** Construct a matrix from a copy of a 2-D array.
	 * @param A    Two-dimensional array of doubles.
	 * @exception  IllegalArgumentException All rows must have the same length
     */
	public static MatrixD constructWithCopy(double[][] A) {
		int m = A.length;
		int n = A[0].length;
		MatrixD X = new MatrixD(m, n);
		double[][] C = X.getArray();
		for (int i = 0; i < m; i++) {
			if (A[i].length != n) {
				throw new IllegalArgumentException("All rows must have the same length.");
			}
			for (int j = 0; j < n; j++) {
				C[i][j] = A[i][j];
			}
		}
		return X;
	}
	
	/** Generate identity matrix 
	 * @param m    Number of rows.
	 * @param n    Number of colums.
	 * @return     An m-by-n matrix with ones on the diagonal and zeros elsewhere.
     */
	public static MatrixD identity(int m, int n) {
		MatrixD A = new MatrixD(m, n);		
		for (int i = 0; i < m; i++) {
			for (int j = 0; j < n; j++) {
				A.set(i, j, i == j ? 1.0 : 0.0);
			}
		}
		return A;
	}
	
	/** Generate matrix with random elements
	 * @param m    Number of rows.
	 * @param n    Number of colums.
	 * @return     An m-by-n matrix with uniformly distributed random elements.
	 */
	public static MatrixD random(int m, int n) {
		MatrixD A = new MatrixD(m, n);
		double[][] X = A.getArray();
		for (int i = 0; i < m; i++) {
			for (int j = 0; j < n; j++) {
				X[i][j] = Math.random();
			}
		}
		return A;
	}


	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
