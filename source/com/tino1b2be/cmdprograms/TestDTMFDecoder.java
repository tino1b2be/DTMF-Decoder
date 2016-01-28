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
package com.tino1b2be.cmdprograms;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

import com.tino1b2be.audio.WavFileException;
import com.tino1b2be.dtmfdecoder.DTMFDecoderException;
import com.tino1b2be.dtmfdecoder.FileUtil;

/**
 * Program to test the DTMF Decoder on a batch of audio files whose names
 * represent the sequence of tones in the file. The decoder is designed to
 * decode DTMF tones which follow the ITU-T reccomendations
 * 
 * @author tino1b2be
 *
 */
public class TestDTMFDecoder {
	private static String parent;
	private static String resultsFilename;

	/**
	 * Program to test the DTMF Decoder on a batch of audio files whose names
	 * represent the sequence of tones in the file. The decoder is designed to
	 * decode DTMF tones which follow the ITU-T reccomendations
	 * 
	 * @param args
	 *            1st first argument is directory of files to be tested. 2nd
	 *            argument will be the filepath and/or name for the text file to
	 *            write the results to. e.g args={"Test files/Test Data",
	 *            "results.txt"}
	 * 
	 * @throws IOException
	 * @throws WavFileException
	 * @throws InterruptedException
	 * @throws DTMFDecoderException
	 */
	public static void main(String[] args)
			throws IOException, InterruptedException, DTMFDecoderException {

//		DTMFUtil.goertzel = true;
		if (args.length == 2) {
			parent = args[0];
			resultsFilename = args[1];
		} else {
			getInputFromUser();
		}

		double startT = System.currentTimeMillis();
		// create threads.

		ArrayList<ArrayList<File>> testThreadFiles = new ArrayList<>();
		ArrayList<File> testFiles = FileUtil.getFiles(parent, ".wav");
		setUpThreadFiles(testThreadFiles, testFiles);
		TestResult[] results = new TestResult[testFiles.size()];
		TestThread[] testThreads = startThreads(testThreadFiles, results);

		int hits = 0, tries = 0;
		for (TestThread thread : testThreads) {
			thread.join();
			hits += thread.hits;
			tries += thread.tries;
		}

		FileUtil.writeToFile(results, resultsFilename);
		int sum = results.length;
		double successRate = TestResult.totalSuccess * 100.0 / (sum * 1.0);
		double hitRate = (hits * 1.0) / (tries * 1.0) * 100.0;
		System.out.println("Total files: " + results.length);
		System.out.println("Success Rate = " + successRate + "%");
		System.out.println("Total Hit Rate: " + Double.toString(hitRate) + "%");
		double stopT = System.currentTimeMillis();
		System.out.println("Time taken = " + Double.toString((stopT - startT) / 1000) + "sec.");
	}

	/**
	 * Method to start the test threads
	 * 
	 * @param testThreadFiles
	 * @param results
	 * @return
	 */
	private static TestThread[] startThreads(ArrayList<ArrayList<File>> testThreadFiles, TestResult[] results) {
		TestThread[] testThreads = new TestThread[testThreadFiles.size()];
		int start = 0;
		int stop = 0;
		int i = 0;
		for (; i < testThreadFiles.size() - 1; i++) {
			stop = start + testThreadFiles.get(i).size();
			testThreads[i] = new TestThread(testThreadFiles.get(i), results, start);
			testThreads[i].start();
			start = stop;
		}

		// start another thread inside this current thread
		testThreads[i] = new TestThread(testThreadFiles.get(i), results, start);
		testThreads[i].run();
		return testThreads;
	}

	/**
	 * Method to setup the test threads.
	 * 
	 * @param testThreadFiles
	 * @param testFiles
	 */
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
		System.out.print("Please enter the directory containing the test files: ");
		Scanner sc = new Scanner(System.in);
		parent = sc.nextLine();
		System.out.print("Please enter the filename for the test results: ");
		resultsFilename = sc.nextLine();
		sc.close();
	}
}
