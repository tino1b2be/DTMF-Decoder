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
import java.io.IOException;

/**
 * Class to represent an audio wav file
 * 
 * @author Tinotenda Chemvura
 *
 */
public class WavFile extends WavFileUtil implements AudioFile {
	
	private WavFileUtil wavFile;

	public WavFile(){
		super();
	}

	public WavFile(WavFileUtil other) {
		wavFile = new WavFileUtil();
		wavFile.setFile(other.getFile());
		wavFile.setIoState(other.getIoState());
		wavFile.setBytesPerSample(other.getBytesPerSample());
		wavFile.setNumFrames(other.getNumFrames());
		wavFile.setoStream(other.getoStream());
		wavFile.setiStream(other.getiStream());
		wavFile.setFloatScale(other.getFloatScale());
		wavFile.setFloatOffset(other.getFloatOffset());
		wavFile.setWordAlignAdjust(other.isWordAlignAdjust());
		wavFile.setNumChannels(other.getNumChannels());
		wavFile.setSampleRate(other.getSampleRate());
		wavFile.setBlockAlign(other.getBlockAlign());
		wavFile.setValidBits(other.getValidBits());
		wavFile.setBuffer(other.getBuffer());
		wavFile.setBufferPointer(other.getBufferPointer());
		wavFile.setBytesRead(other.getBytesRead());
		wavFile.setFrameCounter(other.getFrameCounter());
		
	}

	public WavFile(File exportFile, int numChannels, long numFrames, int resolution, int Fs) throws IOException, WavFileException {
		wavFile = WavFileUtil.newWavFile(exportFile, numChannels, numFrames, 16, Fs);
		
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
	
	public int writeFrames(double[][] sampleBuffer, int numFramesToWrite) throws IOException, WavFileException
	{
		return wavFile.writeFrames(sampleBuffer, numFramesToWrite);
	}
	
	public int writeFrames(double[] sampleBuffer, int numFramesToWrite) throws IOException, WavFileException
	{
		return wavFile.writeFrames(sampleBuffer, numFramesToWrite);
	}
	
	public long getFramesRemaining(){
		return wavFile.getFramesRemaining();
	}
	
	public void close() throws IOException
	{
		wavFile.close();
	}

	public int writeFrames(int[][] sampleBuffer, int offset, int numFramesToWrite) throws IOException, WavFileException
	{
		return wavFile.writeFrames(sampleBuffer, offset, numFramesToWrite);
	}
	
	public int writeFrames(int[] sampleBuffer, int offset, int numFramesToWrite) throws IOException, WavFileException
	{
		return wavFile.writeFrames(sampleBuffer, offset, numFramesToWrite);
	}
}
