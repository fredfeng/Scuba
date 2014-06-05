package test.interproc;

//simple example from the paper.
public class TestInter1 {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		int a = 1;
	}
	
	class foo {
		void bar() {
			X x = new X();
		}
	}
	
	class X {
		void goo(){
			
		}
	}

}
