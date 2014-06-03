package test.intraproc;

/**
 * Test case for Rule 4 in figure 8.
 * 
 * @author yufeng
 * 
 */

public class TestRule6 {

	// if-else.
	Z foo(Z a) {
		int i = 1;
		Z v, u, ret;
		X b, c, e;
		c = new X();
		if (i > 0) {
			v = new Z();
			ret = v;
			b = ret.f;

		} else {
			u = new Z();
			ret = u;
			b = a.f;
		}
		e = c;

		return ret;
	}

	class Z {
		X f;

	}

	class X {

	}
}

// PASSED!
// we still need to fix the PHI node

// public class Harness {
// public static void main(String[] args) {
// new Harness().foo(new Z());
// }
//
// // if-else.
// Z foo(Z a) {
// int i = 1;
// Z v, u, ret;
// X b, c, e;
// c = new X();
// if (i > 0) {
// v = new Z();
// ret = v;
// b = ret.f;
//
// } else {
// u = new Z();
// ret = u;
// b = a.f;
// }
// e = c;
//
// return ret;
// }
//
// }
//
// class Z {
// X f;
// }
//
// class X {
//
// }
