package src;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Program to process audio recordings and lilsten for DTMF tones
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
 * @author tino1b2be
 *
 */
public class AudioRecordingsTest {
	public static void main(String[] args) throws IOException, WavFileException {
		String parent;
		if (args.length > 0)
			parent = args[0];
		else{
			parent = getParentFolerFromUser();
			// create threads of 2000 files and run 8 of them at a time
			ArrayList<ArrayList<File>> testThreadFiles = new ArrayList<>();
			ArrayList<File> testFiles = FileUtil.getFiles(parent);
			setUpThreadFiles(testThreadFiles,testFiles);
			AudioTestThread[] testThreads = setUpThreads(testThreadFiles);
			
			
		}
	}

	private static AudioTestThread[] setUpThreads(ArrayList<ArrayList<File>> testThreadFiles) {
		AudioTestThread[] testThreads = new AudioTestThread[testThreadFiles.size()];
		int i = 0;
		for (AudioTestThread thread : testThreads)
			thread = new AudioTestThread(testThreadFiles.get(i++));
		
		return testThreads;
	}

	private static void setUpThreadFiles(ArrayList<ArrayList<File>> testThreadFiles, ArrayList<File> testFiles) {
		int index = 0;
		do{
			ArrayList<File> threadFiles = new ArrayList<>();
			for (; index%1000 != 0 && index < testFiles.size(); index++){ // 1000 files per thread
				threadFiles.add(testFiles.get(index));
			}
			testThreadFiles.add(threadFiles); // add an array of 1000 files
			if (index >= testFiles.size())
				break;
		}while(true);
		
	}

	private static String getParentFolerFromUser() {
		// TODO Auto-generated method stub
		return "";
	}
}
