package test.intraproc;

/**
 * Test case for Rule3 and 4 in figure 8.
 * 
 * @author yufeng
 * 
 */
public class TestRule3 {

	class Z {
		X x;
	}

	class X {

	}

	// v1.f = v2.
	void foo(Z a) {
		X y = new X();
		a.x = y;
	}
}

// PASSED!

// public class Harness {
// public static void main(String[] args) {
// new Harness().foo(new Harness().new Z());
// }
//
// class Z {
// X x;
// }
//
// class X {
//
// }
//
// // v1.f = v2.
// void foo(Z a) {
// X y = new X();
// a.x = y;
// }
// }
