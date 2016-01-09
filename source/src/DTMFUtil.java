package src;

import java.util.ArrayList;
import java.util.Arrays;

public class DTMFUtil {

	//		int[] freqs = {697, 770, 852, 941, 1209, 1336, 1477, 1633};
	private int[] fbin = {
			687,697,707,			//697
			758,770,782,			//770
			839,852,865,			//852
			927,941,955,			//941
			1191,1209,1227,			//1209
			1316,1336,1356,			//1336
			1455,1477,1499,			//1477
			1609,1633,1647,1657};	//1633
	private int Fs;
	private double[] data;
	private boolean decoded;
	private String seq;
	private static double cutOffPerc = 0.61;
	static int frameSize = 370;

	public DTMFUtil(WavData file) {
		//genFreqBin();
		this.data = file.getSamples();
		this.Fs = (int) file.getSampleRate();
		this.decoded = false;
	}

	/**
	 * Method to generate the frequency bin that will be used for the Goertzel
	 * Transformations
	 */
	private void genFreqBin() {
		// f_bin = [687:707, 758:782, 839:865, 927:955, 1191:1227,
		// 1316:1356,1455:1499, 1609:1657];
		// {697, 770, 852, 941, 1209, 1336, 1477, 1633}
		fbin = new int[274];
		int i = 0;
		// 697
		for (int f = 687; f < 708; i++, f++) {
			fbin[i] = f;
		}
		// 770
		for (int f = 758; f < 783; i++, f++) {
			fbin[i] = f;
		}
		// 852
		for (int f = 839; f < 866; i++, f++) {
			fbin[i] = f;
		}
		// 941
		for (int f = 927; f < 956; i++, f++) {
			fbin[i] = f;
		}
		// 1209
		for (int f = 1191; f < 1228; i++, f++) {
			fbin[i] = f;
		}
		// 1336
		for (int f = 1316; f < 1357; i++, f++) {
			fbin[i] = f;
		}
		// 1477
		for (int f = 1455; f < 1500; i++, f++) {
			fbin[i] = f;
		}
		// 1633
		for (int f = 1609; f < 1658; i++, f++) {
			fbin[i] = f;
		}

	}

	/**
	 * Method to make the frames to be used in the Goertzel transforms
	 * 
	 * @param data
	 *            The array with sample points
	 * @param Fs
	 *            Sample Rate
	 * @return a 2-D array of frames of the wav file
	 */
	private double[][] makeFrames(double[] data) {

		// TODO calculate appropriate frame size for samples rates other than
		// 8kHz

		int numFrames = 0;
		if (data.length < frameSize) {
			// TODO deal with a case when only one frame is available (this
			// should probably be rejected)
		} else {
			numFrames = (int) (Math.floor(data.length / frameSize) * 2 - 1);
		}

		double[][] out = new double[numFrames][frameSize];

		/*
		 * MATLAB code col = 1; for i= 1 : floor(frameSize/2) : length(data) new
		 * = data(i:i+frameSize-1); frames(:,col) = new; if (col == numFrames) %
		 * break when all the frames have been created break; end col = col+1;
		 * 
		 * end % end of for loop
		 */

		int frameNum = 0;
		for (int sample = 0; sample < data.length; sample += Math.floor(frameSize / 2)) {
			int start = sample;
			int finish = sample + frameSize;
			double[] frame = Arrays.copyOfRange(data, start, finish);
			out[frameNum] = frame;
			if (frameNum == numFrames - 1)
				break;
			frameNum++;
		}

		return out;
	}

