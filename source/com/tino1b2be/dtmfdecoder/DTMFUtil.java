/*
 * The MIT License (MIT)
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import javax.sound.sampled.UnsupportedAudioFileException;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import com.tino1b2be.audio.AudioFile;
import com.tino1b2be.audio.AudioFileException;
import com.tino1b2be.audio.TempAudio;
import com.tino1b2be.audio.WavFileException;

/**
 * Class to decode DTMF signals in a supported audio file.
 * 
 * @author Tinotenda Chemvura
 *
 */
public class DTMFUtil {

	/**
	 * True if the decoder is to be used in debug mode. False by default
	 */

	public static boolean debug = false;
	/**
	 * True if decoder is to use the goertzel algorithm instead of the FFT False
	 * by default
	 */
	public static boolean goertzel = false;

	private static final double CUT_OFF_POWER = 0.004;
	private static final double FFT_CUT_OFF_POWER_NOISE_RATIO = 0.46;
	private static final double FFT_FRAME_DURATION = 0.030;
	private static final double GOERTZEL_CUT_OFF_POWER_NOISE_RATIO = 0.87;
	private static final double GOERTZEL_FRAME_DURATION = 0.045;

	private boolean decoded;
	private static boolean decode60 = false;
	private static boolean decode80 = false;
	private static boolean decode100 = false;

	private boolean decoder = false;
	private boolean generate = false;

	private String seq[];
	private AudioFile audio;
	private int frameSize;

	private static int[] freqIndicies;

	/**
	 * The list of valid DTMF frequencies that are going to be processed and
	 * searched for within the ITU-T recommendations . See the <a href=
	 * "http://en.wikipedia.org/wiki/Dual-tone_multi-frequency_signaling" >
	 * WikiPedia article on DTMF</a>.
	 */
	public static final int[] DTMF_FREQUENCIES_BIN = { 
			687, 697, 707, // 697
			758, 770, 782, // 770
			839, 852, 865, // 852
			927, 941, 955, // 941
			1191, 1209, 1227, // 1209
			1316, 1336, 1356, // 1336
			1455, 1477, 1499, // 1477
			1609, 1633, 1647, 1657 // 1633
	};

	/**
	 * The list of valid DTMF frequencies. See the <a href=
	 * "http://en.wikipedia.org/wiki/Dual-tone_multi-frequency_signaling" >
	 * WikiPedia article on DTMF</a>.
	 */
	public static final int[] DTMF_FREQUENCIES = { 697, 770, 852, 941, 1209, 1336, 1477, 1633 };

