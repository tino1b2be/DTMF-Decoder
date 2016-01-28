package com.tino1b2be.cmdprograms;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import com.tino1b2be.dtmfdecoder.DTMFDecoderException;
import com.tino1b2be.dtmfdecoder.DTMFUtil;
import com.tino1b2be.dtmfdecoder.FileUtil;

public class CompareDecoders {
	public static void main(String[] args) throws DTMFDecoderException, InterruptedException, IOException {
		
		ArrayList<File> parents = FileUtil.getDirs("/media/tino1b2be/lin_2/tt/");
				
		// get times for FFT
		DTMFUtil.goertzel = false;
		ArrayList<Double> times = new ArrayList<Double>();
		for (File dir : parents){
			times.add(runTests(dir));
		}
		
		FileUtil.writeToFile(times, "Times for FFT.txt");
		
		System.out.println("Done with FFT");
		
		// get times for Goertzel
		times = new ArrayList<Double>();
		DTMFUtil.goertzel = true;
		for (File dir : parents){
			times.add(runTests(dir));
		}
		
		FileUtil.writeToFile(times, "Times for Goertzel.txt");
		
		System.out.println("Done with Goertzel");
		
	}

	private static double runTests(File dir) throws InterruptedException, IOException, DTMFDecoderException {
		
		// create threads.

		ArrayList<ArrayList<File>> testThreadFiles = new ArrayList<>();
		ArrayList<File> testFiles = FileUtil.getFiles(dir, ".wav");
		setUpThreadFiles(testThreadFiles, testFiles);
		TestResult[] results = new TestResult[testFiles.size()];
		
		// start timing
		double startT = System.currentTimeMillis();
		TestThread[] testThreads = startThreads(testThreadFiles, results);

		for (TestThread thread : testThreads) {
			thread.join();
		}
		// stop timing
		double stopT = System.currentTimeMillis();
		return stopT - startT;
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
}
