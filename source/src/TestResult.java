package src;

public class TestResult {

	private String original;
	private String decoded;
	private boolean success;
	private String path;
	public int tries = 0;
	public int hits = 0;
	public static int totalSuccess;
	

	public TestResult(String path, String decoded) {
		this.path = path;
		this.original = DecoderUtil.getFileSequence(path);
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
		
		if (success) {
			return "~~ pass : , " + original;
		} else {
			return "** FAIL : , The file \"" + path + "\" decoded to \"" + decoded + "\" instead of \"" + original
					+ "\"";
		}

	}
}
