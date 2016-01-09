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

	public static double[] concatenate (double[] a, double[] b) {
		   int aLen = a.length;
		   int bLen = b.length;
		   double[] c= new double[aLen+bLen];
		   System.arraycopy(a, 0, c, 0, aLen);
		   System.arraycopy(b, 0, c, aLen, bLen);
		   return c;
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
}
