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

/**
 * Class Goertzel returns an outputList with values that represents the weight
 * of selected frequencies in a signal. Calculations are performed by using the
 * optimized Goertzel-algorithm.
 * 
 * Details about the algorithm are found in the report on the DTMF-Decoder
 * Project
 * 
 * @author Tinotenda Chemvura
 * @version 04/01/2016
 */
public class GoertzelOptimised {

	private int[] fbin;
	private int Fs;
	private double[] coeffs;
	private double[] samples;
	private double[] magSquared;
	private boolean computed = false;

	/**
	 * Instantiate the object by initialising the coefficients to be used in the
	 * goertzel transform.
	 * 
	 * @param Fs
	 * @param samples
	 * @param fbin
	 */
	public GoertzelOptimised(int Fs, double[] samples, int[] fbin) {
		this.Fs = Fs;
		this.samples = samples;
		this.fbin = fbin;
		calculateCoefficients();
	}

	/**
	 * Instantiate the object by initialising the coefficients to be used in the
	 * goertzel transform.
	 * 
	 * @param Fs
	 * @param fbin
	 */
	public GoertzelOptimised(int Fs, int[] fbin) {
		this.Fs = Fs;
		this.fbin = fbin;
		calculateCoefficients();
	}

	/**
	 * Method to calculate the coefficients to be used in the goertzel
	 * calculation
	 */
	private void calculateCoefficients() {
		this.coeffs = new double[fbin.length];

		// coeff = 2 * c
		// c = cos(w)
		// s = sin(w)
		// k = (int)(0.5 x N x target x 1/Fs)
		// w = (2*π/N)*k

		// coeff = 2 * cos (target * pi / Fs)

		for (int i = 0; i < coeffs.length; i++) {
			coeffs[i] = 2.0 * Math.cos(2.0 * fbin[i] * Math.PI / Fs);
		}

	}

	/**
	 * Creates an outputList with the calculated values for each frequency.
	 * 
	 * @return Return an array of magnitude^s as a double[].
	 * 
	 * @throws DTMFDecoderException
	 *             When the object was not instantiated using the correct
	 *             constructor. The constructor to use for this method to be
	 *             usable is GoertzelOptimised(Fs, samples, fbin)
	 */
	public boolean compute() throws DTMFDecoderException {
		if (samples == null) {
			throw new DTMFDecoderException(
					"No samples have been provided. To use this method please instantiate the Goertzel object with the constructor GoertzelOptimised()");
		}
		double[] magSquared = new double[fbin.length];

		// for each frequency in the bin
		for (int f = 0; f < fbin.length; f++) {
			double q0, q1 = 0, q2 = 0;
			for (int s = 0; s < samples.length; s++) {

				// For each sample
				// Q0 = coeff * Q1 - Q2 + sample
				// Q2 = Q1
				// Q1 = Q0

				q0 = (coeffs[f] * q1) - q2 + samples[s];
				q2 = q1;
				q1 = q0;
			}
			// use the optimised goertzel algorithm to get the magnitude^2
			// magnitude2 = Q1^2 + Q2^2 - (Q1*Q2*coeff)

			magSquared[f] = (q1 * q1) + (q2 * q2) - (q1 * q2 * coeffs[f]);
		}
		this.magSquared = magSquared;
		computed = true;
		return true;
	}

	/**
	 * Creates an output list with the calculated magnitudes for each frequency
	 * using precalculated coefficients.
	 * 
	 * @param samples
	 *            The samples to be used in the calculations.
	 * @return Return an array of square of the magnitudes as a double[].
	 */
	public boolean compute(double[] samples) {

		double[] magSquared = new double[fbin.length];

		// for each frequency in the bin
		for (int f = 0; f < fbin.length; f++) {
			double q0, q1 = 0, q2 = 0;
			for (int s = 0; s < samples.length; s++) {

				// For each sample
				// Q0 = coeff * Q1 - Q2 + sample
				// Q2 = Q1
				// Q1 = Q0

				q0 = (coeffs[f] * q1) - q2 + samples[s];
				q2 = q1;
				q1 = q0;
			}
			// use the optimised goertzel algorithm to get the magnitude^2
			// magnitude2 = Q1^2 + Q2^2 - (Q1*Q2*coeff)

			magSquared[f] = (q1 * q1) + (q2 * q2) - (q1 * q2 * coeffs[f]);
		}
		this.magSquared = magSquared;
		computed = true;
		return true;
	}

	/**
	 * Method to get the power data for the transformed samples
	 * 
	 * @return Array with the magnitude squared of the powers
	 * @throws DTMFDecoderException
	 *             If compute has not been run yet or if decoding failed.
	 */
	public double[] getMagnitudeSquared() throws DTMFDecoderException {
		if (computed)
			return magSquared;
		else
			throw new DTMFDecoderException("Not yet decoded.");
	}

}
