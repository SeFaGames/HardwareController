package de.fabiansiemens.hardwarecontroller;

public class HardwareDebugger implements HardwareListener {
	private int debugStep;
	private HardwareController controller;
	
	/**
	 * Erzeugt eine neue Instanz des Hardware Debuggers.
	 * Die Instanz kann dafür genutzt werden einzelne Debug-Schritte auszuführen.
	 * Für den kompletten Debug ablauf, sollte die {@link #runDebug()} verwendet werden. 
	 * @param controller - HardwareController instanz
	 */
	public HardwareDebugger(HardwareController controller) {
		this.controller = controller;
		this.debugStep = 0;
	}
	
	/**
	 * Führt eine Reihe an Debug Schritten aus. Die Schritte werden nacheinander, nach drücken des Tasters durchgeführt.
	 * Diese Funktion fügt einen Listener zum HardwareController hinzu, welcher den LED Zustand verändern wird.
	 * Hierbei kann es zu Konflikten mit anderen Listenern kommen, die ebenfalls LEDs ansteuern.
	 */
	public static void runDebug(HardwareController controller) {
		controller.addListener(new HardwareDebugger(controller));
	}
	
	/**
	 * Führt einen Debug Schritt durch.
	 * 0 -> Alle LEDs aufleuchten lassen
	 * 1 -> Standard Schachaufstellung
	 * 2 -> Reihe für Reihe
	 * 3 -> Spalte für Spalte
	 * 4 -> Schnelles Blinken 
	 * 5 -> Trace vertikal
	 * 6 -> Trace horizontal
	 * 7 -> Trace diagonal
	 * 8 -> Trace Springer
	 * 9 -> Trace einzelnes Feld
	 * 10 -> Trace Illegal Coordinates
	 * 11 -> Print Text
	 * @param step
	 */
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
		case 9 -> 	{	//Trace Stationary
						controller.blinkTrace(0, 0, 0, 0, 3);
					}
		case 10 -> 	{	//Trace Stationary
						controller.blinkTrace(-1, -1, -2, -2, -3);
					}
		case 11 ->	{
						controller.getLedMatrix().print("White wins");
					}
		default -> controller.shutdown();
		}
	}
	
	/**
	 * Lässt den Thread temporär warten
	 * @param millis - Zeit in Millisekunden
	 */
	private void sleep(int millis) {
		try {
			Thread.sleep(millis);
		}
		catch(InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Wird ausgelöst, sobalt der Knopf am Schachbrett gedrückt wurde
	 */
	@Override
	public void onConfirmButtonPressed(HardwareController controller) {
		executeDebugStep(++debugStep);
	}
}
