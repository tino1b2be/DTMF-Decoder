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

package src;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

/**
 * Class to decode DTMF signals within a wav file. 
 * 
 * @author Tinotenda Chemvura
 *
 */
public class DTMFUtil {

	public static double CUT_OFF_POWER = 0.004;
	public static double CUT_OFF_POWER_NOISE_RATIO = 0.87;
	public static double FRAME_DURATION = 0.045;
	private boolean decoded;
	private String seq[];
	private AudioFile audio;
	private int frameSize;
	public static boolean decode60 = false;
	public static boolean decode80 = false;
	public static ArrayList<Double> noisyTemp = new ArrayList<>();
	public static boolean debug = true;
	public static boolean db = true;

	/**
	 * The list of valid DTMF frequencies. See the <a
	 * href="http://en.wikipedia.org/wiki/Dual-tone_multi-frequency_signaling"
	 * >WikiPedia article on DTMF</a>.
	 */
	public static final int[] DTMF_FREQUENCIES = { 697, 770, 852, 941, 1209, 1336, 1477, 1633 };
	
	private static int[] fbin = 
		{ 
			687, 697, 707,						 	// 697
			758, 770, 782, 							// 770
			839, 852, 865, 							// 852
			927, 941, 955, 							// 941
			1191, 1209, 1227, 						// 1209
			1316, 1336, 1356, 						// 1336
			1455, 1477, 1499, 						// 1477
			1609, 1633, 1647, 1657 					// 1633 
		};

	/**
	 * Create DTMFUtil object for an audio file given the AudioFile object
	 * 
	 * @param data
	 *            AudioFile object to be processed.
	 */
	public DTMFUtil(AudioFile data) {
		this.audio = data;
		setFrameSize();
		this.decoded = false;
		seq = new String[2];
		this.seq[0] = "";
		this.seq[1] = "";
	}

	/**
	 * Create DTMFUtil object for an audio file given the filename
	 * 
	 * @param filename
	 *            Filename of the audio file to be processed
	 * @throws Exception 
	 * @throws AudioFileException 
	 * @throws IOException
	 * @throws WavFileException
	 */
	public DTMFUtil(String filename) throws AudioFileException, Exception{
		this.audio = FileUtil.readAudioFile(filename);
		setFrameSize();
		this.decoded = false;
		seq = new String[2];
		this.seq[0] = "";
		this.seq[1] = "";
	}

	/**
	 * Create DTMFUtil object for an audio file given the file object
	 * 
	 * @param file
	 *            File object for the audio file
	 * @throws Exception 
	 * @throws AudioFileException 
	 * @throws IOException
	 * @throws WavFileException
	 */
	public DTMFUtil(File file) throws AudioFileException, Exception{
		this.audio = FileUtil.readAudioFile(file);
		setFrameSize();
		setCentreIndicies();
		this.decoded = false;
		seq = new String[2];
		this.seq[0] = "";
		this.seq[1] = "";
	}

	/**
	 * Method to precalculate the indices to be used to locate the DTMF frequencies in the power spectrum
	 */
	private void setCentreIndicies() {
		for (int i = 0; i < freqIndicies.length; i++){
			freqIndicies[i] = 
		}
	}

	/**
	 * Method to set the frame size for the decoding process. Framesize must be
	 * a poser of 2
	 */
	private void setFrameSize() {
		// calculate framesize depending on the framerate
		this.frameSize = 256;
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
		// TODO
		/*
		out[0] = DecoderUtil.max(Arrays.copyOfRange(frame, 0, 3));
		out[1] = DecoderUtil.max(Arrays.copyOfRange(frame, 3, 6));
		out[2] = DecoderUtil.max(Arrays.copyOfRange(frame, 6, 9));
		out[3] = DecoderUtil.max(Arrays.copyOfRange(frame, 9, 12));
		out[4] = DecoderUtil.max(Arrays.copyOfRange(frame, 12, 15));
		out[5] = DecoderUtil.max(Arrays.copyOfRange(frame, 15, 18));
		out[6] = DecoderUtil.max(Arrays.copyOfRange(frame, 18, 21));
		out[7] = DecoderUtil.max(Arrays.copyOfRange(frame, 21, 25));
		*/
		return out;
	}

