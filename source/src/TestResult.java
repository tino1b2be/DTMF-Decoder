package src;

public class TestResult {
	
	
	private String original;
	private String decoded;
	private boolean success;

	public TestResult(String original, String decoded) {
		this.original = original;
		this.decoded = decoded;
		this.success = original.equals(decoded);
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

}
