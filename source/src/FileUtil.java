package src;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Class to hand File operations
 * 
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
 * 
 * @author Tinotenda Chemvura
 *
 */

public class FileUtil {

	/**
	 * Method to read a Wav File
	 * 
	 * @param filename
	 * @return A WavData object that has the meta data for the wav file and the
	 *         sample data
	 * @throws IOException
	 * @throws WavFileException
	 */
	public static WavData readWavFile(String filename) throws IOException, WavFileException {
		// open the wav file specified by the 'filename' parameter
		WavFile out = WavFile.openWavFile(new File(filename));
		// create a buffer for all the samples in the wav file and get the
		// sample points
		double[] buffer = new double[(int) out.getNumFrames()];
		out.readFrames(buffer, buffer.length);
		out.close();
		return new WavData(out.getSampleRate(), out.getNumChannels(), buffer, out.getValidBits());
	}

	public static void writeWavFile(WavData data) {
		// TODO to implement method later. can use matlab to generate test files
		// for now
	}

	/**
	 * Method to load a text file
	 * 
	 * @param filename
	 * @return
	 * @throws IOException
	 */
	public static ArrayList<String> loadFile(String filename) throws IOException {

		FileInputStream fstream = new FileInputStream(filename);
		BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
		ArrayList<String> data = new ArrayList<String>();
		String line;
		int count = 0;

		while ((line = br.readLine()) != null) {
			data.add(line);
			count++;
		}
		br.close();
		if (count == 0)
			return null;
		else
			return data;
	}

	/**
	 * Mehotd to write the observations to a file
	 * 
	 * @param dataOut
	 * @throws IOException
	 */
	public static void writeToFile(double[] dataOut) throws IOException {
		PrintWriter pw = new PrintWriter(new FileWriter("results.csv"));
		if (dataOut == null)
			pw.print("");
		else {
			for (int i = 0; i < dataOut.length; i++) {
				pw.println(dataOut[i]);
			}
		}
		pw.close();
	}

	/**
	 * Mehotd to write the observations to a file
	 * 
	 * @param <T>
	 * @param dataOut
	 * @throws IOException
	 */
	public static <T> void writeToFile(ArrayList<T> dataOut, String filename) throws IOException {
		PrintWriter pw = new PrintWriter(new FileWriter(filename));
		if (dataOut == null)
			pw.print("");
		else {
			for (int i = 0; i < dataOut.size(); i++) {
				pw.println(dataOut.get(i));
			}
		}
		pw.close();
	}

	// TODO finish off method to cycle through files
	public static ArrayList<File> getFiles(String path) throws IOException, WavFileException {
		ArrayList<File> files = new ArrayList<>(); // output array of files
		File dir = new File(path); // file directory for test files
		File[] directoryListing = dir.listFiles(); // files inside directory
		if (directoryListing != null) {
			for (File child : directoryListing) { // for each file inside the
													// dir
				if (child.getAbsolutePath().endsWith(".wav") || child.getAbsolutePath().endsWith(".WAV")) { // add
																											// to
																											// output
																											// if
																											// its
																											// a
																											// wav
																											// file
					files.add(child);
				}
			}
		} else {
			throw new WavFileException("File path given is either not a directory or it is empty.");
		}
		return files;
	}

	public static WavFile readWavFileBuffer(String filename) throws IOException, WavFileException {
		return WavFile.openWavFile(new File(filename));
	}

	public static WavFile readWavFileBuffer(File file) throws IOException, WavFileException {
		return readWavFileBuffer(file.getPath());
	}

	/**
	 * Write test results to a file
	 * 
	 * @param dataOut
	 * @throws IOException
	 */
	public static <T> void writeToFile(ArrayBlockingQueue<T> dataOut) throws IOException {
		PrintWriter pw = new PrintWriter(new FileWriter("results.txt"));
		if (dataOut == null)
			pw.print("");
		else {
			for (T data : dataOut) {
				if (((TestResult) data).isSuccess())
					TestResult.totalSuccess++;
				pw.println(data);

			}
		}
		pw.close();
	}

	/**
	 * Write test results to a file
	 * 
	 * @param dataOut
	 * @throws IOException
	 */
	public static void writeToFile(TestResult[] dataOut) throws IOException {
		PrintWriter pw = new PrintWriter(new FileWriter("results.txt"));
		if (dataOut == null)
			pw.print("");
		else {
			for (TestResult data : dataOut) {
				if (((TestResult) data).isSuccess())
					TestResult.totalSuccess++;
				pw.println(data);

			}
		}
		pw.close();
	}

	/**
	 * Write test results to a file
	 * 
	 * @param dataOut
	 * @throws IOException
	 */
	public static void writeToFile(TestResult[] dataOut, String filename) throws IOException {
		PrintWriter pw = new PrintWriter(new FileWriter(filename));
		if (dataOut == null)
			pw.print("");
		else {
			for (TestResult data : dataOut) {
				if (((TestResult) data).isSuccess())
					TestResult.totalSuccess++;
				pw.println(data);

			}
		}
		pw.close();
	}

	public static ArrayList<File> getDirs(String parent) throws WavFileException {
		ArrayList<File> files = new ArrayList<>(); // output array of files
		File dir = new File(parent); // file directory for test files
		File[] directoryListing = dir.listFiles(); // files inside directory
		if (directoryListing != null) {
			for (File child : directoryListing) { // for each file inside the
													// dir
				if (child.isDirectory()) { // add to output if its a wav file
					files.add(child);
				}
			}
		} else {
			throw new WavFileException("File path given is either not a directory or it is empty.");
		}
		return files;
	}

	public static ArrayList<File> getFiles(File path) throws IOException, WavFileException {
		return getFiles(path.getAbsolutePath());
	}

}
