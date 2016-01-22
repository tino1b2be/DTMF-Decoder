/* The MIT License (MIT)
 * 
 * Copyright (c) 2015 Tinotenda Chemvura
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tino1b2be.dtmfdecoder;

import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * 
 * Class for arbitary functions used in the DTMF decoder
 * 
 * @author Tinotenda Chemvura
 *
 */
public class DecoderUtil {

	/**
	 * Method to concatenate 2 arrays of a generic type
	 * @param a
	 * @param b
	 * @return
	 */
	public static <T> T[] concatenate(T[] a, T[] b) {
		int aLen = a.length;
		int bLen = b.length;

		@SuppressWarnings("unchecked")
		T[] c = (T[]) Array.newInstance(a.getClass().getComponentType(), aLen + bLen);
		System.arraycopy(a, 0, c, 0, aLen);
		System.arraycopy(b, 0, c, aLen, bLen);

		return c;
	}

	/**
	 * Method to concatenate 2 arrays
	 * 
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

	/**
	 * Method to concatenate several double arrays
	 * 
	 * @param tempBuffer1
	 * @param buffer1
	 * @return
	 */
	public static double[] concatenateAll(double[] arr1, double[]... arr2) {
		int totalLength = arr1.length;
		for (double[] array : arr2) {
			totalLength += array.length;
		}
		double[] result = Arrays.copyOf(arr1, totalLength);
		int offset = arr1.length;
		for (double[] array : arr2) {
			System.arraycopy(array, 0, result, offset, array.length);
			offset += array.length;
		}
		return result;
	}

	/**
	 * Method to calculate and return the average power of the signal (average
	 * amplitude)
	 * 
	 * @param frame
	 *            Array of sample points to be tested
	 * @return average amplitude of the frame
	 */
	public static double signalPower(double[] frame) {
		double power = 0;

		for (int i = 0; i < frame.length; i++) {
			power += Math.abs(frame[i]);
		}
		return power / frame.length;
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

	/**
	 * Method to extract the dtmf tones represented in a wav file from the filename
	 * @param filename
	 * @return
	 */
	public static String getFileSequence(String filename) {
		filename = filename.substring(filename.lastIndexOf('/') + 1, filename.length() - 4); // remove
																								// .wav
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

	/**
	 * Method to calculate sum of an array
	 * 
	 * @param arr
	 *            Array whose mean is to be calculated
	 * @return mean of the input array
	 */
	public static double sumArray(double[] arr) {
		double out = 0.0;
		for (int i = 0; i < arr.length; i++)
			out += arr[i];
		return out;
	}

}
