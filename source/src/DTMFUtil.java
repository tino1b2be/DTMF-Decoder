package src;

import java.util.Arrays;

public class DTMFUtil {

	/**
	 * Method to make the frames to be used in the Goertzel transforms
	 * 
	 * @param data
	 *            WavData object
	 * @return a 2-D array of frames of the wav file
	 */
	public double[][] makeFrames(WavData wavFile) {
		double[] data = wavFile.getSamples();
		long Fs = wavFile.getSampleRate();
		return makeFrames(data, Fs);
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
	public double[][] makeFrames(double[] data, long Fs) {

		// TODO calculate appropriate framezise for samples rates other than
		// 8kHz

		int frameSize = 370;
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
		for (int sample = 0; sample < numFrames; sample += Math.floor(frameSize / 2)) {
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
	public double[][] transformFrames(double[][] frames, long Fs) {
		// TODO implement Goertzel function in Java
		return null;
	}
	
	/**
	 * Method to decode the frames to get the DTMF tone (or pause) being represented by each frame
	 * @param dftData Data from the Goertzel Transforms
	 * @return int Array of the sequence of DTMF Characters represented by each frame
	 */
	public int[] getRawSequence(double[][] dftData){
		// TODO implement this method
		return null;
	}
	
	/**
	 * Method to obtain the actual DTMF tone sequence represented by the data
	 * @param raw
	 * @return
	 */
	public String getDTMFSequence(int[] raw){
		// TODO implement this method
		return null;
	}
}
