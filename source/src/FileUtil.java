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
}
