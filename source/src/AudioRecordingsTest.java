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
package src;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Program to process audio recordings and lilsten for DTMF tones
 * 
 * @author tino1b2be
 *
 */
public class AudioRecordingsTest {
	public static void main(String[] args) throws IOException, WavFileException, InterruptedException {
		double startT = System.currentTimeMillis();
		DTMFUtil.debug = false;
		DTMFUtil.decode80 = true;
		String parent;
		if (args.length > 0)
			parent = args[0];
		else{
			parent = getParentFolerFromUser();
			// create threads of 2000 files and run 8 of them at a time
			ArrayList<ArrayList<File>> testThreadFiles = new ArrayList<>();
			ArrayList<File> testFiles = FileUtil.getFiles(parent);
			setUpThreadFiles(testThreadFiles,testFiles);
			AudioTestResult[] results = new AudioTestResult[testFiles.size()];
			AudioTestThread[] testThreads = startThreads(testThreadFiles, results);
			for (AudioTestThread thread : testThreads)
				thread.join();
//			FileUtil.writeToFile(results, "Audio Recordings results (60ms).txt");
			FileUtil.writeToFileSuccessOnly(results, "Audio Recordings results (80ms) (Found).txt");
			double perc = AudioTestResult.filesWithTones.get() * 100.0/testFiles.size();
			System.out.println("done"
					+ "\nFiles with tones = " + AudioTestResult.filesWithTones.get() + " = " + perc + "% of all files.");
			
			double stopT = System.currentTimeMillis();
			System.out.println("Time taken = " + Double.toString((stopT-startT)/1000) + "sec.");
			
		}
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
		do{
			ArrayList<File> threadFiles = new ArrayList<>();
			threadFiles.add(testFiles.get(index++));
			int stop = testFiles.size() / 8;
			for (; index%stop != 0 && index < testFiles.size(); index++){ // make 8 queues
				threadFiles.add(testFiles.get(index));
			}
			testThreadFiles.add(threadFiles); // add an array of 1000 files
			if (index >= testFiles.size())
				break;
		}while(true);
		
	}

	private static String getParentFolerFromUser() {
		// TODO Auto-generated method stub
		return "/media/tino1b2be/lin_2/wavs/converted/TestAPI2/";
	}
}