	/**
	 * Method to decodes a given frame matrix and gives an output in the form a
	 * matrix with each column giving the magnitudes of the each of the DTMF
	 * signals in the corresponding frame.
	 * 
	 * @param frames
	 *            array of frames to be decoded
	 * @param Fs
	 *            Sample Rate
	 * @return 2-D Array n the form a matrix with each column giving the
	 *         magnitudes of the each of the DTMF signals in the corresponding
	 *         frame.
	 */
	private double[][] transformFrames(double[][] frames) {
		// output 2-D array. each element is an array with magnitudes of the
		// DTMF frequecies after beeing transforms using goertzel algorithm
		// There will be 274 frequencies to be tested instead of 8 so that the
		// frequency tolerance recquirement can be met
		double[][] temp = new double[frames.length][25];
		double[][] out = new double[frames.length][8];
		Goertzel g = new Goertzel(this.Fs, frameSize, fbin);
		
		// 1. transform the frames using goertzel algorithm
		// 2. get the highest DTMF freq within the tolerance range and use that
		// magnitude to represet the corresponsing DTMF free
		for (int frame = 0; frame < frames.length; frame++) {
			temp[frame] = g.calcFreqWeight(frames[frame]);
			out[frame] = filterFrame(temp[frame]);
		}
		return out;
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
	private double[] filterFrame(double[] frame) {
		double[] out = new double[8];

		out[0] = max(Arrays.copyOfRange(frame, 0, 3));
		out[1] = max(Arrays.copyOfRange(frame, 3, 6));
		out[2] = max(Arrays.copyOfRange(frame, 6, 9));
		out[3] = max(Arrays.copyOfRange(frame, 9, 12));
		out[4] = max(Arrays.copyOfRange(frame, 12, 15));
		out[5] = max(Arrays.copyOfRange(frame, 15, 18));
		out[6] = max(Arrays.copyOfRange(frame, 18, 21));
		out[7] = max(Arrays.copyOfRange(frame, 21, 25));

		return out;
	}

	/**
	 * Function to return the largest value of an array
	 * 
	 * @param arr
	 *            Array to be processed
	 * @return Value of the largest element
	 */

	private double max(double[] arr) {
		Arrays.sort(arr);
		return arr[arr.length - 1];
	}

	/**
	 * Method to decode the frames to get the DTMF tone (or pause) being
	 * represented by each frame
	 * 
	 * @param dftData
	 *            Data from the Goertzel Transforms
	 * @return int Array of the sequence of DTMF Characters represented by each
	 *         frame
	 * @throws DTMFDecoderException Throws exception when decoding has failed.
	 */
	private char[] getRawSequence(double[][] dftData) throws DTMFDecoderException {

		// initialise the output array with '_' to denote pauses
		char[] out = new char[dftData.length];

		// Get the top 5 from the first 50 frames
		double[][] first50;
		if (dftData.length < 50)
			first50 = dftData;
		else
			first50 = Arrays.copyOfRange(dftData, 0, 50);

		// get the average of each frame
		double[] topFramesAvg = avgFrames(first50);
		Arrays.sort(topFramesAvg);

		// get the top 5 frames and use the average of those frames
		// skip the first 2 frames, they are usually outliners
		double topAvg = 0; // average of top 5 frames
		if (topFramesAvg.length < 6)
			topAvg = topFramesAvg[topFramesAvg.length-1];
		else
			topAvg = meanArray(Arrays.copyOfRange(topFramesAvg, topFramesAvg.length-6, topFramesAvg.length-1));

		// loop through each frame in the dft data and eliminate if the avg is
		// too low. if avg is high enough, decode by detecting the relevant
		// frequencies.

		for (int fr = 0; fr < dftData.length; fr++) {
			if (meanArray(dftData[fr]) < (cutOffPerc * topAvg)) {
				out[fr] = '_';
			} else {
//				int low = maxIndex(Arrays.copyOfRange(dftData[fr], 0, 4));
//				int hi = maxIndex(Arrays.copyOfRange(dftData[fr], 4, 7));

				int low, hi;
				double[] lower = Arrays.copyOfRange(dftData[fr], 0, 4);
				double[] higher = Arrays.copyOfRange(dftData[fr], 4, 8);
				
				low = maxIndex(lower);
				hi = maxIndex(higher);
				
				if (low == 0) {				// low = 697
					if (hi == 0){			// High = 1209
						out[fr] = '1';
					} else if (hi == 1){	// high = 1336
						out[fr] = '2';
					} else if (hi == 2){	// high = 1477
						out[fr] = '3';
					} else if (hi == 3){	// high = 1633
						out[fr] = 'A';
					} else
						throw new DTMFDecoderException("Something went terribly wrong!");

				} else if (low == 1) {		// low = 770
					if (hi == 0){			// high = 1209
						out[fr] = '4';
					} else if (hi == 1){	// high = 1336
						out[fr] = '5';
					} else if (hi == 2){	// high = 1477
						out[fr] = '6';
					} else if (hi == 3){	// high = 1633
						out[fr] = 'B';
					} else
						throw new DTMFDecoderException("Something went terribly wrong!");

				} else if (low == 2) {		// low = 852
					if (hi == 0){			// high = 1209
						out[fr] = '7';
					} else if (hi == 1){	// high = 1336
						out[fr] = '8';
					} else if (hi == 2){	// high = 1477
						out[fr] = '9';
					} else if (hi == 3){	// high = 1633
						out[fr] = 'C';
					} else
						throw new DTMFDecoderException("Something went terribly wrong!");

				} else if (low == 3) {		// low = 941
					if (hi == 0){			// high = 1209
						out[fr] = '*';
					} else if (hi == 1){	// high = 1336
						out[fr] = '0';
					} else if (hi == 2){	// high = 1477
						out[fr] = '#';
					} else if (hi == 3){	// high = 1633
						out[fr] = 'D';
					} else
						throw new DTMFDecoderException("Something went terribly wrong!");
				} else
					throw new DTMFDecoderException("Something went terribly wrong!");
			}
		}
		return out;
	}

	/**
	 * Method to return the index of the max element in an array
	 * 
	 * @param arr
	 *            Array to be processed
	 * @return Index of the max element
	 */
	private int maxIndex(double[] arr) {
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

	/*
	 * Method to reverse an array
	 * 
	 * @param arr
	 *            Array to be reversed
	 * @return Reversed Array
	 *
	private double[] reverseArray(double[] arr) {
		for (int i = 0; i < arr.length / 2; i++) {
			double temp = arr[i];
			arr[i] = arr[arr.length - i - 1];
			arr[arr.length - i - 1] = temp;
		}
		return arr;
	}*/

	/**
	 * Method to calculate mean of an array
	 * 
	 * @param arr
	 *            Array whose mean is to be calculated
	 * @return mean of the input array
	 */
	private double meanArray(double[] arr) {
		double out = 0.0;
		for (int i = 0; i < arr.length; i++)
			out += arr[i];
		return out / (1.0 * arr.length);
	}

	/**
	 * Method to calculate the mean of each frame
	 * 
	 * @param first50
	 *            2-D array of the frames
	 * @return Array with the mean of each frame.
	 */
	private double[] avgFrames(double[][] first50) {
		double[] out = new double[first50.length];
		for (int i = 0; i < first50.length; i++) {
			double avg = 0;
			for (int j = 0; j < first50[i].length; j++) {
				avg += first50[i][j];
			}
			avg = avg / (first50[i].length * 1.0);
			out[i] = avg;
		}
		return out;
	}
	
	/**
	 * Method to obtain the actual DTMF tone sequence represented by the data
	 * 
	 * @param raw
	 *            int Array with numbers that represent the DTMF tone (or pause)
	 *            represented by each frame in the data
	 * @return A string of the DTMF sequence represented by the audio file
	 */
	private void getDTMFSequence(char[] raw) {
		ArrayList<Character> temp = new ArrayList<Character>();
		if (raw[0] != '_')
			temp.add(raw[0]);
		for (int i = 1; i < raw.length; i++){
			if (raw[i] != raw[i-1])
				temp.add(raw[i]);
		}
		seq = "";
		for (int i = 0; i < temp.size(); i++){
			if (temp.get(i) == '_')
				continue;
			seq += temp.get(i);
		}
	}

	/**
	 * Method to start the decoding process
	 * 
	 * @throws DTMFDecoderException
	 *             Throws an exception when the file has alread been decoded.
	 */
	public void decode() throws DTMFDecoderException {
		if (decoded)
			throw new DTMFDecoderException("This method has already been run.");
		this.decoded = true;
		double[][] frames = makeFrames(data);
		double[][] dft_data = transformFrames(frames);
		char[] rawSeq = getRawSequence(dft_data);
		getDTMFSequence(rawSeq);
	}

	/**
	 * Method returns the DTMF sequence
	 * 
	 * @return char array with the keys represented in the file
	 * @throws DTMFDecoderException
	 *             Throws excepion when the file has not been decoded yet.
	 */
	public String getSequence() throws DTMFDecoderException {
		if (!decoded)
			throw new DTMFDecoderException("File has not been decoded yet. Please run the method decode() first.");
		return seq;
	}
}
