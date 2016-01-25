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
import java.util.InputMismatchException;
import java.util.Scanner;

import com.tino1b2be.audio.WavFileException;
import com.tino1b2be.dtmfdecoder.DTMFDecoderException;
import com.tino1b2be.dtmfdecoder.DTMFUtil;

/**
 * Program to generate a sequence of DTMF tones and export to a file.
 * 
 * @author tino1b2be
 *
 */
public class GenerateDTMF {
	private static int Fs;
	private static int toneDurr;
	private static int pauseDurr;
	private static char[] chars;
	private static Scanner sc;
	private static File file;

	/**
	 * Program to generate a sequence of DTMF tones and export to a wav file.
	 * 
	 * @param args
	 *            [String of DTMF tones with no spaces between the characters,
	 *            duration of each tone (in milliseconds), duraion of pause (in
	 *            milliseconds), Fsm filename];
	 *            e.g 123abc 100 50 8000 test.wav
	 * @throws IOException
	 * @throws WavFileException
	 * @throws DTMFDecoderException
	 */
	public static void main(String[] args) throws IOException, WavFileException, DTMFDecoderException {
		setVariables(args);
		System.out.println("Now generating the sequence.");
		DTMFUtil dtmf = new DTMFUtil(file, chars, Fs, toneDurr, pauseDurr);
		if (dtmf.generate()) {
			System.out.println("Now exporting the file to \"" + file.getPath() + "\"");
			dtmf.export();
		} else {
			throw new DTMFDecoderException("Something went wrong. Sequence could not be generated.");
		}
		System.out.println("Done.");
	}

	/**
	 * Method to set the variables to be used to generate the sequence file 
	 * @param args
	 */
	private static void setVariables(String[] args) {
		if (args.length == 4){
			String ch = args[0];
			chars = new char[ch.length()];
			for (int i = 0; i < ch.length(); i++){
				chars[i] = ch.charAt(i);
			}
			toneDurr = Integer.parseInt(args[1]);
			pauseDurr = Integer.parseInt(args[2]);
			Fs = Integer.parseInt(args[3]);
			file = new File(args[3]);
			return;
		} else {
			sc = new Scanner(System.in);
			do {
				System.out.print("Please enter the sequence of DTMF characters to generate (no spaces between them) : ");
				String ch = sc.nextLine();
				String[] ch2 = ch.split(" ");
				if (ch2.length > 1){
					System.err.println("No spaces between characters! Please try again.");
					continue;
				}
				chars = new char[ch.length()];
				for (int i = 0; i < ch.length(); i++){
					chars[i] = ch2[0].charAt(i);
				}
				break;
			}while(true);
			//get the tone and pause duration
			do {
				try{
					System.out.print("Please enter duration of each tone (in milliseconds) : ");
					toneDurr = sc.nextInt();
					System.out.print("Please enter the duration of each pause between tones (in milliseconds) : ");
					pauseDurr = sc.nextInt();
					System.out.print("Please enter the sampling frequency (>8kHz) : ");
					Fs = sc.nextInt();
					break;
				} catch (InputMismatchException e){
					System.err.println("Please enter an integer value!");
					continue;
				}
			}while(true);
					
			//get output filename
			System.out.print("Please enter the name of the output file. : ");
			sc = new Scanner(System.in);
			file = new File(sc.nextLine());
		}
		sc.close();
	}
}
