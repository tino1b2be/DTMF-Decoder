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
 */

package com.tino1b2be.audio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Class to represent an MP3 audio file
 * @author tino1b2be
 *
 */
public class MP3File extends MP3Decoder implements AudioFile{

	public MP3File(String filename) throws FileNotFoundException, UnsupportedAudioFileException, IOException {
		super(new FileInputStream(new File(filename)));
	}
	
	public MP3File(File file) throws UnsupportedAudioFileException, IOException{
		super(new FileInputStream(file));
	}
	
	public MP3File(InputStream stream) throws UnsupportedAudioFileException, IOException{
		super(stream);
	}

	@Override
	public int read(double[] buffer) {
		return this.readSamples(buffer);
	}

	@Override
	public int read(double[][] buffer) {
		return this.readSamples(buffer);
	}

	@Override
	public String getFileProperties() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getSampleRate() {
		return (int) this.getIn().getFormat().getSampleRate();
	}

	@Override
	public int getNumChannels() {
		return this.getIn().getFormat().getChannels();
	}
	

}