	/**
	 * The list of valid DTMF characters. See the <a href=
	 * "http://en.wikipedia.org/wiki/Dual-tone_multi-frequency_signaling" >
	 * WikiPedia article on DTMF</a>.
	 */
	public static final char[] DTMF_CHARACTERS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd',
			'A', 'B', 'C', 'D' };

	// generation variables
	private double[] generatedSeq;
	private int outPauseDurr;
	private int outToneDurr;
	private double outFs;
	private File outFile;
	private char[] outChars;
	private boolean generated;

	/**
	 * Constructor to decode an array of samples
	 * 
	 * @param samples
	 *            array of samples (Mono channel)
	 * @param Fs
	 *            Sampling Frequency
	 * @throws AudioFileException
	 * @throws DTMFDecoderException
	 */
	public DTMFUtil(double[] samples, int Fs) throws AudioFileException, DTMFDecoderException {
		// create an audio file object and export to a temp location
		// load the temp audio file and decode
		this.decoder = true;
		audio = new TempAudio(samples, Fs);
		setFrameSize();
		setCentreIndicies();
		this.decoded = false;
		seq = new String[2];
		this.seq[0] = "";
		this.seq[1] = "";
	}

	/**
	 * Constructor to decode an array of samples
	 * 
	 * @param samples
	 *            array of samples (Stereo channel)
	 * @param Fs
	 *            Sampling Frequency
	 * @throws AudioFileException
	 * @throws DTMFDecoderException
	 */
	public DTMFUtil(double[][] samples, int Fs) throws AudioFileException, DTMFDecoderException {
		// create an audio file object and export to a temp location
		// load the temp audio file and decode
		this.decoder = true;
		audio = new TempAudio(samples, Fs);
		setFrameSize();
		setCentreIndicies();
		this.decoded = false;
		seq = new String[2];
		this.seq[0] = "";
		this.seq[1] = "";
	}

	/**
	 * Constructor used to Decode an audio file
	 * 
	 * @param data
	 *            AudioFile object to be processed.
	 * @throws DTMFDecoderException
	 */
	public DTMFUtil(AudioFile data) throws DTMFDecoderException {
		this.decoder = true;
		this.audio = data;
		setFrameSize();
		if (!goertzel)
			setCentreIndicies();
		this.decoded = false;
		seq = new String[2];
		this.seq[0] = "";
		this.seq[1] = "";
	}

	/**
	 * Constructor used to Decode an audio file
	 * 
	 * @param filename
	 *            Filename of the audio file to be processed
	 * @throws Exception
	 * @throws AudioFileException
	 * @throws UnsupportedAudioFileException
	 * @throws IOException
	 * @throws WavFileException
	 * @throws DTMFDecoderException
	 */
	public DTMFUtil(String filename) throws AudioFileException, IOException, DTMFDecoderException {
		this.decoder = true;
		this.audio = FileUtil.readAudioFile(filename);
		setFrameSize();
		if (!goertzel)
			setCentreIndicies();
		this.decoded = false;
		seq = new String[2];
		this.seq[0] = "";
		this.seq[1] = "";
	}

	/**
	 * Constructor used to Decode an audio file
	 * 
	 * @param file
	 *            File object for the audio file
	 * @throws Exception
	 * @throws AudioFileException
	 * @throws UnsupportedAudioFileException
	 * @throws IOException
	 * @throws WavFileException
	 * @throws DTMFDecoderException
	 */
	public DTMFUtil(File file) throws AudioFileException, IOException, DTMFDecoderException {
		this.decoder = true;
		this.audio = FileUtil.readAudioFile(file);
		setFrameSize();
		if (!goertzel)
			setCentreIndicies();
		this.decoded = false;
		seq = new String[2];
		this.seq[0] = "";
		this.seq[1] = "";
	}

	/**
	 * Constructor to be used to generate a sequence of DTMF tones.
	 * 
	 * @param file
	 * 
	 * @param chars
	 *            Array with the sequence of DTMF characters.
	 * @param Fs
	 *            Sampling Frequency
	 * @param toneDurr
	 *            Duration of a tone.
	 * @param pauseDurr
	 *            Duration of a pause
	 * @throws DTMFDecoderException
	 *             If the given characters are not valid DTMF characters
	 */
	public DTMFUtil(File file, char[] chars, int fs, int toneDurr, int pauseDurr) throws DTMFDecoderException {
		this.generate = true;
		setChars(chars);
		if (fs < 8000)
			throw new DTMFDecoderException("Sampling frequency must be at least 8kHz.");
		this.outFs = fs;
		if (toneDurr < 40)
			throw new DTMFDecoderException("Tone duration should be greater than 40ms.");
		this.outToneDurr = toneDurr;
		if (toneDurr < 30)
			throw new DTMFDecoderException("Pause duration should be greater than 30ms.");
		this.outPauseDurr = pauseDurr;
		this.outFile = file;
	}

	/**
	 * Constructor to be used to generate a sequence of DTMF tones.
	 * 
	 * @param file
	 * 
	 * @param chars
	 *            Array with the sequence of DTMF characters.
	 * @param Fs
	 *            Sampling Frequency
	 * @param toneDurr
	 *            Duration of a tone.
	 * @param pauseDurr
	 *            Duration of a pause
	 * @throws DTMFDecoderException
	 *             If the given characters are not valid DTMF characters
	 */
	public DTMFUtil(String filename, char[] chars, int fs, int toneDurr, int pauseDurr) throws DTMFDecoderException {
		this.generate = true;
		setChars(chars);
		this.outFs = fs;
		this.outToneDurr = toneDurr;
		this.outPauseDurr = pauseDurr;
		this.outFile = new File(filename);
	}

	/**
	 * Check if the characters are valid and set the characters
	 * 
	 * @param chars
	 *            Characters to be generated
	 * @throws DTMFDecoderException
	 */
	private void setChars(char[] chars) throws DTMFDecoderException {
		outChars = new char[chars.length];
		char[] cc = Arrays.copyOf(DTMF_CHARACTERS, DTMF_CHARACTERS.length);
		Arrays.sort(cc);
		for (int c = 0; c < chars.length; c++) {
			if (Arrays.binarySearch(cc, chars[c]) < 0)
				throw new DTMFDecoderException("The character \"" + chars[c] + "\" is not a DTMF character.");
			else
				outChars[c] = chars[c];
		}
	}

	/**
	 * Method to precalculate the indices to be used to locate the DTMF
	 * frequencies in the power spectrum
	 */
	private void setCentreIndicies() {
		freqIndicies = new int[DTMF_FREQUENCIES_BIN.length];
		for (int i = 0; i < freqIndicies.length; i++) {
			int ind = (int) Math.round(((DTMF_FREQUENCIES_BIN[i] * 1.0) / (audio.getSampleRate()) * 1.0) * frameSize);
			freqIndicies[i] = ind;
		}
	}

	/**
	 * Method to set the frame size for the decoding process. Framesize must be
	 * a power of 2
	 * 
	 * @throws DTMFDecoderException
	 *             If Fs if less than 8kHz or loo large.
	 */
	private void setFrameSize() throws DTMFDecoderException {
		if (audio.getSampleRate() < 8000)
			throw new DTMFDecoderException("Sampling Rate cannot be less than 8kHz.");
		if (goertzel) {
			this.frameSize = (int) Math.floor(GOERTZEL_FRAME_DURATION * audio.getSampleRate());
		} else {
			int size = 0;
			for (int i = 8; i <= 15; i++) {
				size = (int) Math.pow(2, i);
				if (size / (audio.getSampleRate() * 1.0) < FFT_FRAME_DURATION)
					continue;
				else {
					frameSize = size;
					return;
				}
			}
			throw new DTMFDecoderException(
					"Sampling Frequency of the audio file is too high. Please use a file with a lower Sampling Frequency.");
		}
	}

	/**
	 * Method to filter out the power spectrum information for the DTMF
	 * frequencies given an array of power spectrum information from an FFT.
	 * 
	 * @param frame
	 *            Frame with power spectrum information to be processed
	 * @return an array with 8 doubles. Each representing the magnitude of the
	 *         corresponding dtmf frequency
	 */
	private static double[] filterFrame(double[] frame) {
		double[] out = new double[8];

		// 687, 697, 707, // 697 0,1,2
		// 758, 770, 782, // 770 3,4,5
		// 839, 852, 865, // 852 6,7,8
		// 927, 941, 955, // 941 9,10,11
		// 1191, 1209, 1227, // 1209 12,13,14
		// 1316, 1336, 1356, // 1336 15,16,17
		// 1455, 1477, 1499, // 1477 18,19,20
		// 1609, 1633, 1647, 1657 // 1633 21,22,23,24

		// 687, 697, 707, // 697 0,1,2
		out[0] = frame[freqIndicies[0]];
		if (freqIndicies[0] != freqIndicies[1])
			out[0] += frame[freqIndicies[1]];
		if (freqIndicies[0] != freqIndicies[2] && freqIndicies[1] != freqIndicies[2])
			out[0] += frame[freqIndicies[2]];

		// 758, 770, 782, // 770 3,4,5
		out[1] = frame[freqIndicies[3]];
		if (freqIndicies[3] != freqIndicies[4])
			out[1] += frame[freqIndicies[4]];
		if (freqIndicies[3] != freqIndicies[5] && freqIndicies[4] != freqIndicies[5])
			out[1] += frame[freqIndicies[5]];

		// 839, 852, 865, // 852 6,7,8
		out[2] = frame[freqIndicies[6]];
		if (freqIndicies[6] != freqIndicies[7])
			out[2] += frame[freqIndicies[7]];
		if (freqIndicies[6] != freqIndicies[8] && freqIndicies[7] != freqIndicies[8])
			out[2] += frame[freqIndicies[8]];

		// 927, 941, 955, // 941 9,10,11
		out[3] = frame[freqIndicies[9]];
		if (freqIndicies[9] != freqIndicies[10])
			out[3] += frame[freqIndicies[10]];
		if (freqIndicies[9] != freqIndicies[11] && freqIndicies[10] != freqIndicies[11])
			out[3] += frame[freqIndicies[11]];

		// 1191, 1209, 1227, // 1209 12,13,14
		out[4] = frame[freqIndicies[12]];
		if (freqIndicies[12] != freqIndicies[13])
			out[4] += frame[freqIndicies[13]];
		if (freqIndicies[12] != freqIndicies[14] && freqIndicies[13] != freqIndicies[14])
			out[5] += frame[freqIndicies[14]];

		// 1316, 1336, 1356, // 1336 15,16,17
		out[5] = frame[freqIndicies[15]];
		if (freqIndicies[15] != freqIndicies[16])
			out[5] += frame[freqIndicies[16]];
		if (freqIndicies[15] != freqIndicies[17] && freqIndicies[16] != freqIndicies[17])
			out[5] += frame[freqIndicies[17]];

		// 1455, 1477, 1499, // 1477 18,19,20
		out[6] = frame[freqIndicies[18]];
		if (freqIndicies[18] != freqIndicies[19])
			out[6] += frame[freqIndicies[19]];
		if (freqIndicies[18] != freqIndicies[20] && freqIndicies[19] != freqIndicies[20])
			out[6] += frame[freqIndicies[20]];

		out[7] = frame[freqIndicies[21]];
		if (frame[freqIndicies[22]] != frame[freqIndicies[21]])
			out[7] += frame[freqIndicies[22]];
		else
			out[7] += frame[freqIndicies[23]];
		out[7] += frame[freqIndicies[24]];

		return out;
	}

	/**
	 * Method returns the DTMF sequence
	 * 
	 * @return char array with the keys represented in the file
	 * @throws DTMFDecoderException
	 *             Throws excepion when the file has not been decoded yet.
	 */
	public String[] getDecoded() throws DTMFDecoderException {
		if (!decoded)
			throw new DTMFDecoderException("File has not been decoded yet. Please run the method decode() first!");
		return seq;
	}

	/**
	 * Method to generate a frequency spectrum of the frame using FFT
	 * 
	 * @param frame
	 *            Frame to be transformed
	 * @param Fs
	 *            Sampling Frequency
	 * @return an Array showing the realtive powers of all frequencies
	 */
	private static double[] transformFrameFFT(double[] frame, int Fs) {
		final FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
		final Complex[] spectrum = fft.transform(frame, TransformType.FORWARD);
		final double[] powerSpectrum = new double[frame.length / 2 + 1];
		for (int ii = 0; ii < powerSpectrum.length; ii++) {
			final double abs = spectrum[ii].abs();
			powerSpectrum[ii] = abs * abs;
		}
		return powerSpectrum;
	}

	/**
	 * Method to generate a frequency spectrum of the frame using Goertzel
	 * Algorithm
	 * 
	 * @param frame
	 *            Frame to be transformed
	 * @param Fs
	 *            Sampling Frequency
	 * @return an Array showing the realtive powers of the DTMF frequencies
	 * @throws DTMFDecoderException
	 *             If no samples have been provided for the goertze class to
	 *             transform
	 */
	private static double[] transformFrameG(double[] frame, int Fs) throws DTMFDecoderException {
		double[] out;
		GoertzelOptimised g = new GoertzelOptimised(Fs, frame, DTMF_FREQUENCIES_BIN);
		// 1. transform the frames using goertzel algorithm
		// 2. get the highest DTMF freq within the tolerance range and use that
		// magnitude to represet the corresponsing DTMF free
		if (g.compute()) {
			out = filterFrameG(g.getMagnitudeSquared());
			return out;
		} else {
			throw new DTMFDecoderException("Decoding failed.");
		}
	}

	/**
	 * Method to get the highest DTMF freq within the tolerance range and use
	 * that magnitude to represet the corresponsing DTMF freq
	 * 
	 * @param frame
	 *            Frame with 274 magnitudes to be processed
	 * @return an array with 8 magnitudes. Each representing the magnitude of
	 *         each frequency
	 */
	private static double[] filterFrameG(double[] frame) {
		double[] out = new double[8];
		out[0] = DecoderUtil.max(Arrays.copyOfRange(frame, 0, 3));
		out[1] = DecoderUtil.max(Arrays.copyOfRange(frame, 3, 6));
		out[2] = DecoderUtil.max(Arrays.copyOfRange(frame, 6, 9));
		out[3] = DecoderUtil.max(Arrays.copyOfRange(frame, 9, 12));
		out[4] = DecoderUtil.max(Arrays.copyOfRange(frame, 12, 15));
		out[5] = DecoderUtil.max(Arrays.copyOfRange(frame, 15, 18));
		out[6] = DecoderUtil.max(Arrays.copyOfRange(frame, 18, 21));
		out[7] = DecoderUtil.max(Arrays.copyOfRange(frame, 21, 25));
		return out;
	}

	/**
	 * Method to detect whether a frame is too noisy for detection
	 * 
	 * @param dft_data
	 *            Frequency spectrum magnitudes for the DTMF frequencies
	 * @param power_spectrum
	 * @return true is noisy or false if it is acceptable
	 */
	private boolean isNoisy(double[] dft_data, double[] power_spectrum) {
		if (power_spectrum == null)
			return true;
		// sum the powers of all frequencies = sum
		// find ratio of the (sum of two highest peaks) : sum
		double[] temp1 = Arrays.copyOfRange(dft_data, 0, 4);
		double[] temp2 = Arrays.copyOfRange(dft_data, 4, 8);
		Arrays.sort(temp1);
		Arrays.sort(temp2);
		// ratio = (max(lower freqs) + max(higher freqs))/sum(all freqs in
		// spectrum)
		return ((temp1[temp1.length - 1] + temp2[temp2.length - 1])
				/ DecoderUtil.sumArray(power_spectrum)) < FFT_CUT_OFF_POWER_NOISE_RATIO;
	}

	private boolean isNoisyG(double[] dft_data) {
		// sum the powers of all frequencies = sum
		// find ratio of the (sum of two highest peaks) : sum
		double[] temp1 = Arrays.copyOfRange(dft_data, 0, 4);
		double[] temp2 = Arrays.copyOfRange(dft_data, 4, 8);
		Arrays.sort(temp1);
		Arrays.sort(temp2);
		double one = temp1[temp1.length - 1];
		double two = temp2[temp2.length - 1];
		double sum = DecoderUtil.sumArray(dft_data);
		return ((one + two) / sum) < GOERTZEL_CUT_OFF_POWER_NOISE_RATIO;
	}

	/**
	 * Method to decode a frame given the frequency spectrum information of the
	 * frame
	 * 
	 * @param dft_data
	 *            Frequency spectrum information showing the relative magnitudes
	 *            of the power of each DTMF frequency
	 * @return DTMF charatcter represented by the frame
	 * @throws DTMFDecoderException
	 */
	private static char getRawChar(double[] dft_data) throws DTMFDecoderException {
		char out = 0;
		int low, hi;
		double[] lower = Arrays.copyOfRange(dft_data, 0, 4);
		double[] higher = Arrays.copyOfRange(dft_data, 4, 8);

		low = DecoderUtil.maxIndex(lower);
		hi = DecoderUtil.maxIndex(higher);

		if (low == 0) { // low = 697
			if (hi == 0) { // High = 1209
				out = '1';
			} else if (hi == 1) { // high = 1336
				out = '2';
			} else if (hi == 2) { // high = 1477
				out = '3';
			} else if (hi == 3) { // high = 1633
				out = 'A';
			} else
				throw new DTMFDecoderException("Something went terribly wrong!");

		} else if (low == 1) { // low = 770
			if (hi == 0) { // high = 1209
				out = '4';
			} else if (hi == 1) { // high = 1336
				out = '5';
			} else if (hi == 2) { // high = 1477
				out = '6';
			} else if (hi == 3) { // high = 1633
				out = 'B';
			} else
				throw new DTMFDecoderException("Something went terribly wrong!");

		} else if (low == 2) { // low = 852
			if (hi == 0) { // high = 1209
				out = '7';
			} else if (hi == 1) { // high = 1336
				out = '8';
			} else if (hi == 2) { // high = 1477
				out = '9';
			} else if (hi == 3) { // high = 1633
				out = 'C';
			} else
				throw new DTMFDecoderException("Something went terribly wrong!");

		} else if (low == 3) { // low = 941
			if (hi == 0) { // high = 1209
				out = '*';
			} else if (hi == 1) { // high = 1336
				out = '0';
			} else if (hi == 2) { // high = 1477
				out = '#';
			} else if (hi == 3) { // high = 1633
				out = 'D';
			} else
				throw new DTMFDecoderException("Something went terribly wrong!");
		} else
			throw new DTMFDecoderException("Something went terribly wrong!");
		return out;
	}

	/**
	 * Method to decode the wav file.
	 * 
	 * @return String representation of the sequence of DTMF tones represented
	 *         in the wav file
	 * @throws IOException
	 * @throws AudioFileException
	 * @throws WavFileException
	 */
	private void decodeMono40() throws IOException, AudioFileException {
		char prev = '_';
		char prev2 = '_';
		String seq2 = "";
		String seq22 = "";
		int count = 0;
		do {

			char curr;
			try {
				curr = decodeNextFrameMono();
			} catch (DTMFDecoderException e) {
				break;
			}
			if (debug)
				System.out.print(curr);
			// System.out.print(curr);
			if (curr != '_') {
				if (curr == prev) { // eliminate false positives
					if (curr != prev2) {
						if (debug) {
							seq22 = seq22.substring(0, seq22.length() - 1);
							seq22 += curr + ".";
							count = 0;
							seq2 += curr;
						} else {
							seq22 += curr + ".";
							seq2 += curr;
						}

					}
				}
			}
			if (count % 50 == 0) {
				seq22 += '_';
			}
			count++;
			prev2 = prev;
			prev = curr;
		} while (true);
		seq[0] = seq2;
	}

	/**
	 * Method to decode the wav file.
	 * 
	 * @return String representation of the sequence of DTMF tones represented
	 *         in the wav file
	 * @throws IOException
	 * @throws AudioFileException
	 * @throws WavFileException
	 */
	private void decodeMono60() throws IOException, AudioFileException {
		char prev = '_';
		char prev2 = '_';
		char prev3 = '_';
		String seq2 = "";
		String seq22 = "";
		int count = 0;
		do {

			char curr;
			try {
				curr = decodeNextFrameMono();
			} catch (DTMFDecoderException e) {
				break;
			}
			if (debug)
				System.out.print(curr);
			// System.out.print(curr);
			if (curr != '_') {
				if (curr == prev && curr == prev2) { // eliminate false
														// positives
					if (curr != prev3) {
						if (debug) {
							seq22 = seq22.substring(0, seq22.length() - 1);
							seq22 += curr + ".";
							count = 0;
							seq2 += curr;
						} else {
							seq22 += curr + ".";
							seq2 += curr;
						}

					}
				}
			}
			if (count % 30 == 0) {
				seq22 += '_';
			}
			count++;
			prev3 = prev2;
			prev2 = prev;
			prev = curr;
		} while (true);
		seq[0] = seq2;
	}

	/**
	 * Method to decode the wav file.
	 * 
	 * @return String representation of the sequence of DTMF tones represented
	 *         in the wav file
	 * @throws IOException
	 * @throws AudioFileException
	 * @throws WavFileException
	 */
	private void decodeMono80() throws IOException, AudioFileException {
		char prev = '_';
		char prev2 = '_';
		char prev3 = '_';
		char prev4 = '_';
		String seq2 = "";
		String seq22 = "";
		int count = 0;
		do {

			char curr;
			try {
				curr = decodeNextFrameMono();
			} catch (DTMFDecoderException e) {
				break;
			}
			if (debug)
				System.out.print(curr);
			// System.out.print(curr);
			if (curr != '_') {
				if (curr == prev && curr == prev2 && curr == prev3) { // eliminate
																		// false
																		// positives
					if (curr != prev4) {
						if (debug) {
							seq22 = seq22.substring(0, seq22.length() - 1);
							seq22 += curr + ".";
							count = 0;
							seq2 += curr;
						} else {
							seq22 += curr + ".";
							seq2 += curr;
						}

					}
				}
			}
			if (count % 100 == 0) {
				seq22 += '_';
			}
			count++;
			prev4 = prev3;
			prev3 = prev2;
			prev2 = prev;
			prev = curr;
		} while (true);
		seq[0] = seq2;
	}

	/**
	 * Method to decode the wav file.
	 * 
	 * @return String representation of the sequence of DTMF tones represented
	 *         in the wav file
	 * @throws IOException
	 * @throws AudioFileException
	 * @throws WavFileException
	 */
	private void decodeMono100() throws IOException, AudioFileException {
		char prev = '_';
		char prev2 = '_';
		char prev3 = '_';
		char prev4 = '_';
		char prev5 = '_';
		String seq2 = "";
		String seq22 = "";
		int count = 0;
		do {

			char curr;
			try {
				curr = decodeNextFrameMono();
			} catch (DTMFDecoderException e) {
				break;
			}
			if (debug)
				System.out.print(curr);
			// System.out.print(curr);
			if (curr != '_') {
				if (curr == prev && curr == prev2 && curr == prev3 && curr == prev4) { // eliminate
																						// false
																						// positives
					if (curr != prev5) {
						if (debug) {
							seq22 = seq22.substring(0, seq22.length() - 1);
							seq22 += curr + ".";
							count = 0;
							seq2 += curr;
						} else {
							seq22 += curr + ".";
							seq2 += curr;
						}

					}
				}
			}
			if (count % 100 == 0) {
				seq22 += '_';
			}
			count++;
			prev5 = prev4;
			prev4 = prev3;
			prev3 = prev2;
			prev2 = prev;
			prev = curr;
		} while (true);
		seq[0] = seq2;
	}

	/**
	 * Method to decode the wav file.
	 * 
	 * @return String representation of the sequence of DTMF tones represented
	 *         in the wav file
	 * @throws IOException
	 * @throws WavFileException
	 */
	private void decodeStereo40() throws IOException, AudioFileException {
		char curr[];
		char[] prev = { '_', '_' };
		char[] prev2 = { '_', '_' };
		String[] seq2 = { "", "" };
		do {

			try {
				curr = decodeNextFrameStereo();
			} catch (DTMFDecoderException e) {
				break;
			}

			// decode channel 1
			if (curr[0] != '_') {
				if (curr[0] == prev[0]) { // eliminate false positives
					if (curr[0] != prev2[0]) {
						seq2[0] += curr[0];
					}
				}
			}
			prev2[0] = prev[0];
			prev[0] = curr[0];

			// decode channel 2
			if (curr[1] != '_') {
				if (curr[1] == prev[1]) { // eliminate false positives
					if (curr[1] != prev2[1]) {
						seq2[1] += curr[1];
					}
				}
			}
			prev2[1] = prev[1];
			prev[1] = curr[1];

		} while (true);
		seq = seq2;
	}

	/**
	 * Method to decode the wav file.
	 * 
	 * @return String representation of the sequence of DTMF tones represented
	 *         in the wav file
	 * @throws IOException
	 * @throws WavFileException
	 */
	private void decodeStereo60() throws IOException, AudioFileException {
		char curr[];
		char[] prev = { '_', '_' };
		char[] prev2 = { '_', '_' };
		char[] prev3 = { '_', '_' };
		String[] seq2 = { "", "" };
		do {

			try {
				curr = decodeNextFrameStereo();
			} catch (DTMFDecoderException e) {
				break;
			}

			// decode channel 1
			if (curr[0] != '_') {
				if (curr[0] == prev[0] && curr[0] == prev2[0]) { // eliminate
																	// false
																	// positives
					if (curr[0] != prev3[0]) {
						seq2[0] += curr[0];
					}
				}
			}
			prev3[0] = prev2[0];
			prev2[0] = prev[0];
			prev[0] = curr[0];

			// decode channel 2
			if (curr[1] != '_') {
				if (curr[1] == prev[1] && curr[1] == prev2[1]) { // eliminate
																	// false
																	// positives
					if (curr[1] != prev3[1]) {
						seq2[1] += curr[1];
					}
				}
			}
			prev3[1] = prev2[1];
			prev2[1] = prev[1];
			prev[1] = curr[1];

		} while (true);
		seq = seq2;
	}

	/**
	 * Method to decode the wav file.
	 * 
	 * @return String representation of the sequence of DTMF tones represented
	 *         in the wav file
	 * @throws IOException
	 * @throws WavFileException
	 */
	private void decodeStereo80() throws IOException, AudioFileException {
		char curr[];
		char[] prev = { '_', '_' };
		char[] prev2 = { '_', '_' };
		char[] prev3 = { '_', '_' };
		char[] prev4 = { '_', '_' };
		String[] seq2 = { "", "" };
		do {

			try {
				curr = decodeNextFrameStereo();
			} catch (DTMFDecoderException e) {
				break;
			}

			// decode channel 1
			if (curr[0] != '_') {
				if (curr[0] == prev[0] && curr[0] == prev2[0] && curr[0] == prev3[0]) { // eliminate
																						// false
																						// positives
					if (curr[0] != prev4[0]) {
						seq2[0] += curr[0];
					}
				}
			}
			prev4[0] = prev3[0];
			prev3[0] = prev2[0];
			prev2[0] = prev[0];
			prev[0] = curr[0];

			// decode channel 2
			if (curr[1] != '_') {
				if (curr[1] == prev[1] && curr[1] == prev2[1] && curr[1] == prev3[1]) { // eliminate
																						// false
																						// positives
					if (curr[1] != prev4[1]) {
						seq2[1] += curr[1];
					}
				}
			}
			prev4[1] = prev3[1];
			prev3[1] = prev2[1];
			prev2[1] = prev[1];
			prev[1] = curr[1];

		} while (true);
		seq = seq2;
	}

	/**
	 * Method to decode the wav file.
	 * 
	 * @return String representation of the sequence of DTMF tones represented
	 *         in the wav file
	 * @throws IOException
	 * @throws WavFileException
	 */
	private void decodeStereo100() throws IOException, AudioFileException {
		char curr[];
		char[] prev = { '_', '_' };
		char[] prev2 = { '_', '_' };
		char[] prev3 = { '_', '_' };
		char[] prev4 = { '_', '_' };
		char[] prev5 = { '_', '_' };
		String[] seq2 = { "", "" };
		do {

			try {
				curr = decodeNextFrameStereo();
			} catch (DTMFDecoderException e) {
				break;
			}

			// decode channel 1
			if (curr[0] != '_') {
				if (curr[0] == prev[0] && curr[0] == prev2[0] && curr[0] == prev3[0] && curr[0] == prev4[0]) { // eliminate
																												// false
																												// positives
					if (curr[0] != prev5[0]) {
						seq2[0] += curr[0];
					}
				}
			}
			prev5[0] = prev4[0];
			prev4[0] = prev3[0];
			prev3[0] = prev2[0];
			prev2[0] = prev[0];
			prev[0] = curr[0];

			// decode channel 2
			if (curr[1] != '_') {
				if (curr[1] == prev[1] && curr[1] == prev2[1] && curr[1] == prev3[1] && curr[1] == prev4[1]) { // eliminate
																												// false
																												// positives
					if (curr[1] != prev5[1]) {
						seq2[1] += curr[1];
					}
				}
			}
			prev5[1] = prev4[1];
			prev4[1] = prev3[1];
			prev3[1] = prev2[1];
			prev2[1] = prev[1];
			prev[1] = curr[1];

		} while (true);
		seq = seq2;
	}

	/**
	 * Method to decode the next frame in a buffer of a mono channeled wav file
	 * 
	 * @return the decoded DTMF character
	 * @throws AudioFileException
	 * @throws IOException
	 * @throws WavFileException
	 * @throws DTMFDecoderException
	 */
	private char decodeNextFrameMono() throws AudioFileException, DTMFDecoderException, IOException {
		int bufferSize = (int) Math.ceil(frameSize / 3.0);
		double[] buffer = new double[bufferSize];
		double[] tempBuffer11 = new double[bufferSize];
		double[] tempBuffer21 = new double[bufferSize];

		int framesRead = audio.read(buffer);
		if (framesRead < bufferSize) {
			audio.close();
			throw new DTMFDecoderException("Out of frames");
		}
		double[] frame;
		if (goertzel) {
			frame = DecoderUtil.concatenateAll(tempBuffer21, tempBuffer11, buffer);
			tempBuffer21 = tempBuffer11;
			tempBuffer11 = buffer;
		} else {
			// slice off the extra bit to make the framesize a power of 2
			int slice = buffer.length + tempBuffer11.length + tempBuffer21.length - frameSize;
			double[] sliced = Arrays.copyOfRange(buffer, 0, buffer.length - slice);

			frame = DecoderUtil.concatenateAll(tempBuffer21, tempBuffer11, sliced);
			tempBuffer21 = tempBuffer11;
			tempBuffer11 = buffer;
		}

		char out;
		// check if the power of the signal is high enough to be accepted.
		if (DecoderUtil.signalPower(frame) < CUT_OFF_POWER)
			return '_';

		if (goertzel) {
			// transform frame and return frequency spectrum information
			double[] dft_data = DTMFUtil.transformFrameG(frame, (int) audio.getSampleRate());

			// check if the frame has too much noise
			if (isNoisyG(dft_data))
				return '_';

			out = DTMFUtil.getRawChar(dft_data);
			return out;

		} else {
			// transform frame and return frequency spectrum information
			double[] power_spectrum = DTMFUtil.transformFrameFFT(frame, (int) audio.getSampleRate());

			// filter out the 8 DTMF frequencies from the power spectrum
			double[] dft_data = filterFrame(power_spectrum);

			// check if the frame has too much noise
			if (isNoisy(dft_data, power_spectrum))
				return '_';

			out = DTMFUtil.getRawChar(dft_data);
			return out;
		}
	}

	/**
	 * Method to decode the next frame in a buffer of a stereo wav file
	 * 
	 * @return the decoded DTMF character
	 * @throws IOException
	 * @throws WavFileException
	 * @throws DTMFDecoderException
	 */
	private char[] decodeNextFrameStereo() throws IOException, AudioFileException, DTMFDecoderException {
		int bufferSize = (int) Math.ceil(frameSize / 3.0);
		double[][] buffer = new double[2][bufferSize];
		double[] tempBuffer11 = new double[bufferSize];
		double[] tempBuffer21 = new double[bufferSize];
		double[] tempBuffer12 = new double[bufferSize];
		double[] tempBuffer22 = new double[bufferSize];

		int framesRead = audio.read(buffer);
		if (framesRead < bufferSize) {
			audio.close();
			throw new DTMFDecoderException("Out of frames");
		}
		double[] frame1, frame2;
		if (goertzel) {
			frame1 = DecoderUtil.concatenateAll(tempBuffer21, tempBuffer11, buffer[0]);
			frame2 = DecoderUtil.concatenateAll(tempBuffer22, tempBuffer12, buffer[1]);
			tempBuffer21 = tempBuffer11;
			tempBuffer11 = buffer[0];

			tempBuffer22 = tempBuffer12;
			tempBuffer12 = buffer[1];
		} else {
			int slice = buffer.length + tempBuffer11.length + tempBuffer21.length - frameSize;

			double[] sliced1 = Arrays.copyOfRange(buffer[0], 0, buffer.length - slice);
			double[] sliced2 = Arrays.copyOfRange(buffer[1], 0, buffer.length - slice);

			frame1 = DecoderUtil.concatenateAll(tempBuffer21, tempBuffer11, sliced1);
			frame2 = DecoderUtil.concatenateAll(tempBuffer22, tempBuffer12, sliced2);

			tempBuffer21 = tempBuffer11;
			tempBuffer11 = buffer[0];

			tempBuffer22 = tempBuffer12;
			tempBuffer12 = buffer[1];
		}

		char[] outArr = { 'T', 'T' };
		// check if the power of the signal is high enough to be accepted.
		if (DecoderUtil.signalPower(frame1) < CUT_OFF_POWER) {
			outArr[0] = '_';
		}
		if (DecoderUtil.signalPower(frame2) < CUT_OFF_POWER) {
			outArr[1] = '_';
		}

		if (outArr[0] == '_' && outArr[1] == '_') {
			return outArr;
		}
		if (goertzel) {

			// transform frame
			double[] dft_data1 = transformFrameG(frame1, (int) audio.getSampleRate());
			double[] dft_data2 = transformFrameG(frame2, (int) audio.getSampleRate());

			// check if the frame has too much noise
			if (isNoisyG(dft_data1)) {
				outArr[0] = '_';
			}
			if (isNoisyG(dft_data2)) {
				outArr[1] = '_';
			}

			if (outArr[0] == '_' && outArr[1] == '_') {
				return outArr;
			}

			try {
				if (outArr[0] != '_') {
					outArr[0] = getRawChar(dft_data1);
				}
				if (outArr[1] != '_') {
					outArr[1] = getRawChar(dft_data2);
				}
			} catch (DTMFDecoderException e) {
				e.printStackTrace();
				throw new DTMFDecoderException("Something went wrong.");
			}
			return outArr;
		} else {
			// transform frames
			double[] power_spectrum1, power_spectrum2;

			// transform channel 1
			if (outArr[0] != '_') {
				power_spectrum1 = DTMFUtil.transformFrameFFT(frame1, (int) audio.getSampleRate());
			} else {
				power_spectrum1 = null;
			}

			// transform channel 2
			if (outArr[1] != '_') {
				power_spectrum2 = DTMFUtil.transformFrameFFT(frame2, (int) audio.getSampleRate());
			} else {
				power_spectrum2 = null;
			}

			// filter frame 1
			double[] dft_data1, dft_data2;
			if (power_spectrum1 != null) {
				dft_data1 = filterFrame(power_spectrum1);
			} else {
				dft_data1 = null;
			}

			// filter frame 2
			if (power_spectrum2 != null) {
				dft_data2 = filterFrame(power_spectrum2);
			} else {
				dft_data2 = null;
			}

			// check if the frame 1 has too much noise
			if (isNoisy(dft_data1, power_spectrum1)) {
				outArr[0] = '_';
			}

			if (isNoisy(dft_data2, power_spectrum2)) {
				outArr[1] = '_';
			}

			if (outArr[0] == '_' && outArr[1] == '_') {
				return outArr;
			}

			try {
				if (outArr[0] != '_') {
					outArr[0] = DTMFUtil.getRawChar(dft_data1);
				}
				if (outArr[1] != '_') {
					outArr[1] = DTMFUtil.getRawChar(dft_data2);
				}
			} catch (DTMFDecoderException e) {
				e.printStackTrace();
				throw new DTMFDecoderException("Something went wrong.");
			}
			return outArr;
		}
	}

	/**
	 * Method to decode the wav file and return the sequence of DTMF tones
	 * represented.
	 * 
	 * @return True if decoding process was successful
	 * @throws IOException
	 * @throws WavFileException
	 * @throws DTMFDecoderException
	 */
	public boolean decode() throws IOException, AudioFileException, DTMFDecoderException {
		if (!decoder)
			throw new DTMFDecoderException(
					"The object was not instantiated in decoding mode. Please use the correct constructor.");
		if (decoded) {
			return true;
		}
		if (audio.getNumChannels() == 1) {
			if (decode60)
				decodeMono60();
			else if (decode80)
				decodeMono80();
			else if (decode100)
				decodeMono100();
			else
				decodeMono40();
			decoded = true;
		} else if (audio.getNumChannels() == 2) {
			if (decode60)
				decodeStereo60();
			else if (decode80)
				decodeStereo80();
			else if (decode100)
				decodeStereo100();
			else
				decodeStereo40();
			decoded = true;
		} else
			throw new DTMFDecoderException("Can only decode mono and stereo files.");
		return true;
	}

	/**
	 * Method to set the minimum duration of the DTMF tones to be detected
	 * 
	 * @param duration
	 *            minimum duration of a tone. 0 or negative to use the default
	 *            ITU-T recommended value (40ms)
	 * @throws DTMFDecoderException
	 *             Throws an exception if the duration is less than 40ms
	 */
	public static void setMinToneDuration(int duration) throws DTMFDecoderException {
		if (duration <= 0) // use default duration of 40ms
			return;
		else if (duration < 40)
			throw new DTMFDecoderException(
					"Minimum tone duration must be greater than 40ms or, use 0 or a negative number to use the default ITU-T value.");
		else if (duration < 80)
			decode60 = true;
		else if (duration < 100)
			decode80 = true;
		else if (duration > 100)
			decode100 = true;
		else if (duration < Integer.MAX_VALUE)
			decode100 = true;
		else
			throw new DTMFDecoderException("the given minimum tone duration is too long.");
	}

	/**
	 * Method to check if the given audio file has been decoded.
	 */
	public boolean isDecoded() {
		return decoded;
	}

	/**
	 * Method to get the number of channels in the audio files being decoded
	 * 
	 * @return
	 */
	public int getChannelCount() {
		return audio.getNumChannels();
	}

	/**
	 * Method to generate the DTMF tone.
	 * 
	 * @return True if generation was successful
	 * @throws DTMFDecoderException
	 */
	public boolean generate() throws DTMFDecoderException {
		if (!generate)
			throw new DTMFDecoderException(
					"The object was not instantiated in the generation mode. Plese use the correct constructor.");

		ArrayList<Double> outSamples = new ArrayList<Double>();

		// calculate length (number of samples) of the tones and pauses
		int toneLen = (int) Math.floor((outToneDurr * outFs) / 1000.0);
		int pauseLen = (int) Math.floor((outPauseDurr * outFs) / 1000.0);

		// Add a pause at beginning of the file
		addPause(outSamples, pauseLen);

		// add the tones
		for (int i = 0; i < outChars.length; i++) {
			// add tone samples
			addTone(outSamples, outChars[i], toneLen);
			// add pause samples
			addPause(outSamples, pauseLen);
		}
		// Add a pause at the end of the file
		addPause(outSamples, pauseLen);

		generatedSeq = new double[outSamples.size()];
		for (int i = 0; i < generatedSeq.length; i++)
			generatedSeq[i] = outSamples.get(i);
		generated = true;
		return true;
	}

	/**
	 * Method to generate samples representing a dtmf tone
	 * 
	 * @param samples
	 *            array of samples to add the generated samples to.
	 * @param c
	 *            DTMF character to generate.
	 * @param toneLen
	 *            Number of samples to generate.
	 * @throws DTMFDecoderException
	 *             If the given character is not a dtmf character.
	 */
	private void addTone(ArrayList<Double> samples, char c, int toneLen) throws DTMFDecoderException {
		double[] f = getFreqs(c);
		for (double s = 0; s < toneLen; s++) {
			double lo = Math.sin(2.0 * Math.PI * f[0] * s / outFs);
			double hi = Math.sin(2.0 * Math.PI * f[1] * s / outFs);
			samples.add((hi + lo) / 2.0);
			// samples.add(hi);
		}
	}

	/**
	 * Method get the DTMF lower and upper frequencies.
	 * 
	 * @param c
	 *            DTMF character
	 * @return DTMF Frequencies to use to generate the tone.
	 * @throws DTMFDecoderException
	 *             If the given character is not a DTMF character.
	 */
	private double[] getFreqs(char c) throws DTMFDecoderException {
		double[] out = new double[2];

		if (c == '0') {
			out[0] = 941;
			out[1] = 1336;
		} else if (c == '1') {
			out[0] = 697;
			out[1] = 1209;
		} else if (c == '2') {
			out[0] = 697;
			out[1] = 1336;
		} else if (c == '3') {
			out[0] = 697;
			out[1] = 1477;
		} else if (c == '4') {
			out[0] = 770;
			out[1] = 1209;
		} else if (c == '5') {
			out[0] = 770;
			out[1] = 1336;
		} else if (c == '6') {
			out[0] = 770;
			out[1] = 1477;
		} else if (c == '7') {
			out[0] = 852;
			out[1] = 1209;
		} else if (c == '8') {
			out[0] = 852;
			out[1] = 1336;
		} else if (c == '9') {
			out[0] = 852;
			out[1] = 1477;
		} else if (c == 'A' || c == 'a') {
			out[0] = 697;
			out[1] = 1633;
		} else if (c == 'B' || c == 'b') {
			out[0] = 770;
			out[1] = 1633;
		} else if (c == 'C' || c == 'c') {
			out[0] = 852;
			out[1] = 1633;
		} else if (c == 'D' || c == 'd') {
			out[0] = 941;
			out[1] = 1633;
		} else
			throw new DTMFDecoderException("\"" + c + "\" is not a DTMF Character.");

		return out;
	}

	/**
	 * Method to add samples that represent a pause to the output
	 * 
	 * @param samples
	 *            Array of samples to add to.
	 * @param pauseLen
	 *            Number of samples to add.
	 */
	private void addPause(ArrayList<Double> samples, int pauseLen) {
		for (int s = 0; s < pauseLen; s++)
			samples.add(0.0);
	}

	/**
	 * Write the generated sequenec to a wav file.
	 * 
	 * @throws WavFileException
	 * @throws IOException
	 */
	public void export() throws IOException, WavFileException {
		FileUtil.writeWavFile(outFile, generatedSeq, outFs);
	}

	/**
	 * Get the samples array of the DTMF sequence of tones.
	 * 
	 * @return array with the samples of the dtmf sequence that has been
	 *         generated.
	 * @throws DTMFDecoderException
	 *             If the samples have no been generated yet.
	 */
	public double[] getGeneratedSequence() throws DTMFDecoderException {

		if (generated)
			return generatedSeq;
		else
			throw new DTMFDecoderException("Samples have not been generated yet. Please run generate() first.");
	}
}
