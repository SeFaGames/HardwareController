package de.fabiansiemens.hardwarecontroller;

import java.util.LinkedList;
import java.util.List;

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
	private static final int READ_SLEEP_MILLIS = 100;
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
	
	/**
	 * Erzeugt eine neue Instanz und konfiguriert die GPIOS
	 */
	private HardwareController() {
		INSTANCE = this;
	
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
		pi4j.shutdown();
	}
	
	/**
	 * Quick Access für LED Manipulation, für breite Auswahl von LED Funktionen, verwende den {@link LedMatrixController} mittels {@link #getLedMatrix()}
	 * @param x - X Position der LED
	 * @param y - Y Position der LED
	 * @param state - Zustand der LED (true = ON)
	 */
	public void setLed(int x, int y, boolean state) {
		getLedMatrix().setPixel(x, y, state);
		getLedMatrix().refresh();
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
