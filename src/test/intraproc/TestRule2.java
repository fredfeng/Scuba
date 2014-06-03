package test.intraproc;

/**
 * Test case for Rule2 in figure 8.
 * 
 * @author yufeng
 * 
 */
public class TestRule2 {

	class Z {
		X x;
	}

	class X {

	}

	// v1 = v2.f
	void foo(Z a) {
		X x = a.x;
	}
}

// PASSED!

// public class Harness {
// public static void main(String[] args) {
// foo(new Harness().new Z());
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
// // v1 = v2.f
// static void foo(Z a) {
// X x = a.x;
// }
// }

