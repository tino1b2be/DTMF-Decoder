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

package com.tino1b2be.audio;

import java.io.IOException;

/**
 * Class to implement an audio file when an array of samples is used as an input
 * to the dtmfUtil
 * 
 * @author tino1b2be
 *
 */
public class TempAudio implements AudioFile {

	private double[] samples;
	private double[][] samples2;
	private int Fs;
	private int samplesRead = 0;
	private boolean mono = false;

	/**
	 * Constructor to use when the samples represent mono channeled audio.
	 * 
	 * @param samples Array of samples
	 * @param fs Sampling Frequency
	 * @throws AudioFileException
	 */
	public TempAudio(double[] samples, int fs) throws AudioFileException {
		if (fs < 8000)
			throw new AudioFileException("Sampling Frequency must be greater than 8kHz.");
		this.samples = samples;
		this.Fs = fs;
		mono = true;
	}

	/**
	 * Constructor to use when the samples represent stereo audio.
	 * 
	 * @param samples2
	 * @param fs Sampling Frequency
	 * @throws AudioFileException
	 */
	public TempAudio(double[][] samples, int fs) throws AudioFileException {
		if (fs < 8000)
			throw new AudioFileException("Sampling Frequency must be greater than 8kHz.");
		if (samples[0].length != samples[1].length)
			throw new AudioFileException("Both channels must contain the same number of samples.");
		this.samples2 = samples;
		this.Fs = fs;
	}

	@Override
	public int read(double[] buffer) throws AudioFileException {
		int read = 0;

		for (int i = 0; i < buffer.length; i++) {
			if (samplesRead == samples.length)
				throw new AudioFileException("No more samples to read.");
			buffer[i] = samples[i + samplesRead];
			read++;
			samplesRead++;
		}
		return read;
	}

	@Override
	public int read(double[][] buffer) throws AudioFileException {
		int read = 0;
		
		for (int i = 0; i < buffer.length; i++) {
			if (samplesRead == samples.length)
				throw new AudioFileException("No more samples to read.");
			buffer[0][i] = samples2[0][i + samplesRead];
			buffer[1][i] = samples2[1][i + samplesRead];
			read++;
			samplesRead++;
		}
		return read;
	}

	@Override
	public String getFileProperties() {
		if (mono)
			return "Temporary Audio File generated for the samples given.\nSampling Frequency = " + Fs
					+ "\nNumber of samples = " + samples.length;
		else
			return "Temporary Audio File generated for the samples given.\nSampling Frequency = " + Fs
					+ "\nNumber of samples = " + samples2[0].length;
	}

	@Override
	public int getSampleRate() {
		return Fs;
	}

	@Override
	public void close() throws IOException {
		samples = null;
		samples2 = null;
	}

	@Override
	public int getNumChannels() {
		if (mono)
			return 1;
		return 2;
	}
}
