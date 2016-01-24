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

package com.tino1b2be.dtmfdecoder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;

import javax.sound.sampled.UnsupportedAudioFileException;

import com.tino1b2be.audio.AudioFile;
import com.tino1b2be.audio.AudioFile.AudioType;
import com.tino1b2be.audio.AudioFileException;
import com.tino1b2be.audio.MP3File;
import com.tino1b2be.audio.WavFile;
import com.tino1b2be.audio.WavFileException;
import com.tino1b2be.cmdprograms.AudioTestResult;
import com.tino1b2be.cmdprograms.TestResult;

/**
 * Class to hand File operations
 * 
 * @author Tinotenda Chemvura
 */
public class FileUtil {

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
				pw.println(dataOut.get(i).toString());
			}
		}
		pw.close();
	}

	/**
	 * Write test results to a file
	 * 
	 * @param dataOut
	 *            Array of results to be exported
	 * @param filename
	 *            Filename of the text file to be exported
	 * @throws IOException
	 */
	public static <T> void writeToFile(ArrayBlockingQueue<T> dataOut, String filename) throws IOException {
		PrintWriter pw = new PrintWriter(new FileWriter(filename));
		if (dataOut == null)
			pw.print("");
		else {
			for (T data : dataOut) {
				if (data.getClass() == TestResult.class && ((TestResult) data).isSuccess())
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
	 *            Array of results to be exported
	 * @param filename
	 *            Filename of the text file to be exported
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

	/**
	 * Method to file objects of all the directories found in the given parent
	 * directory.
	 * 
	 * @param parent
	 *            path of parent directory
	 * @return An array with file objects of the directories found in the parent
	 *         directory.
	 * @throws DTMFDecoderException
	 *             If the given file path is not a directory of if it is empty.
	 * @throws FileNotFoundException
	 *             If the directory does not exist
	 */
	public static ArrayList<File> getDirs(String parent) throws DTMFDecoderException, FileNotFoundException {
		if (!(new File(parent).exists()))
			throw new FileNotFoundException("The given file path does not exist.");
		if (!(new File(parent).isDirectory()))
			throw new DTMFDecoderException("The given filepath does not represent a directory.");

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
			throw new DTMFDecoderException("The given directpry is empty.");
		}
		return files;
	}

	/**
	 * Method to get File objects of files in the given directory (inluding
	 * those within the sub-directories)
	 * 
	 * @param directory
	 *            parent directory to start searching for the files
	 * @param extension
	 *            file extension for the files being searched for.
	 * @return an arrayList of the files found the in the given directory.
	 * @throws IOException
	 * @throws DTMFDecoderException
	 */
	public static ArrayList<File> getFiles(File directory, String extension) throws IOException, DTMFDecoderException {
		return getFiles(directory.getAbsolutePath(), extension);
	}

	/**
	 * Method to cycle through a folder and get all files that match the given
	 * extension.
	 * 
	 * @param path
	 *            File path of the parent directory
	 * @param extension
	 *            Extension of the files being searched for
	 * @return An ArrayList of files found in the folder (and subfolders) with
	 *         matching extension
	 */
	public static ArrayList<File> getFiles(String directory, String extension) {
		ArrayList<File> files = new ArrayList<>(); // output array of files
		recursiveFileSearch(directory, files, extension);
		return files;
	}

	/**
	 * Method to recursively cycle through folders and subfolders in the given
	 * directory, looking for files that match the given extension.
	 * 
	 * @param path
	 *            Path of parent firectory
	 * @param files
	 *            ArrayList to add the files found in the directory
	 * @param extension
	 *            Extension of the files to look for
	 */
	private static void recursiveFileSearch(String path, ArrayList<File> files, String extension) {
		File dir = new File(path); // file directory for test files
		File[] directoryListing = dir.listFiles(); // files inside directory
		if (directoryListing != null) {
			for (File child : directoryListing) { // for each file inside the
													// dir
				if (child.isDirectory()) {
					recursiveFileSearch(child.getPath(), files, extension);
				}
				if (child.getAbsolutePath().endsWith(extension) || child.getAbsolutePath().endsWith(extension)) {
					files.add(child);
				}
			}
		}
	}

	/**
	 * Method to write results of tests from the audio tests.
	 * 
	 * @param dataOut
	 *            list of audio test result objects
	 * @param filename
	 *            filename to store the results to
	 * @throws IOException
	 */
	public static void writeToFile(AudioTestResult[] dataOut, String filename) throws IOException {
		PrintWriter pw = new PrintWriter(new FileWriter(filename));
		if (dataOut == null)
			pw.print("");
		else {
			for (AudioTestResult data : dataOut) {
				pw.println(data);
			}
		}
		pw.close();
	}

	/**
	 * Method to write results of successful tests from the audio tests.
	 * 
	 * @param dataOut
	 *            list of audio test result objects
	 * @param filename
	 *            filename to store the results to
	 * @throws IOException
	 */
	public static void writeToFileSuccessOnly(AudioTestResult[] dataOut, String filename) throws IOException {
		PrintWriter pw = new PrintWriter(new FileWriter(filename));
		if (dataOut == null)
			pw.print("");
		else {
			for (AudioTestResult data : dataOut) {
				if (data.sequenceFound())
					pw.println(data);
			}
		}
		pw.close();
	}

	private static MP3File readMp3File(String filename) throws UnsupportedAudioFileException, IOException {

		return new MP3File(new FileInputStream(new File(filename)));
	}

	private static WavFile readWavFileBuffer(String filename) throws IOException, WavFileException {
		WavFile f = new WavFile(WavFile.openWavFile(new File(filename)));
		return f;
	}

	private static WavFile readWavFileBuffer(File file) throws IOException, WavFileException {
		WavFile f = readWavFileBuffer(file.getPath());
		return f;
	}

	/**
	 * 
	 * Method to read an audio files of a supported file type.
	 * 
	 * @param filename
	 *            filename of the audio file to be opened
	 * @return
	 * @throws AudioFileException
	 * @throws UnsupportedAudioFileException
	 * @throws IOException
	 * @throws WavFileException
	 */
	public static AudioFile readAudioFile(String filename)
			throws AudioFileException, UnsupportedAudioFileException, IOException, WavFileException {
		AudioFile f = readAudioFile(new File(filename));
		return f;
	}

	/**
	 * Method to read an audio files of a supported file type.
	 * 
	 * @param file
	 *            File of the audio file to be opened
	 * @return
	 * @throws AudioFileException
	 * @throws UnsupportedAudioFileException
	 * @throws IOException
	 * @throws WavFileException
	 */
	public static AudioFile readAudioFile(File file)
			throws AudioFileException, UnsupportedAudioFileException, IOException, WavFileException {
		if (file.getName().toLowerCase().endsWith(".mp3")) {
			return readMp3File(file.getAbsolutePath());
		} else if (file.getName().toLowerCase().endsWith(".wav")) {
			WavFile f = readWavFileBuffer(file);
			return f;
		} else {
			throw new AudioFileException("File type not supported.");
		}
	}

	/**
	 * Method to read an audio files of a supported file type.
	 * 
	 * @param filename
	 *            filename of the audio file to be opened
	 * @param type
	 *            File type of the audio file to be opened
	 * @return An audiofile that can be used by the DTMF decoder
	 * @throws IOException
	 * @throws UnsupportedAudioFileException
	 * @throws WavFileException
	 * @throws AudioFileException
	 *             When the audio file type is not supported
	 */
	public static AudioFile readAudioFile(String filename, AudioType type)
			throws UnsupportedAudioFileException, IOException, WavFileException, AudioFileException {
		if (type.equals(AudioType.MP3)) {
			return readMp3File(filename);
		} else if (type.equals(AudioType.WAV)) {
			return readWavFileBuffer(filename);
		} else
			throw new AudioFileException("File type not supported.");
	}

	public static void writeWavFile(File outFile, double[] samples, double outFs) throws IOException, WavFileException {
		int fs = (int)outFs;
		WavFile wavFile = new WavFile(outFile, 1, samples.length, 16, fs);
		// Initialise a local frame counter
		wavFile.writeFrames(samples, samples.length);
		// Close the wavFile
		wavFile.close();
	}
}
