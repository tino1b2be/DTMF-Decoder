package com.tino1b2be.audio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.HashMap;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.tritonus.share.TDebug;
import org.tritonus.share.sampled.FloatSampleBuffer;
import org.tritonus.share.sampled.file.TAudioFileReader;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Header;
import javazoom.spi.mpeg.sampled.file.MpegAudioFileFormat;
import javazoom.spi.mpeg.sampled.file.MpegAudioFormat;
import javazoom.spi.mpeg.sampled.file.MpegEncoding;
import javazoom.spi.mpeg.sampled.file.MpegFileFormatType;

/**
 * Another mp3 decoder. I got a suspicion that the other one sucks a bit :/
 * @author mzechner
 *
 */
public class MP3Decoder
{				
	AudioInputStream in;
	FloatSampleBuffer buffer;
	byte[] bytes;
	
	public MP3Decoder( InputStream stream ) throws Exception
	{
		InputStream in = new BufferedInputStream( stream, 1024*1024 );
		this.in = new MP3AudioFileReader( ).getAudioInputStream( in );
		AudioFormat baseFormat = this.in.getFormat();
		AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
											baseFormat.getSampleRate(), 16,
											baseFormat.getChannels(),
											baseFormat.getChannels() * 2,
											baseFormat.getSampleRate(), false);
		this.in = AudioSystem.getAudioInputStream(format, this.in);
		// TODO might need to close "in"
	}

	
	public int readSamples(double[] samples) 
	{
		if( buffer == null || buffer.getSampleCount() < samples.length )
		{
			buffer = new FloatSampleBuffer( in.getFormat().getChannels(), samples.length, in.getFormat().getSampleRate() );
			bytes = new byte[buffer.getByteArrayBufferSize( in.getFormat() )];
		}
			
		int read = 0;			
		int readBytes = 0;
		try {
			readBytes = in.read( bytes, read, bytes.length - read );
		} catch (IOException e) {
			return 0;
		}
		if( readBytes == -1 )
			return 0;
		
		read += readBytes;
		while( readBytes != -1 && read != bytes.length )
		{
			try {
				readBytes = in.read( bytes, read, bytes.length - read );
			} catch (IOException e) {
				return 0;
			}
			read += readBytes;
		}	
		
		int frameCount = bytes.length / in.getFormat().getFrameSize();
		buffer.setSamplesFromBytes(bytes, 0, in.getFormat(), 0, frameCount);
		
		for( int i = 0; i <buffer.getSampleCount(); i++ )
		{						
			if( buffer.getChannelCount() == 2 )
				samples[i] = (buffer.getChannel(0)[i] + buffer.getChannel(1)[i]) / 2;
			else{
				samples[i] = buffer.getChannel(0)[i];
			}
		}
		
		return buffer.getSampleCount();
	}
	
	public int readSamples(double[][] samples) 
	{
		if( buffer == null || buffer.getSampleCount() < samples.length )
		{
			buffer = new FloatSampleBuffer( in.getFormat().getChannels(), samples[0].length, in.getFormat().getSampleRate() );
			bytes = new byte[buffer.getByteArrayBufferSize( in.getFormat() )];
		}
			
		int read = 0;			
		int readBytes = 0;
		try {
			readBytes = in.read( bytes, read, bytes.length - read );
		} catch (IOException e) {
			return 0;
		}
		if( readBytes == -1 )
			return 0;
		
		read += readBytes;
		while( readBytes != -1 && read != bytes.length )
		{
			try {
				readBytes = in.read( bytes, read, bytes.length - read );
			} catch (IOException e) {
				return 0;
			}
			read += readBytes;
		}	
		
		int frameCount = bytes.length / in.getFormat().getFrameSize();
		buffer.setSamplesFromBytes(bytes, 0, in.getFormat(), 0, frameCount);
		
		for (int i = 0; i < buffer.getSampleCount(); i++) {
			samples[0][i] = buffer.getChannel(0)[i];
			samples[1][i] = buffer.getChannel(1)[i];
		}
		
		return buffer.getSampleCount();
	}
	
	public void close() throws IOException{
		in.close();
	}

	class MP3AudioFileReader extends TAudioFileReader
	{
		public static final int	INITAL_READ_LENGTH	= 128000;
		private static final int MARK_LIMIT = INITAL_READ_LENGTH + 1;
		private final AudioFormat.Encoding[][]	sm_aEncodings			= {
				{ MpegEncoding.MPEG2L1, MpegEncoding.MPEG2L2, MpegEncoding.MPEG2L3 },
				{ MpegEncoding.MPEG1L1, MpegEncoding.MPEG1L2, MpegEncoding.MPEG1L3 },
				{ MpegEncoding.MPEG2DOT5L1, MpegEncoding.MPEG2DOT5L2,
				MpegEncoding.MPEG2DOT5L3 },									
		};

		protected MP3AudioFileReader( ) {
			super(MARK_LIMIT, true);
		}

		@Override
		protected AudioFileFormat getAudioFileFormat(InputStream inputStream, long mediaLength)
		throws UnsupportedAudioFileException, IOException {
			HashMap<String, Object> aff_properties = new HashMap<String, Object>();
			@SuppressWarnings("rawtypes")
			HashMap<String, Comparable> af_properties = new HashMap<String, Comparable>();
			int mLength = (int)mediaLength;
//			int size = inputStream.available();
			PushbackInputStream pis = new PushbackInputStream(inputStream, MARK_LIMIT);
			byte head[] = new byte[22];
			pis.read(head);			

			// Check for WAV, AU, and AIFF, Ogg Vorbis, Flac, MAC file formats.
			// Next check for Shoutcast (supported) and OGG (unsupported) streams.
			if ((head[0] == 'R') && (head[1] == 'I') && (head[2] == 'F')
					&& (head[3] == 'F') && (head[8] == 'W') && (head[9] == 'A')
					&& (head[10] == 'V') && (head[11] == 'E'))
			{				
//				int isPCM = ((head[21] << 8) & 0x0000FF00) | ((head[20]) & 0x00000FF);				
				throw new UnsupportedAudioFileException("WAV PCM stream found");				

			}
			else if ((head[0] == '.') && (head[1] == 's') && (head[2] == 'n')
					&& (head[3] == 'd'))
			{			  
				throw new UnsupportedAudioFileException("AU stream found");
			}
			else if ((head[0] == 'F') && (head[1] == 'O') && (head[2] == 'R')
					&& (head[3] == 'M') && (head[8] == 'A') && (head[9] == 'I')
					&& (head[10] == 'F') && (head[11] == 'F'))
			{				
				throw new UnsupportedAudioFileException("AIFF stream found");
			}
			else if (((head[0] == 'M') | (head[0] == 'm'))
					&& ((head[1] == 'A') | (head[1] == 'a'))
					&& ((head[2] == 'C') | (head[2] == 'c')))
			{				
				throw new UnsupportedAudioFileException("APE stream found");
			}
			else if (((head[0] == 'F') | (head[0] == 'f'))
					&& ((head[1] == 'L') | (head[1] == 'l'))
					&& ((head[2] == 'A') | (head[2] == 'a'))
					&& ((head[3] == 'C') | (head[3] == 'c')))
			{				
				throw new UnsupportedAudioFileException("FLAC stream found");
			}
			// Shoutcast stream ?
			else if (((head[0] == 'I') | (head[0] == 'i'))
					&& ((head[1] == 'C') | (head[1] == 'c'))
					&& ((head[2] == 'Y') | (head[2] == 'y')))
			{
				pis.unread(head);
				// Load shoutcast meta data.				
			}
			// Ogg stream ?
			else if (((head[0] == 'O') | (head[0] == 'o'))
					&& ((head[1] == 'G') | (head[1] == 'g'))
					&& ((head[2] == 'G') | (head[2] == 'g')))
			{							
				throw new UnsupportedAudioFileException("Ogg stream found");
			}
			// No, so pushback.
			else
			{
				pis.unread(head);
			}
			// MPEG header info.
			int nVersion = AudioSystem.NOT_SPECIFIED;
			int nLayer = AudioSystem.NOT_SPECIFIED;
			// int nSFIndex = AudioSystem.NOT_SPECIFIED;
			int nMode = AudioSystem.NOT_SPECIFIED;
			int FrameSize = AudioSystem.NOT_SPECIFIED;
			// int nFrameSize = AudioSystem.NOT_SPECIFIED;
			int nFrequency = AudioSystem.NOT_SPECIFIED;
			int nTotalFrames = AudioSystem.NOT_SPECIFIED;
			float FrameRate = AudioSystem.NOT_SPECIFIED;
			int BitRate = AudioSystem.NOT_SPECIFIED;
			int nChannels = AudioSystem.NOT_SPECIFIED;
			int nHeader = AudioSystem.NOT_SPECIFIED;
			int nTotalMS = AudioSystem.NOT_SPECIFIED;
			boolean nVBR = false;
			AudioFormat.Encoding encoding = null;
			try
			{
				Bitstream m_bitstream = new Bitstream(pis);
				aff_properties.put("mp3.header.pos",
						new Integer(m_bitstream.header_pos()));
				Header m_header = m_bitstream.readFrame();
				// nVersion = 0 => MPEG2-LSF (Including MPEG2.5), nVersion = 1 => MPEG1
				nVersion = m_header.version();
				if (nVersion == 2)
					aff_properties.put("mp3.version.mpeg", Float.toString(2.5f));
				else
					aff_properties.put("mp3.version.mpeg",
							Integer.toString(2 - nVersion));
				// nLayer = 1,2,3
				nLayer = m_header.layer();
				aff_properties.put("mp3.version.layer", Integer.toString(nLayer));
				// nSFIndex = m_header.sample_frequency();
				nMode = m_header.mode();
				aff_properties.put("mp3.mode", new Integer(nMode));
				nChannels = nMode == 3 ? 1 : 2;
				aff_properties.put("mp3.channels", new Integer(nChannels));
				nVBR = m_header.vbr();
				af_properties.put("vbr", new Boolean(nVBR));
				aff_properties.put("mp3.vbr", new Boolean(nVBR));
				aff_properties.put("mp3.vbr.scale", new Integer(m_header.vbr_scale()));
				FrameSize = m_header.calculate_framesize();
				aff_properties.put("mp3.framesize.bytes", new Integer(FrameSize));
				if (FrameSize < 0)
				{
					throw new UnsupportedAudioFileException("Invalid FrameSize : " + FrameSize);
				}
				nFrequency = m_header.frequency();
				aff_properties.put("mp3.frequency.hz", new Integer(nFrequency));
				FrameRate = (float)((1.0 / (m_header.ms_per_frame())) * 1000.0);
				aff_properties.put("mp3.framerate.fps", new Float(FrameRate));
				if (FrameRate < 0)
				{
					throw new UnsupportedAudioFileException("Invalid FrameRate : " + FrameRate);
				}
				if (mLength != AudioSystem.NOT_SPECIFIED)
				{
					aff_properties.put("mp3.length.bytes", new Integer(mLength));
					nTotalFrames = m_header.max_number_of_frames(mLength);
					aff_properties.put("mp3.length.frames", new Integer(nTotalFrames));
				}
				BitRate = m_header.bitrate();
				af_properties.put("bitrate", new Integer(BitRate));
				aff_properties.put("mp3.bitrate.nominal.bps", new Integer(BitRate));
				nHeader = m_header.getSyncHeader();
				encoding = sm_aEncodings[nVersion][nLayer - 1];
				aff_properties.put("mp3.version.encoding", encoding.toString());
				if (mLength != AudioSystem.NOT_SPECIFIED)
				{
					nTotalMS = Math.round(m_header.total_ms(mLength));
					aff_properties.put("duration", new Long((long)nTotalMS * 1000L));
				}
				aff_properties.put("mp3.copyright", new Boolean(m_header.copyright()));
				aff_properties.put("mp3.original", new Boolean(m_header.original()));
				aff_properties.put("mp3.crc", new Boolean(m_header.checksums()));
				aff_properties.put("mp3.padding", new Boolean(m_header.padding()));
				InputStream id3v2 = m_bitstream.getRawID3v2();
				if (id3v2 != null)
				{
					aff_properties.put("mp3.id3tag.v2", id3v2);			
				}
				if (TDebug.TraceAudioFileReader)
					TDebug.out(m_header.toString());
			}
			catch (Exception e)
			{			
				throw new UnsupportedAudioFileException("not a MPEG stream:"
						+ e.getMessage());
			}
			// Deeper checks ?
			int cVersion = (nHeader >> 19) & 0x3;
			if (cVersion == 1)
			{
				throw new UnsupportedAudioFileException(
				"not a MPEG stream: wrong version");
			}
			int cSFIndex = (nHeader >> 10) & 0x3;
			if (cSFIndex == 3)
			{

				throw new UnsupportedAudioFileException(
				"not a MPEG stream: wrong sampling rate");
			}

			AudioFormat format = new MpegAudioFormat(encoding, (float)nFrequency,
					AudioSystem.NOT_SPECIFIED // SampleSizeInBits
					// -
					// The
					// size
					// of a
					// sample
					, nChannels // Channels - The
					// number of
					// channels
					, -1 // The number of bytes in
					// each frame
					, FrameRate // FrameRate - The
					// number of frames
					// played or
					// recorded per
					// second
					, true, af_properties);
			return new MpegAudioFileFormat(MpegFileFormatType.MP3, format,
					nTotalFrames, mLength, aff_properties);
		}
		

	}
}
