package de.fabiansiemens.hardwarecontroller;

public class HardwareDebug implements HardwareListener {
	private int debugStep;
	private HardwareController controller;
	
	public HardwareDebug(HardwareController controller) {
		this.controller = controller;
		this.debugStep = 0;
	}
	
	public static void runDebug() {
		HardwareController controller = HardwareController.getInstance();
		controller.addListener(new HardwareDebug(controller));
	}
	
	public void executeDebugStep(int step) {
		switch(step) {
		case 0 -> 	{	//Alle LEDs leuchten lassen
						controller.getLedMatrix().enableAll();
					}
		case 1 -> 	{	//Schachaufstellung
						controller.clearLeds();
						controller.setRow(0, true);
						controller.setRow(1, true);
						controller.setRow(6, true);
						controller.setRow(7, true);
					}
		case 2 -> 	{	//Row für Row
						for(int i = 0; i < 8; i++) {
							controller.setRow(i, true);
							sleep(100);
							controller.setRow(i, false);
						}
					}
		case 3 -> 	{	//Column für Column
						for(int i = 0; i < 8; i++) {
							controller.setColumn(i, true);
							sleep(100);
							controller.setColumn(i, false);
						}
					}
		case 4 -> 	{	//Fast Blink
						controller.blinkFast(4, 4, 10);
					}
		case 5 -> 	{	//Trace Column
						controller.blinkTrace(0, 0, 0, 7, 3);
					}
		case 6 -> 	{	//Trace Row
						controller.blinkTrace(0, 0, 7, 0, 3);
					}
		case 7 -> 	{	//Trace Diagonal
						controller.blinkTrace(0, 0, 7, 7, 3);
					}
		case 8 -> 	{	//Trace Knight
						controller.blinkTrace(0, 0, 1, 2, 5);
					}
		case 9 -> 	{	//Trace Column
						controller.blinkTrace(0, 0, 0, 7, 3);
					}
		case 10 ->	{
						controller.getLedMatrix().print("White wins");
					}
		default -> controller.shutdown();
		}
	}
	
	private void sleep(int millis) {
		try {
			Thread.sleep(millis);
		}
		catch(InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onConfirmButtonPressed(HardwareController controller) {
		executeDebugStep(++debugStep);
	}
}
