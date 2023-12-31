package de.fabiansiemens.hardwarecontroller;

import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalInput;
import com.pi4j.io.gpio.digital.DigitalInputConfigBuilder;
import com.pi4j.io.gpio.digital.DigitalOutput;
import com.pi4j.io.gpio.digital.DigitalOutputConfigBuilder;
import com.pi4j.io.gpio.digital.DigitalState;
import com.pi4j.io.gpio.digital.PullResistance;
import com.pi4j.library.pigpio.PiGpio;
import com.pi4j.plugin.pigpio.provider.gpio.digital.PiGpioDigitalInputProvider;
import com.pi4j.plugin.pigpio.provider.gpio.digital.PiGpioDigitalOutputProvider;
import com.pi4j.plugin.pigpio.provider.spi.PiGpioSpiProvider;

import de.fabiansiemens.hardwarecontroller.led.LedMatrixController;

/**
 * Core.
 * 
 * Diese Klasse stellt Funktionen zum Lesen des Feldes und zum Ändern von LEDs, sowie eine Listener/Observer Schnittstelle zur Verfügung
 * Eine Instanz dieser Klasse kann mit {@link HardwareController#getInstance()} abgerufen werden.
 * Alle weiteren Operationen finden nicht statisch, sondern auf dieser HardwareController Instanz statt.
 * Am Ende der Nutzung sollte {@link HardwareController#shutdown()} aufgerufen werden um den HardwareController ordnungsgemäß zu beenden
 * @author Fabian Siemens
 */
public class HardwareController {
	
	//Hardware Konstanten
	private static final int FIELD_SIZE = 8;
	private static final int READ_SLEEP_MILLIS = 50;
	static final int[] OUTPUT_PINS = {26,21,20,16,12,7,25,24};	//Reihenfolge wichtig
	static final int[] INPUT_PINS = {19,13,6,5,27,9,17,22};
	static final int BUTTON_PIN = 18;
	
	
	private static HardwareController INSTANCE;
	
	private List<HardwareListener> listener;
	private LinkedList<DigitalOutput> outputs;
	private LinkedList<DigitalInput> inputs;
	private DigitalInput button;
	private Context pi4j;
	private PiGpio pigpio;
	private LedMatrixController matrix;
	private volatile boolean enabled;
	
	/**
	 * Erzeugt eine neue Instanz und konfiguriert die GPIOS
	 */
	private HardwareController() {
		INSTANCE = this;
		enabled = true;
	
		this.outputs = new LinkedList<DigitalOutput>();
		this.inputs = new LinkedList<DigitalInput>();
		this.listener = new LinkedList<HardwareListener>();
		this.pigpio = PiGpio.newNativeInstance();
		this.pi4j = Pi4J.newContextBuilder()
				.noAutoDetect()
				.add(	PiGpioSpiProvider.newInstance(pigpio),
						PiGpioDigitalInputProvider.newInstance(pigpio),
						PiGpioDigitalOutputProvider.newInstance(pigpio)
				)
				.build();
		
		this.matrix = new LedMatrixController(pi4j);
		
		//Erstelle Config für Output GPIOs
		DigitalOutputConfigBuilder outputConfig = DigitalOutput.newConfigBuilder(pi4j)
		        .shutdown(DigitalState.LOW)
		        .initial(DigitalState.LOW)
		        .provider("pigpio-digital-output");
		
		//Erstelle Config für Input GPIOs
		DigitalInputConfigBuilder inputConfig = DigitalInput.newConfigBuilder(pi4j)
				.debounce(3000L)
				.pull(PullResistance.PULL_DOWN)		//Trotz externer Pull_downs ist das aktivieren der internen Pull_downs zum Vermeiden von Fehlern wichtig
		        .provider("pigpio-digital-input");
		
		//Erstelle Config für ConfirmMove GPIO
		DigitalInputConfigBuilder buttonConfig = DigitalInput.newConfigBuilder(pi4j)
				.name("Confirm-Move Button")
				.id("button")
				.address(BUTTON_PIN)
				.debounce(3000L)
				.pull(PullResistance.PULL_UP)
		        .provider("pigpio-digital-input");
		
		button = pi4j.create(buttonConfig);
		
		int i = 1;
		
		//Config anwenden und Ausgangs GPIOS registrieren
		for(int id : OUTPUT_PINS) {
			outputs.add(pi4j.create(outputConfig.id("column" + i).name("Column " + i).address(id)));
			i++;
		}
		
		i = 1;
		
		//Config anwenden und Eingangs GPIOS registrieren
		for(int id : INPUT_PINS) {
			inputs.add(pi4j.create(inputConfig.id("row" + i).name("Row " + i).address(id)));
			i++;
		}
		
		//Listener auf GPIO Input registrieren, welcher die HardwareListener benachrichtigt, wenn der Zustand des Pins auf LOW ist
		button.addListener(pin -> {
			if(pin.state() == DigitalState.LOW)
				for(HardwareListener lis : listener) {
					lis.onConfirmButtonPressed(this);
				}
		});
		
		//LED Matrix aktivieren
		matrix.setEnabled(true);
		matrix.clear();
		matrix.refresh();
	}
	
	/**
	 * Gibt die Singleton Instanz dieses Controllers zurück
	 * @return Instanz dieses Controllers
	 */
	public static HardwareController getInstance() {
		if(INSTANCE == null)
			return new HardwareController();
		
		return INSTANCE;
	}
	
	public LedMatrixController getLedMatrix() {
		return matrix;
	}
	
	public boolean isShutdown() {
		return !enabled;
	}
	
