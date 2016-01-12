package src;

import java.util.Arrays;

/**
 * Class Goertzel returns an outputList with values that represents the weight
 * of selected frequencies in a signal. Calculations are performed by using the
 * optimized Goertzel-algorithm.
 * 
 * Original file :
 * http://bejersdu.googlecode.com/svn/tags/netdelebil/presentation/Goertzel.java
 * 
 * Modified by Tinotenda Chemvura Original Author: Gruppe 4 (3/12/2008)
 * 
 * @author Tinotenda Chemvura
 * @version 04/01/2016
 */

public class Goertzel2 {
	private int numSamples; // Number of samples.
	private int sampleRate; // SampleRate in Hz.
	private final int[] freqs; // Frequency bin in Hz.
	private double[] coeffs; // Array with calculated coefficients
	private double y0, y1, y2; // y0 current output, y1 previous output (n-1),
								// y(2) output two samples ago (n-2).
	private double[] inputSamples;

	/**
	 * Initialize by calculating coefficients.
	 * 
	 * @param sampleRate
	 *            Sample Rate for the wav file
	 * @param numSamples
	 *            Number of samples in the frame
	 * @param freqBin
	 *            The frequency bin to be used in the Goertzel Transformation
	 */
	public Goertzel2(int sampleRate, int numSamples, int[] freqBin) {
		this.sampleRate = sampleRate;
		this.numSamples = numSamples;
		//Arrays.sort(freqBin); // sort the
		this.freqs = freqBin;
		calcCoeffs();
	}

	/**
	 * Initialize by calculating coefficients.
	 * 
	 * @param wav
	 *            wavData object with wav file information and sample data
	 */
	public Goertzel2(WavData wav, int[] freqBin) {
		this.sampleRate = (int) wav.getSampleRate();
		this.numSamples = wav.getSamples().length;
		//Arrays.sort(freqBin); // sort the
		this.freqs = freqBin;
		this.inputSamples = wav.getSamples();
		calcCoeffs();
	}

	/**
	 * Method for calculating the coefficients used in the Goertzel-algorithm
	 */
	private void calcCoeffs() {
		coeffs = new double[freqs.length];
		int i;
		for (i = 0; i < coeffs.length; i++) {
			coeffs[i] = 2.0 * Math.cos(2.0 * Math.PI * freqs[i] / sampleRate);
		}
	}

	/**
	 * Creates an outputList with the calculated values for each frequency.
	 * 
	 * @param inputSamples
	 *            The samples from "line-in" to be used in the calculations.
	 * @return Return an outputList as a double[].
	 */
	public double[] calcFreqWeight(double[] inputSamples) {
		double[] outputList = new double[freqs.length];

		for (int k = 0; k < coeffs.length; k++) {
			y2 = y1 = y0 = 0; // Reset the values before a new loop with a new
								// frequency is started.

			// Goertzel-algorithm.
			for (int n = 0; n < numSamples; n++) {
				y0 = (double) inputSamples[n] + coeffs[k] * y1 - y2;
				y2 = y1;
				y1 = y0;
			}
			// Weight calculation for each frequency (optimized
			// Goertzel-algorithm).
			outputList[k] = Math.pow(y1, 2) + Math.pow(y2, 2) - y1 * y2 * coeffs[k];
		}
		return outputList;
	}

	/**
	 * Creates an outputList with the calculated values for each frequency.
	 * 
	 * @param inputSamples
	 *            The samples from "line-in" to be used in the calculations.
	 * @return Return an outputList as a double[].
	 */
	public double[] calcFreqWeight() {
		double[] outputList = new double[freqs.length]; // Memory usage can be
														// optimized by having
														// only 1
														// outputListArray.

		for (int k = 0; k < coeffs.length; k++) {
			y2 = y1 = y0 = 0; // Reset the values before a new loop with a new
								// frequency is started.

			// Goertzel-algorithm.
			for (int n = 0; n < numSamples; n++) {
				y0 = (double) this.inputSamples[n] + coeffs[k] * y1 - y2;
				y2 = y1;
				y1 = y0;
			}
			// Weight calculation for each frequency (optimized
			// Goertzel-algorithm).
			outputList[k] = Math.pow(y1, 2) + Math.pow(y2, 2) - y1 * y2 * coeffs[k];
		}
		return outputList;
	}
}