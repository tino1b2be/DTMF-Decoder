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
import java.util.ArrayList;

import com.tino1b2be.audio.AudioFileException;
import com.tino1b2be.dtmfdecoder.DTMFUtil;

/**
 * Test test thread to run the dtmf decoder on test data.
 * @author tino1b2be
 *
 */
public class TestThread extends Thread {

	private TestResult[] results; // results array

	private ArrayList<File> files; // wav files to be tested
	private int start; // results start index
	public int tries = 0;
	public int hits = 0;
	public double hitrate;
	private int pass = 0;
	private int total = 0;
	public double passRate;

	public String parent;

	/**
	 * Test thread to run the dtmf decoder on test files.
	 * @param files list of files to be tested
	 * @param results array to store the results in. This array can be shared amoungst several threads
	 * @param start index to start writing the results to in the results array. 
	 */
	public TestThread(ArrayList<File> files, TestResult[] results, int start) {
		this.files = files;
		this.results = results;
		this.start = start;
		this.parent = files.get(0).getParentFile().getName();
	}

	/**
	 * Start running the decoder on the audio files.
	 */
	public void run() {
		// cylces through each wav file and test the decoder
		// store the results in the results array

		// sequential run
		try {
			sequential();
		} catch (Exception e) {
			e.printStackTrace();
		}

		hitrate = (1.0 * hits) * 100.0 / (tries * 1.0);
	}

	private void sequential()
			throws AudioFileException, Exception {
		int i = start;
		DTMFUtil dtmf;
		for (File file : files) {
			dtmf = new DTMFUtil(file);
			dtmf.decode();
			String decoded = dtmf.getDecoded()[0];
			results[i] = new TestResult(file, decoded);
			tries += results[i].tries;
			hits += results[i].hits;
			total++;
			if (results[i++].isSuccess())
				pass++;
		}
	}
	
	public String toString(){
		passRate = (100.0*pass)/(total*1.0);
//		return "Folder : " + parent + " Hit Rate: " + hitrate + " Pass Rate: " + passRate;
		return parent.substring(0, parent.length() - 2) + "," + passRate;
	}
}
