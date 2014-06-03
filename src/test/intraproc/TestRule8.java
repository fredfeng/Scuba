package test.intraproc;


/**
 * Test case for null assignment.
 * 
 * @author yufeng
 * 
 */
public class TestRule8 {

	class Z {
		G g;
	}
	
	class G {
		G g = new G();
	}
	
	class X {
		Z z;
		Y y;
	}

	class Y extends X {
	}
	
	// long access path.
	void foo(Z a) {
		Y y = new Y();
		X x = new X();
		
		Z z = x.z;
		y.z = null;
		x.y = new Y();
		Z z1 = y.z;
	}
}


// PASSED!

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
// Y y;
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
// Z z = x.z;
// y.z = null;
// x.y = new Y();
// Z z1 = y.z;
// }
// }