package src;

import java.util.Arrays;

public class DTMFUtil {

	private int[] fbin;
	private int Fs;
	private double[] data;
	private boolean decoded;
	private char[] seq;
	
	public DTMFUtil(WavData file) {
		genFreqBin();
		this.data = file.getSamples();
		this.Fs = (int) file.getSampleRate();
		this.decoded = false;
	}

	/**
	 * Method to generate the frequency bin that will be used for the Goertzel
	 * Transformations
	 */
	private void genFreqBin() {
		// f_bin = [687:707, 758:782, 839:865, 927:955, 1191:1227, 1316:1356,1455:1499, 1609:1657];
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
	private double[][] transformFrames(double[][] frames) {
		// output 2-D array. each element is an array with magnitudes of the
		// DTMF frequecies after beeing transforms using goertzel algorithm
		// There will be 274 frequencies to be tested instead of 8 so that the
		// frequency tolerance recquirement can be met
		double[][] temp = new double[frames.length][274];
		Goertzel g = new Goertzel(this.Fs, 274, fbin);
		for (int frame = 0; frame < frames.length; frame++) {
			temp[frame] = g.calcFreqWeight(frames[frame]);
		}

		// get the highest DTMF freq within the tolerance range and use that
		// magnitude to represet the corresponsing DTMF freq
		
		double[][] out = new double[frames.length][8];
		 for (int frame = 0; frame < frames.length; frame++){
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
		/*
		 * MATLAB code
		 * 
			for f = 1:size(frames,2) % for each frame
		        out(1,f) = max(dft_data(1:20,f));       % 697Hz
		        out(2,f) = max(dft_data(21:46,f));      % 770Hz
		        out(3,f) = max(dft_data(47:73,f));      % 852Hz
		        out(4,f) = max(dft_data(74:102,f));     % 941Hz
		        out(5,f) = max(dft_data(103:139,f));    % 1209Hz
		        out(6,f) = max(dft_data(140:180,f));    % 1336Hz
		        out(7,f) = max(dft_data(181:225,f));    % 1477Hz
		        out(8,f) = max(dft_data(226:274,f));    % 1633Hz
		    end
		 */
		out[0] = max(Arrays.copyOfRange(frame, 0, 20));
		out[1] = max(Arrays.copyOfRange(frame, 20, 46));
		out[2] = max(Arrays.copyOfRange(frame, 46, 73));
		out[3] = max(Arrays.copyOfRange(frame, 73, 102));
		out[4] = max(Arrays.copyOfRange(frame, 102, 139));
		out[5] = max(Arrays.copyOfRange(frame, 139, 180));
		out[6] = max(Arrays.copyOfRange(frame, 180, 225));
		out[7] = max(Arrays.copyOfRange(frame, 225, 274));
		
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
		return arr[arr.length-1];
	}
	

	/**
	 * Method to decode the frames to get the DTMF tone (or pause) being
	 * represented by each frame
	 * 
	 * @param dftData
	 *            Data from the Goertzel Transforms
	 * @return int Array of the sequence of DTMF Characters represented by each
	 *         frame
	 */
	private char[] getRawSequence(double[][] dftData) {
		
		// initialise the output array with '_' to denote pauses
		char[] out = new char[dftData.length];
		for (int i = 0; i < out.length; i++)
			out[i] = '_';
		
		// TODO implement algorithm to get the raw sequence of the tones
		
		
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
	private char[] getDTMFSequence(char[] raw) {
		// TODO implement this method
		return null;
	}
	
	/**
	 * Method to start the decoding process
	 * @throws DTMFDecoderException Throws an exception when the file has alread been decoded.
	 */
	public void decode() throws DTMFDecoderException{
		if (decoded)
			throw new DTMFDecoderException("This method has already been run.");	
		this.decoded = true;
		double[][] frames = makeFrames(data);
		double[][] dft_data = transformFrames(frames);
		char[] rawSeq = getRawSequence(dft_data);
		seq = getDTMFSequence(rawSeq);
	}

	/**
	 * Method returns the DTMF sequence
	 * @return char array with the keys represented in the file
	 * @throws DTMFDecoderException Throws excepion when the file has not been decoded yet.
	 */
	public char[] getSequence() throws DTMFDecoderException {
		if (!decoded)
			throw new DTMFDecoderException("File has not been decoded yet. Please run the method decode() first.");	
		return seq;
	}
	
}
