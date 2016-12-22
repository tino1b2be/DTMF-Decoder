# [DTMF Decoder](http://tino1b2be.github.io/DTMF-Decoder/)
For the project page click [here](http://tino1b2be.github.io/DTMF-Decoder/).

## What is DTMF?
**[DTMF](https://en.wikipedia.org/wiki/Dual-tone_multi-frequency_signaling)** stands for **Dual Tone Multi Frequency**. This is an in-band telecommunication signalling system using voice-frequency band over telephone lines between telephone equipment and other communications devices and switching centres. DTMF is used to represent up to 16 keys (most telephones only use 12 of these). Each key is represented by two different frequencies. The first bin (lower frequencies) consist of frequencies under 1kHz and the second bin (Upper bin) consists of frequencies above 1.2kHz. The combination of the two tones will be distinctive and different from tones of other keys and these tones cannot be mimicked by voice or random signals.

## DTMF-Decoder
The intent of this project is to design a DTMF Decoder and create a Java API for a it. I started this project while I was on a short internship at **[VASTech](http://www.vastech.co.za/)** during the December 2015-January 2016 UCT vacation break. My mentor for this project was Albert Visagie (@avisagie).

### DTMF-Decoder API Specifications
The API is designed for use in programs where a DTMF signal needs to be decoded (given it is in a valid form of .mp3 file, .wav file or as an array of sample points; either as `double[]` (mono) or as `double[2][]` (stereo)).

* DTMF Decoder for **_.wav_** and **_.mp3_** files or when given an array of sample points. (`double[]` / `double[2][]`).
* The API can decode only mono and stereo channeled audio signals. It separately decodes and returns the DTMF tones found in each channel.
* Has an audio file interface which can be implemented for more audio file types (ogg, wma, etc...)
* DTMF Tone/Sequence **_Generator_** that can export to **_.wav_** files.
* Goertzel Class which can be used independently with arrays of sample points representing a signal.
* The API includes a GUI Application (_Java Swing_) which can decode DTMF .mp3 and .wav files and also generate DTMF tone sequences.

### Possible Improvements
* Optimising the Goertzel Class to improve on speed and performance.
* Coming up with a more efficient way to detect noise and human speech to improve rejection and minimise false hits when decoding random noise files.
* Decoder could give a precise location (time) of detected tones within the audio file.
* Implement signal processing techniques that improve detection like correlation to boost the SNR, window functions to reduce spectral leakage, etc... (I hadn't studied these at the time of this project)

## Usage
Check out this [small CMD program](https://github.com/tino1b2be/DTMF-Decoder/blob/master/source/com/tino1b2be/cmdprograms/DTMFDecoder.java) that uses the decoder.
To use this decoder in your code, import `com.tino1b2be.dtmfdecoder.DTMFUtil;`

### For `.mp3` or `.wav` files

If you have a signal you want to decoded that is saved as a `.mp3` or `.wav` , it can be decoded this way:

```java
DTMFUtil dtmf = new DTMFUtil(filename);
dtmf.decode();
String left_channel = dtmf.getDecoded()[0];
String right_channel = dtmf.getDecoded()[1]; // only works if it exists else it throws an indexing error
```

Where `filename` is the path to the `.mp3` or `.wav` file. More file types can be implemented using the AudioFile interface but only these two are implemented so far.

### For a given array of samples

This is particularly useful if you are decoding an audio (or any signal) stream. If you have an array of samples of the signal ( from `-1` to `1` with a mean of `0`) and where `Fs` is the sampling frequency of the signal, the decoder can be used this way:

#### For 1 channel signal (mono)
```java
int Fs = 8000;
double[] samples = {/* array of samples */}
DTMFUtil dtmf = new DTMFUtil(samples, Fs);
dtmf.decode();
String sequence = dtmf.getDecoded()[0];
```

#### For 2 channel signal (stereo)
```java
int Fs = 8000;
double[][] samples = {{/* array of samples from first channel */},{/* array of samples from second channel */}}
DTMFUtil dtmf = new DTMFUtil(samples, Fs);
dtmf.decode();
String[] sequence = dtmf.getDecoded();
String first_channel = sequence[0];
String second_channel = sequence[1];
```

## Support or Contact
A PDF version of the full report on this project can be viewed [here](https://github.com/tino1b2be/DTMF-Decoder/blob/master/Documentation/DTMF%20Decoder%20Report.pdf). This report covers everything from the research made in the project, the pseudo code and algorithms used along with the motivations for using them, testing and much more. You can contact me for more information on my email (ttchemvura@gmail.com). To find out more about me please visit my [website](http://tino1b2be.com).

## Licence
The project is licensed under the [MIT License](https://github.com/tino1b2be/DTMF-Decoder/raw/master/LICENSE).
