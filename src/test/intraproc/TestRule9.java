package test.intraproc;


/**
 * Test case for static put/load.
 * 
 * @author yufeng
 * 
 */
public class TestRule9 {

	static Z z;

	class Z {

	}

	class X {
		Z z = new Z();
	}

	class Y extends X {
	}

	// long access path.
	void foo(Z a) {
		Y y = new Y();
		X x = new X();
		z = x.z;
		y.z = new Z();
		x = y;
		y.z = z;

	}
}

// PASSED!

// public class Harness {
// public static void main(String[] args) {
// new Harness().foo(new Harness().new Z());
// }
//
// static Z z;
//
// class Z {
//
// }
//
// class X {
// Z z = new Z();
// }
//
// class Y extends X {
// }
//
// // long access path.
// void foo(Z a) {
// Y y = new Y();
// X x = new X();
// z = x.z;
// y.z = new Z();
// x = y;
// y.z = z;
//
// }
// }