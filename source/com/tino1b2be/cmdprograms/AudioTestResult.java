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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class to store the rest results fromt the Audio Test Recordings.
 * @author tino1b2be
 *
 */
public class AudioTestResult {

	private File file;
	private String decoded;
	private boolean status = false;
	
	/**
	 * Number of files with tones
	 */
	public static AtomicInteger filesWithTones = new AtomicInteger(0);

	/**
	 * Create a Test Result object with the original file and the decoder results
	 * @param file
	 * @param decoded
	 */
	public AudioTestResult(File file, String decoded) {
		this.decoded = decoded;
		this.file = file;
		processDecoded();
	}
	
	private void processDecoded() {
		for (int i = 0; i < decoded.length()-2; i++){
			if (!decoded.substring(i, i+1).equals("_")){
				status = true;
				filesWithTones.incrementAndGet();
				break;
			}
		}	
	}

	public boolean hasTones(){
		return decoded.length() != 0;
	}
	
	public String toString(){
		return file.toString() + " , " + decoded;
	}

	public boolean sequenceFound() {
		return status;
	}

}
