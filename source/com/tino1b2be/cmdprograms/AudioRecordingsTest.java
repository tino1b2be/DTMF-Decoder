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
 * 
 */
package com.tino1b2be.cmdprograms;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import com.tino1b2be.DTMFDecoder.DTMFDecoderException;
import com.tino1b2be.DTMFDecoder.DTMFUtil;
import com.tino1b2be.DTMFDecoder.FileUtil;
import com.tino1b2be.audio.WavFileException;

/**
 * Program to process audio recordings and listen for DTMF tones
 * 
 * @author tino1b2be
 *
 */

public class AudioRecordingsTest {
	private static String parent;
	private static String resultsFilename;
	private static int toneDuration;
	
	/**
	 * Program to process audio recordings and listen for DTMF tones.
	 * 
	 * @param args
	 *            1st first argument is directory of files to be tested. 2nd
	 *            argument gives minimum duration of tone to be used in
	 *            milliseconds e.g (40, 60, 80, 100) 3rd argument will be the
	 *            filepath and/or name for the text file to write the results to.
	 *            e.g args={"Test files/recordings/", 80, "results.txt"}
	 *            
	 * @throws IOException
	 * @throws WavFileException
	 * @throws InterruptedException
	 * @throws DTMFDecoderException 
	 */
	public static void main(String[] args) throws IOException, WavFileException, InterruptedException, DTMFDecoderException {
		double startT = System.currentTimeMillis();
		DTMFUtil.setMinToneDuration(80);
		if (args.length == 3){
			parent = args[0];
			resultsFilename = args[1];
			toneDuration = Integer.parseInt(args[2]);
		}
		else{
			getInputFromUser();
		}
			// create 8 threads
		
		ArrayList<ArrayList<File>> testThreadFiles = new ArrayList<>();
		ArrayList<File> testFiles = FileUtil.getFiles(parent);
		setUpThreadFiles(testThreadFiles, testFiles);
		AudioTestResult[] results = new AudioTestResult[testFiles.size()];
		AudioTestThread[] testThreads = startThreads(testThreadFiles, results);
		for (AudioTestThread thread : testThreads)
			thread.join();
		// FileUtil.writeToFile(results, "Audio Recordings results (60ms).txt");
		FileUtil.writeToFileSuccessOnly(results, "Audio Recordings results (80ms) (Found).txt");
		double perc = AudioTestResult.filesWithTones.get() * 100.0 / testFiles.size();
		System.out.println("done" + "\nFiles with tones = " + AudioTestResult.filesWithTones.get() + " = " + perc
				+ "% of all files.");
		double stopT = System.currentTimeMillis();
		System.out.println("Time taken = " + Double.toString((stopT - startT) / 1000) + "sec.");

	}

	private static AudioTestThread[] startThreads(ArrayList<ArrayList<File>> testThreadFiles, AudioTestResult[] results) {
		AudioTestThread[] testThreads = new AudioTestThread[testThreadFiles.size()];
		int start = 0;
		int stop = 0;
		int i = 0;
		for (; i < testThreadFiles.size() - 1; i++){
			stop = start + testThreadFiles.get(i).size();
			testThreads[i] = new AudioTestThread(testThreadFiles.get(i), results, start, stop);
			testThreads[i].start();
			start = stop;
		}
		// start another thread inside this current thread
		testThreads[i] = new AudioTestThread(testThreadFiles.get(i), results, start, stop);
		testThreads[i].run();
		return testThreads;
	}

	private static void setUpThreadFiles(ArrayList<ArrayList<File>> testThreadFiles, ArrayList<File> testFiles) {
		int index = 0;
		do {
			ArrayList<File> threadFiles = new ArrayList<>();
			threadFiles.add(testFiles.get(index++));
			// 1000 files per thread
			for (; index % 1000 != 0 && index < testFiles.size(); index++) {
				threadFiles.add(testFiles.get(index));
			}
			testThreadFiles.add(threadFiles); // add an array of 1000 files
			if (index >= testFiles.size())
				break;
		} while (true);
	}

	private static void getInputFromUser() {
		parent = "/media/tino1b2be/lin_2/wavs/converted/TestAPI2/";
		resultsFilename = "Audio Test Results.txt";
		toneDuration = 80;
		// get input from user. TODO
	}
}
