package framework.scuba.domain;

// Context is in the following format:
// ctx_1 || ctx_2 || .. || ctx_n
// where ctx_n is the latest one

public class Context implements Numberable {

	final protected ProgPoint curr;

	final protected Context prevCtx;

	// number starts from 1, and 1 is for empty context
	private int number;

	// curr = null, prevCtx = null, number = 1 is the base case
	public Context(ProgPoint curr, Context prevCtx, int number) {
		this.curr = curr;
		this.prevCtx = prevCtx;
		setNumber(number);
	}

	public int length() {
		return curr == null ? 0 : 1 + prevCtx.length();
	}

	public boolean contains(ProgPoint point) {
		return curr == null ? false
				: (curr == point || prevCtx.contains(point));
	}

	// --------------- Numberable ------------------
	@Override
	public int getNumber() {
		return number;
	}

	@Override
	public void setNumber(int number) {
		this.number = number;
	}

	// --------------- Regular ------------------
	@Override
	public int hashCode() {
		assert number > 0 : "Ctx should have non-negative number.";
		return number;
	}

	@Override
	public boolean equals(Object other) {
		return this == other;
	}

	@Override
	public String toString() {
		return "[Ctx]" + curr + " || " + prevCtx;
	}

}
