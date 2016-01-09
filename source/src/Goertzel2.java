package src;

/**
 * Class Goertzel returns an outputList with values that represents the weight of selected frequencies in a signal.
 * Calculations are performed by using the optimized Goertzel-algorithm.
 * 
 * @author Gruppe 4 
 * @version 3/12 2008
 */
public class Goertzel2 {
	private int numSamples; // Number of samples.
	private int sampleRate; // SampleRate in Hz.
	private final int[] freqs = {697, 770, 852, 941, 1209, 1336, 1477}; // Frequencies in Hz.
	private double[] coeffs = new double[freqs.length]; // Array with calculated coefficients for each frequency.
	private double y0, y1, y2; // y0 current output, y1 previous output (n-1), y(2) output two samples ago (n-2).

	/**
	 * Initialize by calculating coefficients.
	 * 
	 * @param numSamples Number of samples to examine.
	 * @param sampleRate The sample rate.
	 */
	public Goertzel2(int numSamples, int sampleRate) {
		this.numSamples = numSamples;
		this.sampleRate = sampleRate;

		calcCoeffs();
	}

	/**
	 * Method for calculating the coefficients used in the Goertzel-algorithm
	 */
	private void calcCoeffs() {
		int i;
		for(i = 0; i < coeffs.length; i++) {
			coeffs[i] = 2.0 * Math.cos(2.0 * Math.PI * freqs[i] / sampleRate);
		}
	}

	/**
	 * Creates an outputList with the calculated values for each frequency.
	 * 
	 * @param inputSamples The samples from "line-in" to be used in the calculations.
	 * @return Return an outputList as a double[].
	 */
	public double[] calcFreqWeight(byte[] inputSamples) {
		double[] outputList = new double[freqs.length]; // Memory usage can be optimized by having only 1 outputListArray.

		for(int k = 0; k < coeffs.length; k++) {
			y2 = y1 = y0 = 0; // Reset the values before a new loop with a new frequency is started.
			
			// Goertzel-algorithm.
			for(int n = 0; n < numSamples; n++) {
				y0 = (double) inputSamples[n] + coeffs[k] * y1 - y2;
				y2 = y1;
				y1 = y0;
			}
			// Weight calculation for each frequency (optimized Goertzel-algorithm).
			outputList[k] = Math.pow(y1, 2) + Math.pow(y2, 2) - y1 * y2 * coeffs[k];
		}
		return outputList;
	}
}