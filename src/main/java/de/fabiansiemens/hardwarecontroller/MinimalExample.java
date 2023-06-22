package de.fabiansiemens.hardwarecontroller;

/**
 * Dies ist ein Beispiel wie der HardwareController verwendet wird.
 * @author Fabian Siemens
 *
 */
public class MinimalExample implements HardwareListener {
	
	private static int buttonPressCount = 0;
	
	public static void main(String[] args) throws Exception {
		
		HardwareController controller = HardwareController.getInstance();
		controller.addListener(new MinimalExample());
		
		while(buttonPressCount < 5) {
			try {
				Thread.sleep(500);
			}
			catch(InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		controller.shutdown();
	}
	
	private static void printMatrix(boolean[][] matrix, HardwareController controller) {
		 System.out.println("Input  | A B C D E F G H  <-- Output");
		  for(int row = 0; row < 8; row++) {
			  System.out.print("     ");
			  System.out.print(row+1);
			  System.out.print(" | ");
			  for(int col = 0; col < 8; col++){
				  controller.setLed(col, row, matrix[col][row]);
				  System.out.print(matrix[col][row] ? 1 : 0);
				  System.out.print(" ");
			  }
			  System.out.print("\n");
		  }
		  System.out.print("\n");
	}

	@Override
	public void onConfirmButtonPressed(HardwareController controller) {
		boolean[][] field = controller.readField();
		printMatrix(field, controller);
		controller.blinkTrace(0, 0, 7, 7, 4);
		controller.blinkTrace(0, 4, 7, 4, 3);
		controller.blinkTrace(4, 0, 4, 7, 3);
		controller.blinkTrace(0, 0, 2, 1, 3);
		controller.blinkFast(4, 4, 8);
		buttonPressCount++;
	}
}
