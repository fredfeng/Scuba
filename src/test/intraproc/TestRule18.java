package test.intraproc;

public class TestRule18 {
	public static void main(String[] args) {
	}

	class X {

	}

	void foo() {
		X[][] x1 = new X[10][];
		x1[0] = new X[5];
		x1[1] = new X[6];
		x1[0][0] = new X();
	}
}
