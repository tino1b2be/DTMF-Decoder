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

import java.io.IOException;

/**
 * Interface for Audio Files that can be used in the DTMF Decoder.
 * @author Tinotenda Chemvura
 *
 */
public interface AudioFile {
	
	/**
	 * Enum for the types of audio files that are supported by the dtmf decoder.
	 * 
	 * @author tino1b2be
	 */
	public enum AudioType{
		// More audio types can be added as they are implemented.
		MP3, WAV;
	}
	
	/**
	 * Read samples from an audio file with 1 channel (mono)
	 * 
	 * @param buffer buffer To store the samples read from the file
	 * @return number of frames read
	 * @throws AudioFileException 
	 */
	public int read(double[] buffer) throws AudioFileException;
	
	/**
	 * Read samples from an audio file with 2 channels (stereo
	 * 
	 * @param buffer To store the samples read from the file
	 * @return Number of frames read
	 * @throws AudioFileException 
	 */
	public int read(double[][] buffer) throws AudioFileException;
	
	
	/**
	 * Method to get the properties of the audio file
	 * @return String representation of the file properties
	 */
	public String getFileProperties();
	
	/**
	 * Method to get the sampling freqeuncy of the audio file
	 * @return The sampling frequencey
	 */
	public int getSampleRate();
	
	/**
	 * Method to close the input stream
	 * @throws IOException
	 */
	public void close() throws IOException;

	/**
	 * Method to get the number of channels in the file
	 * @return Number of channels in the audio file
	 */
	public int getNumChannels();
}
