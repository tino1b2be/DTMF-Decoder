package src;

import java.io.File;
import java.io.IOException;

public class TestResult {

	private String original;
	private String decoded;
	private boolean success;
	private String path;
	public int tries = 0;
	public int hits = 0;
	public static int totalSuccess;
	

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
		// TODO Auto-generated method stub
		
	}
}
