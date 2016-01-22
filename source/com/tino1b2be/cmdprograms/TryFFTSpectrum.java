package com.tino1b2be.cmdprograms;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import com.tino1b2be.dtmfdecoder.DecoderUtil;
import com.tino1b2be.dtmfdecoder.Signals;

public class TryFFTSpectrum {

	public static void main(String[] args) {
		double startT = System.currentTimeMillis();
		final double Fs = 8000;
		final int N = 256;

		// *** Generate a signal
		double[] signal = new double[N];
		System.out.println("Frame length: " + N + " samples => " + (N / Fs) + "ms");

		final double f1 = 697.0, f2 = 1209; // Hz
		for (int ii = 0; ii < N; ii++) {
			signal[ii] = Math.sin(2.0 * Math.PI * f1 * (double) ii / Fs) + Math.sin(2.0 * Math.PI * f2 * (double) ii / Fs) ;
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
		final double center1 = (f1 / Fs) * N;
		final double center2 = (f2 / Fs) * N;
		
		int start, end;
//		start = (int) (center - 10.0);
//		end = (int) (center + 10.0);
		start = 0;
		end = powerSpectrum.length;
		
		System.out.println("Center frequency 1 in the FFT: " + center1);
		System.out.println("Center frequency 2 in the FFT: " + center2);
		for (int ii = start; ii < end; ii++) {
			System.out.format("% 3d (% 4.0fHz) power:%01.01f\n", ii, ii * Fs / N, powerSpectrum[ii]);
		}

		// *** Detect
		double totalPower = sum(powerSpectrum);
		double totalPower2 = Signals.power(signal);
		double totalPower3 = DecoderUtil.signalPower(signal);
		
		double signalPower = powerSpectrum[32] + powerSpectrum[31]  + powerSpectrum[33]+ powerSpectrum[55] + powerSpectrum[56] + powerSpectrum[57];
		double ratio = signalPower / totalPower;

		// around 80%. Reason being leakage in the FFT. The actual spectrum is a
		// sigmoid function with sidelobes. If you use e.g. a Hamming window,
		// the side-lobes are suppressed at the cost an even broader center
		// lobe. It's ok though, it just affects the width of the band where you
		// look for the actual frequency of interest.
		System.out.println("Detection ratio: " + ratio);
		
		double stopT = System.currentTimeMillis();
		System.out.println("Time taken = " + Double.toString((stopT-start)/1000) + "sec.");
	}

	private static double sum(double[] powerSpectrum) {
		double s = 0.0;
		for (int ii = 0; ii < powerSpectrum.length; ii++) {
			s += powerSpectrum[ii];
		}
		return s;
	}
}
