package src;

import java.io.File;
import java.io.IOException;

/**
 * Class to hand File operations
 * @author Tinotenda Chemvura
 *
 */

public class FileUtil {
	
	/**
	 * Method to read a Wav File
	 * @param filename
	 * @return A WavData object that has the meta data for the wav file and the sample data
	 * @throws IOException
	 * @throws WavFileException
	 */
	public static WavData readWavFile(String filename) throws IOException, WavFileException{
		// open the wav file specified by the 'filename' parameter
		WavFile out = WavFile.openWavFile(new File(filename));
		// create a buffer for all the samples in the wav file and get the sample points
		double[] buffer = new double[(int)out.getNumFrames()];
		out.readFrames(buffer, buffer.length);
		out.close();
		return new WavData(out.getSampleRate(), out.getNumChannels(), buffer, out.getValidBits());
	}
	
	public static void writeWavFile(WavData data){
		// TODO to implement method later. can use matlab to generate test files for now
	}
}
