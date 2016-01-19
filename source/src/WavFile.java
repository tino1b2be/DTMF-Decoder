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

package src;

import java.io.IOException;

/**
 * Class to represent an audio wav file
 * 
 * @author Tinotenda Chemvura
 *
 */
public class WavFile extends WavFileUtil implements AudioFile {
	
	public WavFile(){
		super();
	}

	public WavFile(WavFileUtil other) {
		setFile(other.getFile());
		setIoState(other.getIoState());
		setBytesPerSample(other.getBytesPerSample());
		setNumFrames(other.getNumFrames());
		setoStream(other.getoStream());
		setiStream(other.getiStream());
		setFloatScale(other.getFloatScale());
		setFloatOffset(other.getFloatOffset());
		setWordAlignAdjust(other.isWordAlignAdjust());
		setNumChannels(other.getNumChannels());
		setSampleRate(other.getSampleRate());
		setBlockAlign(other.getBlockAlign());
		setValidBits(other.getValidBits());
		setBuffer(other.getBuffer());
		setBufferPointer(other.getBufferPointer());
		setBytesRead(other.getBytesRead());
		setFrameCounter(other.getFrameCounter());
		
	}

	@Override
	public int read(double[] buffer) throws AudioFileException {
		try {
			return this.readFrames(buffer, buffer.length);
		} catch (IOException | WavFileException e) {
			throw new AudioFileException(e.getMessage());
		}
	}

	@Override
	public int read(double[][] buffer) throws AudioFileException {
		try {
			return this.readFrames(buffer, buffer[0].length);
		} catch (IOException | WavFileException e) {
			throw new AudioFileException(e.getMessage());
		}
	}

	@Override
	public String getFileProperties() {
		// TODO Auto-generated method stub
		return null;
	}

}
