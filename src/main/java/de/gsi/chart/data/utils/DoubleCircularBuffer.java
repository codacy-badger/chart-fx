package de.gsi.chart.data.utils;

/**
 * simple circular ring buffer implementation for double type (with read ==
 * write position)
 *
 * @author rstein
 */
public class DoubleCircularBuffer {
    private final double[] elements;
    private final int capacity;
    private int writePos; // buffer has once being fully written
    private boolean flipped;

    public DoubleCircularBuffer(final int capacity) {
        this.capacity = capacity;
        elements = new double[capacity];
        flipped = false;
    }

    public void reset() {
        writePos = 0;
        flipped = false;
    }

    public int available() {
        if (flipped) {
            return capacity;
        }
        return writePos;
    }

    public int remainingCapacity() {
        return available();
    }

    public boolean put(final double element) {
        elements[writePos++] = element;
        if (writePos == capacity) {
            writePos = 0;
            flipped = true;
        }

        return true;
    }

    public int put(final double[] newElements, final int length) {
        return put(newElements, 0, length);
    }

    public int put(final double[] newElements, final int startIndex, final int length) {
        // readPos lower than writePos - free sections are:
        // 1) from writePos to capacity
        // 2) from 0 to readPos
        final int lengthUpperHalf = capacity - writePos;
        if (length <= lengthUpperHalf) {
            // new elements fit into top half of elements array - copy directly
            System.arraycopy(newElements, startIndex, elements, writePos, length);
            writePos += length;

            if (writePos == capacity) {
                writePos = 0;
                flipped = true;
            }
            return writePos;
        }

        // length > lengthUpperHalf
        System.arraycopy(newElements, startIndex, elements, writePos, lengthUpperHalf);
        writePos = capacity - 1;
        writePos += lengthUpperHalf;
        if (writePos >= capacity) {
            writePos = 0;
            flipped = true;
        }

        // writing the remained of the array to the circular buffer
        return put(newElements, startIndex + lengthUpperHalf, length - lengthUpperHalf);

    }

    public double get(final int readPos) {
        int index = flipped ? writePos + readPos : readPos;
        while (index >= capacity) {
            index -= capacity;
        }
        if (!flipped) {

            if (index >= 0) {
                return elements[index];
            }
            throw new IllegalArgumentException("writePos = '" + writePos + "' readPos = '" + readPos + "'/index = '"
                    + index + "' is beyond circular buffer capacity limits = [0," + capacity + "]");
        }
        // adjust for turn-around index
        while (index < 0) {
            index += capacity;
        }
        while (index >= capacity) {
            index -= capacity;
        }
        return elements[index];
    }

    public double[] get(final double[] into, final int length) {
        return get(into, 0, length);
    }

    public double[] get(final double[] into, final int readPos, final int length) {
        final double[] retVal = into == null ? new double[length] : into;
        // N.B. actually there seem to be no numerically more efficient
        // implementation
        // since the order of the indices for 'into' need to be reverse order
        // w.r.t. 'elements'
        for (int i = 0; i < length; i++) {
            retVal[i] = get(i + readPos);
        }

        return retVal;
    }

    /**
     * meant for testing/illustrating usage
     *
     * @param args
     *            the command line arguments
     */
    public static void main(final String[] args) {
        final int bufferLength = 10;
        final int fillBufferLength = 35;
        final DoubleCircularBuffer buffer1 = new DoubleCircularBuffer(bufferLength);
        final DoubleCircularBuffer buffer2 = new DoubleCircularBuffer(bufferLength);
        final double[] input = new double[fillBufferLength];
        final double[] output = new double[fillBufferLength];

        buffer1.put(-2);
        buffer1.put(-1);
        buffer2.put(-2);
        buffer2.put(-1);

        for (int i = 0; i < fillBufferLength; i++) {
            buffer1.put(i);
            input[i] = i;
        }
        buffer2.put(input, fillBufferLength);
        buffer2.get(output, 10);
        System.out.println("demo print-out");
        for (int i = 0; i < 30; i++) {
            System.out.println(String.format("buffer[1,2].get(%d) = [%2.0f,%2.0f,%2.0f]", i, buffer1.get(i),
                    buffer2.get(i), output[i]));
        }
    }

}
