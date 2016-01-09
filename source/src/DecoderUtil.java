package src;

import java.lang.reflect.Array;
import java.util.Arrays;

public class DecoderUtil {
	
	public static <T> T[] concatenate (T[] a, T[] b) {
	    int aLen = a.length;
	    int bLen = b.length;

	    @SuppressWarnings("unchecked")
	    T[] c = (T[]) Array.newInstance(a.getClass().getComponentType(), aLen+bLen);
	    System.arraycopy(a, 0, c, 0, aLen);
	    System.arraycopy(b, 0, c, aLen, bLen);

	    return c;
	}
	 /**
	  * Method to concatenate 2 arrays
	  * @param a
	  * @param b
	  * @return
	  */
	public static double[] concatenate(double[] a, double[] b) {
		int aLen = a.length;
		int bLen = b.length;
		double[] c = new double[aLen + bLen];
		System.arraycopy(a, 0, c, 0, aLen);
		System.arraycopy(b, 0, c, aLen, bLen);
		return c;
	}
	
	public static <T> double[] concatenateAll(double[] tempBuffer1, double[]... buffer1) {
		int totalLength = tempBuffer1.length;
		for (double[] array : buffer1) {
			totalLength += array.length;
		}
		double[] result = Arrays.copyOf(tempBuffer1, totalLength);
		int offset = tempBuffer1.length;
		for (double[] array : buffer1) {
			System.arraycopy(array, 0, result, offset, array.length);
			offset += array.length;
		}
		return result;
	}

	/**
	 * Method to calculate and return the average power of the signal (average amplitude)
	 * @param frame Array of sample points to be tested
	 * @return average amplitude of the frame
	 */
	public static double signalPower(double[] frame) {
		double power = 0;
		
		for (int i= 0; i < frame.length; i++){
			power += Math.abs(frame[i]);
		}
		return power/frame.length;
	}
	/**
	 * Function to return the largest value of an array
	 * 
	 * @param arr
	 *            Array to be processed
	 * @return Value of the largest element
	 */

	public static double max(double[] arr) {
		Arrays.sort(arr);
		return arr[arr.length - 1];
	}
	
	/**
	 * Method to return the index of the max element in an array
	 * 
	 * @param arr
	 *            Array to be processed
	 * @return Index of the max element
	 */
	public static int maxIndex(double[] arr) {
		int index = 0;
		double max = arr[0];
		for (int i = 0; i < arr.length; i++) {
			if (arr[i] > max) {
				max = arr[i];
				index = i;
			}
		}
		return index;
	}

	public static String getFileSequence(String filename) {
		filename = filename.substring(filename.lastIndexOf('/')+1, filename.length()-4); // remove .wav
		return filename;
	}
	
	/**
	 * Method to calculate mean of an array
	 * 
	 * @param arr
	 *            Array whose mean is to be calculated
	 * @return mean of the input array
	 */
	public static double meanArray(double[] arr) {
		double out = 0.0;
		for (int i = 0; i < arr.length; i++)
			out += arr[i];
		return out / (1.0 * arr.length);
	}
}
