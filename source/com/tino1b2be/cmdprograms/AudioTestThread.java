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

import com.tino1b2be.audio.AudioFileException;
import com.tino1b2be.audio.WavFileException;
import com.tino1b2be.dtmfdecoder.DTMFDecoderException;
import com.tino1b2be.dtmfdecoder.DTMFUtil;

/**
 * Test Thread to run the dtmf decoder on audio recordings.
 * 
 * @author tino1b2be
 *
 */
public class AudioTestThread extends Thread {

	private ArrayList<File> files;
	private int start;
	private AudioTestResult[] results;

	/**
	 * Test Thread to run the dtmf decoder on audio recordings.
	 * 
	 * @param fileList
	 *            List of files to be decoded
	 * @param results
	 *            Results array to store the results of the tests, this can be
	 *            shared amoungst other threads
	 * @param start
	 *            index to start writig the test results to in the given array
	 */
	public AudioTestThread(ArrayList<File> fileList, AudioTestResult[] results, int start) {
		this.files = fileList;
		this.start = start;
		this.results = results;
	}

	/**
	 * Method to go through the audio files in the thread and decode them in
	 * search for DTMF tones.
	 */
	public void run() {
		try {
			sequentialRun();
		} catch (IOException | WavFileException | DTMFDecoderException e) {
			e.printStackTrace();
		} catch (AudioFileException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void sequentialRun() throws AudioFileException, Exception {
		int i = start;
		for (File file : files) {
			DTMFUtil dtmf = new DTMFUtil(file);
			dtmf.decode();
			String decoded = dtmf.getDecoded()[0];
			results[i++] = new AudioTestResult(file, decoded);
		}
	}

}