	/**
	 * Method returns the DTMF sequence
	 * 
	 * @return char array with the keys represented in the file
	 * @throws DTMFDecoderException
	 *             Throws excepion when the file has not been decoded yet.
	 */
	public String[] getSequence() throws DTMFDecoderException {
		if (!decoded)
			throw new DTMFDecoderException("File has not been decoded yet. Please run the method decode() first.");
		return seq;
	}

	/**
	 * Method to generate a frequency spectrum of the frame
	 * 
	 * @param frame
	 *            Frame to be transformed
	 * @param Fs
	 *            Sampling Frequency
	 * @return an Array showing the realtive powers of the DTMF frequencies
	 */
	private static double[] transformFrame(double[] frame, int Fs) {
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
	 * Method to detect whether a frame is too noisy for detection
	 * 
	 * @param dft_data
	 *            Frequency spectrum magnitudes for the DTMF frequencies
	 * @return true is noisy or false if it is acceptable
	 */
	private boolean isNoisy(double[] dft_data) {
		// sum the powers of all frequencies = sum
		// find ratio of the (sum of two highest peaks) : sum
		double[] temp1 = Arrays.copyOfRange(dft_data, 0, 4);
		double[] temp2 = Arrays.copyOfRange(dft_data, 4, 8);
		Arrays.sort(temp1);
		Arrays.sort(temp2);
		double one = temp1[temp1.length - 1];
		double two = temp2[temp2.length - 1];
		double sum = DecoderUtil.sumArray(dft_data);
		return ((one + two) / sum) < CUT_OFF_POWER_NOISE_RATIO;
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
	 * Method to decode the next frame in a buffer of a mono channeled wav file
	 * 
	 * @return the decoded DTMF character
	 * @throws AudioFileException 
	 * @throws IOException
	 * @throws WavFileException
	 * @throws DTMFDecoderException
	 */
	private char decodeNextFrame1() throws AudioFileException, DTMFDecoderException, IOException {
		int bufferSize = (int) Math.ceil(frameSize / 3.0);
		double[] buffer1 = new double[bufferSize];
		double[] tempBuffer11 = new double[bufferSize];
		double[] tempBuffer21 = new double[bufferSize];

		int framesRead = audio.read(buffer1);
		if (framesRead < bufferSize){
			audio.close();
			throw new DTMFDecoderException("Out of frames");
		}
		
		// slice off the extra bit to make the framesize a power of 2
		int slice = buffer1.length + tempBuffer11.length + tempBuffer21.length - frameSize;
		double[] sliced = Arrays.copyOfRange(buffer1, 0, buffer1.length-slice);
		
		double[] frame = DecoderUtil.concatenateAll(tempBuffer21, tempBuffer11, sliced);
		tempBuffer21 = tempBuffer11;
		tempBuffer11 = buffer1;

		char out = 'T';
		// check if the power of the signal is high enough to be accepted.
		// TODO run tests to obtain good power cut-off
		
		double power = DecoderUtil.signalPower(frame);
		// noisyTemp.add(power);
		
		if (power < CUT_OFF_POWER)
			return '_';
		
		// transform frame and return frequency spectrum information		
		double[] power_spectrum = DTMFUtil.transformFrame(frame, (int) audio.getSampleRate());
		
		// filter out the 8 DTMF frequencies from the power spectrum
		double[] dft_data = filterFrame(power_spectrum);
		
		
		
		// check if the frame has too much noise
		if (isNoisy(power_spectrum))
			return '_';
		
		out = DTMFUtil.getRawChar(dft_data);
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
				curr = decodeNextFrame1();
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
						}else {
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
	
	private void decodeMono60() throws IOException, AudioFileException  {
		char prev = '_';
		char prev2 = '_';
		char prev3 = '_';
		String seq2 = "";
		String seq22 = "";
		int count = 0;
		do {

			char curr;
			try {
				curr = decodeNextFrame1();
			} catch (DTMFDecoderException e) {
				break;
			}
			if (debug)
				System.out.print(curr);
			// System.out.print(curr);
			if (curr != '_') {
				if (curr == prev && curr == prev2) { // eliminate false positives
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
	
	private void decodeMono80() throws IOException, AudioFileException  {
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
				curr = decodeNextFrame1();
			} catch (DTMFDecoderException e) {
				break;
			}
			if (debug)
				System.out.print(curr);
			// System.out.print(curr);
			if (curr != '_') {
				if (curr == prev && curr == prev2 && curr == prev3) { // eliminate false positives
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
	 * @throws WavFileException
	 */
	private void decodeStereo() throws IOException, AudioFileException  {
		char curr[];
		char[] prev = { '_', '_' };
		char[] prev2 = { '_', '_' };
		String[] seq2 = { "", "" };
		do {

			try {
				curr = decodeNextFrame2();
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
	 * Method to decode the next frame in a buffer of a stereo wav file
	 * 
	 * @return the decoded DTMF character
	 * @throws IOException
	 * @throws WavFileException
	 * @throws DTMFDecoderException
	 */
	private char[] decodeNextFrame2() throws IOException, AudioFileException , DTMFDecoderException {
		// TODO 
		
		double[][] buffer = new double[2][(int) Math.floor(frameSize / 3)]; 
		double[] tempBuffer11 = new double[(int) Math.floor(frameSize / 3)];
		double[] tempBuffer21 = new double[(int) Math.floor(frameSize / 3)];
		double[] tempBuffer12 = new double[(int) Math.floor(frameSize / 3)];
		double[] tempBuffer22 = new double[(int) Math.floor(frameSize / 3)];
		int framesRead = 0;

		framesRead = audio.read(buffer);
		if (framesRead < frameSize / 3){
			audio.close();
			throw new DTMFDecoderException("Out of frames");
		}

		double[] frame1 = DecoderUtil.concatenateAll(tempBuffer21, tempBuffer11, buffer[0]);
		double[] frame2 = DecoderUtil.concatenateAll(tempBuffer22, tempBuffer12, buffer[1]);
		tempBuffer21 = tempBuffer11;
		tempBuffer11 = buffer[0];

		tempBuffer22 = tempBuffer12;
		tempBuffer12 = buffer[1];

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

		// transform frame
		double[] dft_data1 = DTMFUtil.transformFrame(frame1, (int) audio.getSampleRate());
		double[] dft_data2 = DTMFUtil.transformFrame(frame2, (int) audio.getSampleRate());

		// check if the frame has too much noise
		if (isNoisy(dft_data1)) {
			outArr[0] = '_';
		}
		if (isNoisy(dft_data2)) {
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

	/**
	 * Method to decode the wav file and return the sequence of DTMF tones
	 * represented.
	 * 
	 * @return String array with the sequence of tones for each channel
	 * @throws IOException
	 * @throws WavFileException
	 * @throws DTMFDecoderException
	 */
	public String[] decode() throws IOException, AudioFileException , DTMFDecoderException {
		if (decoded) {
			return seq;
		}
		if (audio.getNumChannels() == 1) {
			if (decode60)
				decodeMono60();
			else if (decode80)
				decodeMono80();
			else
				decodeMono40();
			decoded = true;
		} else if (audio.getNumChannels() == 2) {
			decodeStereo();
			decoded = true;
		} else
			throw new DTMFDecoderException("Can only decode mono and stereo files.");
		return seq;
	}
}
