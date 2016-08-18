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

import java.util.Scanner;

import com.tino1b2be.audio.AudioFileException;
import com.tino1b2be.dtmfdecoder.DTMFDecoderException;
import com.tino1b2be.dtmfdecoder.DTMFUtil;

/**
 * A program that decodes DTMF tones within a supported audio file.
 * 
 * @author tino1b2be
 *
 */
public class DTMFDecoder {
	
	/**
	 * File name for the audio file to be decoded
	 */
	private static String filename;
	
	/**
	 * A program that decodes DTMF tones within a supported audio file.
	 * 
	 * @param args 1st argument is the filename. 2nd argument is the minimum tone duration of the tones (0 or a negative number to use the ITU-T recommended duration)
	 * @throws Exception 
	 * @throws AudioFileException 
	 * 
	 */
	public static void main(String[] args) throws AudioFileException, Exception {
		
		if (args.length == 2){
			filename = args[0];
			DTMFUtil.setMinToneDuration(Integer.parseInt(args[1]));
		} else {
			getInputFromUser();
		}
		
		DTMFUtil dtmf = new DTMFUtil(filename);
		dtmf.decode();
		String[] sequence = dtmf.getDecoded();
		
		if (dtmf.getChannelCount() == 1) {
			System.out.println("The DTMF tones found in the given file are: " + sequence[0]);
		} else {
			System.out.println("The DTMF tones found in channel one are: " + sequence[0]
					+ "\nThe DTMF tones found in channel one are: " + sequence[1]);
		}	
	}
	
	/**
	 * Method to get input from the user
	 */
	private static void getInputFromUser() {
		filename = "samples/stereo.wav";
		System.out.print("Please enter the filename for the audio file to be decoded: ");
		Scanner sc = new Scanner(System.in);
		filename = sc.nextLine();
		
		System.out.print("Please enter the minimum tone duration to be used for detection. (0 for default value): ");
		double tone;
		do {
			try {
				tone = Double.parseDouble(sc.nextLine());
				DTMFUtil.setMinToneDuration((int) tone);
				break;
			} catch (NumberFormatException e){
				System.err.println("Input not a number. Please enter a valid number, decimals accepted. (0 or negative number to use default tone duration.)");
			} catch (NullPointerException e) {
				System.err.println("Please enter a valid number, decimals accepted. (0 or negative number to use default tone duration.)");
			} catch (DTMFDecoderException e) {
				System.err.println(e.getMessage());
			}
		} while (true);
		sc.close();
	}
}
