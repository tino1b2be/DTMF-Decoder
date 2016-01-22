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

import com.tino1b2be.dtmfdecoder.DecoderUtil;

/**
 * Class to store the test results from the tests on the dtmf decoder
 * @author tino1b2be
 *
 */
public class TestResult {

	private String original;
	private String decoded;
	private boolean success;
	private String path;
	public int tries = 0;
	public int hits = 0;
	private double hitrate;
	public static int totalSuccess;
	
	/**
	 * create a test result object given the file and the decoder results
	 * @param file File that is being tested
	 * @param decoded The results of the dtmf decoder
	 * @throws IOException
	 */
	public TestResult(File file, String decoded) throws IOException {
		this.path = file.getAbsolutePath();
		this.original = DecoderUtil.getFileSequence(file.getPath());
		this.decoded = decoded;
		this.success = original.equals(decoded);
		tries += original.length();
		hits += decoded.length();
	}

	public String getOriginal() {
		return original;
	}

	public String getDecoded() {
		return decoded;
	}

	public boolean isSuccess() {
		return success;
	}

	public double getHitrate(){
		return hitrate;
	}
	
	public String toString() {
		calcHitRate();
		if (success) {
			return "~~ pass : , " + path ;
		} else {
			return "** FAIL : , The file \"" + path + "\" decoded to \"" + decoded + "\" instead of \"" + original
					+ "\"";
		}

	}
	
	private void calcHitRate() {
		hitrate = 100.0 * hits / (tries * 1.0);
	}
}