	/**
	 * Registriert einen Listener im Controller. Der Listener wird benachrichtigt, sobald der Confirm-Move Knopf gedrückt wurde
	 * Wenn der übergebene Listener null ist, wird nichts hinzugefügt.
	 * @param listener - Objekt vom Typ {@link HardwareListener} @Nullable
	 */
	public void addListener(HardwareListener listener) {
		if(listener == null)
			return;
		
		this.listener.add(listener);
	}
	
	/**
	 * Diese Methode sollte vor Beenden des Programms aufgerufen werden um den HardwareController und alle zugehörigen GPIOS ordnungsgemäß
	 * zu beenden.
	 */
	public void shutdown() {
		clearLeds();
		getLedMatrix().setEnabled(false);
		pi4j.shutdown();
		enabled = false;
	}
	
	/**
	 * Schneller Weg die LED Matrix zu leeren.
	 * Der interne Buffer wird gelöscht und die Matrix aktualisiert.
	 */
	public void clearLeds() {
		getLedMatrix().clear();
		getLedMatrix().refresh();
	}
	
	/**
	 * Quick Access für LED Manipulation, für breite Auswahl von LED Funktionen, verwende den {@link LedMatrixController} mittels {@link #getLedMatrix()}
	 * @param x - X Position der LED (0-7)
	 * @param y - Y Position der LED (0-7)
	 * @param state - Zustand der LED (true = ON)
	 */
	public void setLed(int x, int y, boolean state) {
		getLedMatrix().setPixel(x, y, state);
		getLedMatrix().refresh();
	}
	
	/**
	 * Lässt eine LED schnell blinken (Anwendung z.B: bei Fehlerhaften Positionen)
	 * @param x - X Position der LED (0-7)
	 * @param y - Y Position der LED (0-7)
	 * @param amount - Wie oft geblinkt werden soll
	 */
	public void blinkFast(int x, int y, int amount) {
		byte[] originalbuffer = getLedMatrix().getBuffer().clone();	//Nach blinken kann der vorherige Zustand des Bretts verändert sein
		
		for(; amount > 0; amount --) {
			setLed(x, y, true);
			sleep(100);
			setLed(x, y, false);
			sleep(100);
		}
		
		getLedMatrix().overwriteBuffer(originalbuffer);				//Daher wird der vorherige Zustand manuell wiederhergestellt
		getLedMatrix().refresh();
	}
	
	public void setColumn(int x, boolean state) {
		for(int i = 0; i < LedMatrixController.WIDTH; i++)
			getLedMatrix().setPixel(x, i, state);
		
		getLedMatrix().refresh();
	}
	
	public void setRow(int y, boolean state) {
		for(int i = 0; i < LedMatrixController.HEIGHT; i++)
			getLedMatrix().setPixel(i, y, state);
		
		getLedMatrix().refresh();
	}
	
	/**
	 * Lässt eine Spur (blinkende Linie) von einem Startfeld zu einem Zielfeld aufblinken
	 * @param startX - X Position vom Startfeld (0-7)
	 * @param startY - Y Position vom Startfeld (0-7)
	 * @param destX - X Position vom Zielfeld (0-7)
	 * @param destY - Y Position vom Zielfeld (0-7)
	 * @param amount - Wie oft diese Spur aufblinken soll
	 */
	public void blinkTrace(int startX, int startY, int destX, int destY, int amount) {
		byte[] originalbuffer = getLedMatrix().getBuffer().clone();
		float steps;
		float dx = destX - startX;
		float dy = destY - startY;
		
		if(Math.abs(dx) > Math.abs(dy))
			steps = Math.abs(dx);
		else
			steps = Math.abs(dy);
		
		dx = dx/steps;
		dy = dy/steps;
		
		for(; amount > 0; amount--) {
			float x = startX;
			float y = startY;
			
			for (int i = 0 ; i <= steps; i++) {
				setLed(Math.round(x), Math.round(y), true);
				sleep(200);
				setLed(Math.round(x), Math.round(y), false);
				x += dx;
				y += dy;
			}
		}
		
		getLedMatrix().overwriteBuffer(originalbuffer);
		getLedMatrix().refresh();
	}
	
	private void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch(InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	@Deprecated
	private double lerp(float a, float b, float f)
	{
	    return a * (1.0 - f) + (b * f);
	}
	
	/**
	 * Liest den aktuellen Zustand des Felds ein und gibt ihn als zweidimensionales Boolean-Array zurück.
	 * Die Ausführung dieser Funktion kann einen Moment dauern, da alle Spielfeldspalten nacheinander abgefragt werden.
	 * Eine Verzögerung von ca. 1,5 Sekunden ist zu erwarten
	 * @NotNull
	 * @return Zustand des Spielfelds als 2D Boolean Array
	 */
	public boolean[][] readField(){
		boolean[][] matrix = new boolean[FIELD_SIZE][FIELD_SIZE];
		
		if(pi4j.isShutdown())
			return matrix;
		
	    for(int col = 0; col < FIELD_SIZE; col++)
	        readColumn(col, matrix);
	    return matrix;
	}
	
	/**
	 * Liest eine einzelne spezifizierte Spalte ein
	 * @param col - Spaltenindex
	 * @param matrix - 2D Boolean Matrix wo die eingelesenen Werte eingetragen werden.
	 */
	private void readColumn(int col, boolean[][] matrix){
		outputs.get(col).high();
		
		try {
			Thread.sleep(READ_SLEEP_MILLIS);
		}
		catch(InterruptedException e) {
			e.printStackTrace();
		}
		
	    for(int row = 0; row < FIELD_SIZE; row++)
	        matrix[col][row] = inputs.get(row).state().isHigh();
	    
	    try {
			Thread.sleep(READ_SLEEP_MILLIS);
		}
		catch(InterruptedException e) {
			e.printStackTrace();
		}
	    
	    outputs.get(col).low();
	}
}
