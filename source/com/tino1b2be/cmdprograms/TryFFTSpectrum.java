package com.tino1b2be.cmdprograms;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

public class TryFFTSpectrum {

	public static void main(String[] args) {
		final double Fs = 8000;
		final int N = 256;

		// *** Generate a signal with frequency f
		double[] signal = new double[N];
		System.out.println("Frame length: " + N + " samples => " + (N / Fs) + "ms");

		final double f = 1234.0; // Hz
		for (int ii = 0; ii < N; ii++) {
			signal[ii] = Math.sin(2.0 * Math.PI * f * (double) ii / Fs);
		}

		// Should apply hamming window to the frame.

		// *** Get power spectrum
		final FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);

		// Note: signal.length should have been a power of 2
		final Complex[] spectrum = fft.transform(signal, TransformType.FORWARD);
		final double[] powerSpectrum = new double[N / 2 + 1];
		for (int ii = 0; ii < powerSpectrum.length; ii++) {
			final double abs = spectrum[ii].abs();
			powerSpectrum[ii] = abs * abs;
		}

		// Expect a sharp peak at around frequency f - index f/Fs *
		// signal.length
		final double center = (f / Fs) * N;
		final int start = (int) (center - 10.0);
		final int end = (int) (center + 10.0);

		System.out.println("Center frequency in the FFT: " + center);
		for (int ii = start; ii < end; ii++) {
			System.out.format("% 3d (% 4.0fHz) power:%01.01f\n", ii, ii * Fs / N, powerSpectrum[ii]);
		}

		// *** Detect
		double totalPower = sum(powerSpectrum);
		double signalPower = powerSpectrum[39] + powerSpectrum[40];
		double ratio = signalPower / totalPower;

		// around 80%. Reason being leakage in the FFT. The actual spectrum is a
		// sigmoid function with sidelobes. If you use e.g. a Hamming window,
		// the side-lobes are suppressed at the cost an even broader center
		// lobe. It's ok though, it just affects the width of the band where you
		// look for the actual frequency of interest.
		System.out.println("Detection ratio: " + ratio);
	}

	private static double sum(double[] powerSpectrum) {
		double s = 0.0;
		for (double i : powerSpectrum)
			s += i;
		return s;
	}
}
