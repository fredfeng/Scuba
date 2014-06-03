package test.intraproc;


/**
 * Test case for long access path.
 * 
 * @author yufeng
 * 
 */
public class TestRule5 {

	class Z {
		G g;
	}

	class G {
		G g = new G();
	}

	class X {
		Z z;
	}

	class Y extends X {
	}

	// long access path.
	void foo(Z a) {
		Y y = new Y();
		X x = new X();

		Z z = new Z();
		y.z = z;
		x = y;
		x.z.g = new G();
		X x1 = x;

		G g = x1.z.g;
		G g1 = x1.z.g.g;

	}
}

// PASSED!
// we have not consider the method call
// so we cannot handle x.z.g = new G()
// so x.z.g does not have g field
// so we cannot test the field selector currently
// i.e., maybe we should 

// public class Harness {
// public static void main(String[] args) {
// new Harness().foo(new Harness().new Z());
// }
//
// class Z {
// G g;
// }
//
// class G {
// G g = new G();
// }
//
// class X {
// Z z;
// }
//
// class Y extends X {
// }
//
// // long access path.
// void foo(Z a) {
// Y y = new Y();
// X x = new X();
//
// Z z = new Z();
// y.z = z;
// x = y;
// x.z.g = new G();
// X x1 = x;
//
// G g = x1.z.g;
// G g1 = x1.z.g.g;
// }
// }