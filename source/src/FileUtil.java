package src;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;

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

	/**
	 * Method to read a file with known DTMF sequence which will be used in
	 * testing process to determine whether test was a success
	 * 
	 * @param filename
	 * @return
	 * @throws IOException
	 * @throws WavFileException
	 */
	public static DTMFfile readDTMFfile(String filename) throws IOException, WavFileException {
		String seq = getSeq(filename);
		return new DTMFfile(readWavFile(filename),seq);
	}
	
	/**
	 * Method to extract the dtmf sequence from a given file name
	 * @param filename
	 * @return
	 */
	private static String getSeq(String filename) {
		// TODO Method to extract the dtmf sequence from a given file name
		return null;
	}

	public static void writeWavFile(WavData data){
		// TODO to implement method later. can use matlab to generate test files for now
	}
	
	/**
	 * Method to load a text file
	 * @param filename
	 * @return
	 * @throws IOException
	 */
	public static ArrayList<String> loadFile(String filename) throws IOException{
		
		FileInputStream fstream = new FileInputStream(filename);
		BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
		ArrayList<String> data = new ArrayList<String>();
		String line;
		int count=0;

		while((line = br.readLine())!=null){
			data.add(line);
			count++;
		}
		br.close();
		if (count==0)
			return null;
		else
			return data;
	}


	/**
	 * Mehotd to write the observations to a file
	 * @param dataOut
	 * @throws IOException 
	 */
	public static void writeToFile(double[] dataOut) throws IOException {
		PrintWriter pw = new PrintWriter(new FileWriter("results.csv"));
		if (dataOut == null)
			pw.print("");
		else{
			for (int i = 0; i < dataOut.length; i++){
				pw.println(dataOut[i]);
			}
		}
		pw.close();
	}

	// TODO finish off method to cycle through files
	public static ArrayList<DTMFfile> getFiles(String path) throws IOException, WavFileException {
		ArrayList<DTMFfile> files = new ArrayList<>();	// output array of files
		File dir = new File(path);						// file directory for test files
		File[] directoryListing = dir.listFiles();		// files inside directory
		if (directoryListing != null) {
			for (File child : directoryListing) {		// for each file inside the dir
				if (child.getAbsolutePath().endsWith(".wav")) { // add to output if its a wav file
					files.add(readDTMFfile(child));
				}
			}
		} else {
			throw new WavFileException("File path given is either not a directory or it is empty.");
		}
		return files;
	}

	private static DTMFfile readDTMFfile(File child) throws IOException, WavFileException {
		return readDTMFfile(child.getAbsolutePath());
	}

	public static WavFile readWavFileBuffer(String filename) throws IOException, WavFileException {
		return WavFile.openWavFile(new File(filename));
	}
	
}
