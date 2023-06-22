package de.fabiansiemens.hardwarecontroller.led;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.pi4j.context.Context;
import com.pi4j.io.spi.Spi;
import com.pi4j.io.spi.SpiConfig;

import de.fabiansiemens.hardwarecontroller.led.print.Direction;
import de.fabiansiemens.hardwarecontroller.led.print.Symbol;

/**
 * Implementatierung der LED Matrix.
 * Adaptiert aus folgender Quelle: {@link https://github.com/Pi4J/pi4j-example-crowpi/blob/main/src/main/java/com/pi4j/crowpi/components/events/SimpleEventHandler.java}
 */
public class LedMatrixController extends MAX7219 {
    /**
     * Default SPI channel for the LED matrix on the CrowPi
     */
    protected static final int DEFAULT_CHANNEL = 0;	//0 oder 1
    /**
     * Default SPI baud rate for the LED matrix on the CrowPi
     */
    protected static final int DEFAULT_BAUD_RATE = 8000000;
    /**
     * Default delay between scroll operations in milliseconds
     */
    protected static final long DEFAULT_SCROLL_DELAY = 50;
    /**
     * Default direction for scroll operations
     */
    protected static final Direction DEFAULT_SCROLL_DIRECTION = Direction.LEFT;

    /**
     * Creates a new LED matrix component with the default channel and baud rate.
     *
     * @param pi4j Pi4J context
     */
    public LedMatrixController(Context pi4j) {
        this(pi4j, DEFAULT_CHANNEL, DEFAULT_BAUD_RATE);
    }

    /**
     * Creates a new LED matrix component with a custom channel and baud rate.
     *
     * @param pi4j    Pi4J context
     * @param channel SPI channel
     * @param baud    SPI baud rate
     */
    public LedMatrixController(Context pi4j, int channel, int baud) {
        super(pi4j.create(buildSpiConfig(pi4j, channel, baud)));
    }

    /**
     * Scrolls the display towards the given direction and leaves the now empty row/column empty.
     *
     * @param direction Desired scroll direction
     */
    public void scroll(Direction direction) {
        scroll(direction, ScrollMode.NORMAL, null, 0);
    }

    /**
     * Rotates the display towards the given direction and wraps around the affected row/column.
     * E.g. if {@link Direction#LEFT} is used, the column which falls out on the left will be the new rightmost column.
     *
     * @param direction Desired scroll direction
     */
    public void rotate(Direction direction) {
        scroll(direction, ScrollMode.ROTATE, null, 0);
    }

    /**
     * Scrolls the display towards the given direction and fills the empty row/column based on scroll mode.
     * This helper method calls the appropriate internal functions and MUST not be exposed as it contains internal logic.
     * The scrolling operating will be immediately visible on the display.
     *
     * @param direction  Desired scroll direction
     * @param scrollMode Desired scroll mode
     * @param newBuffer  Only if {@link ScrollMode#REPLACE}: New buffer for replacement values
     * @param newOffset  Only if {@link ScrollMode#REPLACE}: Desired row/column offset for new buffer
     */
    protected void scroll(Direction direction, ScrollMode scrollMode, byte[] newBuffer, int newOffset) {
        // Call internal scroll function based on direction
        // This has been split up into separate methods to keep this method tidy
        switch (direction) {
            case UP:
                scrollUp(scrollMode, newBuffer, newOffset);
                break;
            case DOWN:
                scrollDown(scrollMode, newBuffer, newOffset);
                break;
            case LEFT:
                scrollLeft(scrollMode, newBuffer, newOffset);
                break;
            case RIGHT:
                scrollRight(scrollMode, newBuffer, newOffset);
                break;
        }

        // Immediately draw the updated buffer to the LED matrix
        refresh();
    }

    /**
     * Scrolls the display upwards and fills the now empty row based on scroll mode.
     * This works by copying the buffer array with an offset using {@link System#arraycopy(Object, int, Object, int, int)}.
     *
     * @param scrollMode Desired scroll mode
     * @param newBuffer  Only if {@link ScrollMode#REPLACE}: New buffer for replacement values
     * @param newOffset  Only if {@link ScrollMode#REPLACE}: Desired row offset for new buffer
     */
    @SuppressWarnings("SuspiciousSystemArraycopy")
    private void scrollUp(ScrollMode scrollMode, byte[] newBuffer, int newOffset) {
        // Preserve first row and scroll buffer upwards
        final var firstRow = buffer[0];
        System.arraycopy(buffer, 1, buffer, 0, HEIGHT - 1);

        // Determine target value for last row based on scroll mode
        final byte lastRow;
        if (scrollMode == ScrollMode.ROTATE) {
            lastRow = firstRow;
        } else if (scrollMode == ScrollMode.REPLACE) {
            lastRow = newBuffer[newOffset];
        } else {
            lastRow = 0;
        }

        // Set last row to determined value
        buffer[HEIGHT - 1] = lastRow;
    }

    /**
     * Scrolls the display downwards and fills the now empty row based on scroll mode.
     * This works by copying the buffer array with an offset using {@link System#arraycopy(Object, int, Object, int, int)}.
     *
     * @param scrollMode Desired scroll mode
     * @param newBuffer  Only if {@link ScrollMode#REPLACE}: New buffer for replacement values
     * @param newOffset  Only if {@link ScrollMode#REPLACE}: Desired row offset for new buffer
     */
    @SuppressWarnings("SuspiciousSystemArraycopy")
    private void scrollDown(ScrollMode scrollMode, byte[] newBuffer, int newOffset) {
        // Preserve last row and scroll buffer downwards
        final var lastRow = buffer[HEIGHT - 1];
        System.arraycopy(buffer, 0, buffer, 1, HEIGHT - 1);

        // Determine target value for first row based on scroll mode
        final byte firstRow;
        if (scrollMode == ScrollMode.ROTATE) {
            firstRow = lastRow;
        } else if (scrollMode == ScrollMode.REPLACE) {
            firstRow = newBuffer[HEIGHT - 1 - newOffset];
        } else {
            firstRow = 0;
        }

        // Set first row to determined value
        buffer[0] = firstRow;
    }

    /**
     * Scrolls the display to the left and fills the now empty column based on scroll mode.
     * This works by shifting each row to the left and combining the new column value with a binary OR.
     *
     * @param scrollMode Desired scroll mode
     * @param newBuffer  Only if {@link ScrollMode#REPLACE}: New buffer for replacement values
     * @param newOffset  Only if {@link ScrollMode#REPLACE}: Desired column offset for new buffer
     */
    private void scrollLeft(ScrollMode scrollMode, byte[] newBuffer, int newOffset) {
        // Scroll each row individually to the left
        for (int row = 0; row < HEIGHT; row++) {
            // Convert byte to integer to ensure proper bit operations
            final int tmp = buffer[row] & 0xFF;

            if (scrollMode == ScrollMode.ROTATE) {
                // Shift left by one (therefore moving the columns) and wrap the value around
                buffer[row] = (byte) ((tmp << 1) | (tmp >>> 7));
            } else if (scrollMode == ScrollMode.REPLACE) {
                // Determine target value for last column based on new buffer
                final int lastColumn = getBitFromByte(newBuffer[row], WIDTH - 1 - newOffset);
                // Combine shifted row (without last column) with last column
                buffer[row] = (byte) ((tmp << 1) | lastColumn);
            } else {
                // Shift left by one, causing the last column to be empty
                buffer[row] = (byte) (tmp << 1);
            }
        }
    }

    /**
     * Scrolls the display to the right and fills the now empty column based on scroll mode.
     * This works by shifting each row to the right and combining the new column value with a binary OR.
     *
     * @param scrollMode Desired scroll mode
     * @param newBuffer  Only if {@link ScrollMode#REPLACE}: New buffer for replacement values
     * @param newOffset  Only if {@link ScrollMode#REPLACE}: Desired column offset for new buffer
     */
    private void scrollRight(ScrollMode scrollMode, byte[] newBuffer, int newOffset) {
        // Scroll each row individually to the right
        for (int row = 0; row < HEIGHT; row++) {
            // Convert byte to integer to ensure proper bit operations
            final int tmp = buffer[row] & 0xFF;

            if (scrollMode == ScrollMode.ROTATE) {
                // Shift right by one (therefore moving the columns) and wrap the value around
                buffer[row] = (byte) ((tmp >>> 1) | (tmp << 7));
            } else if (scrollMode == ScrollMode.REPLACE) {
                // Determine target value for first column based on new buffer
                final int firstColumn = getBitFromByte(newBuffer[row], newOffset) << 7;
                // Combine shifted row (without first column) with first column
                buffer[row] = (byte) ((tmp >>> 1) | firstColumn);
            } else {
                // Shift right by one, causing the first column to be empty
                buffer[row] = (byte) (tmp >>> 1);
            }
        }
    }

    /**
     * Prints the given string to the LED matrix by scrolling each character in from left to right with the default scroll delay.
     * This method is blocking until the string has been fully printed and will both start and end with an empty display.
     * <p>
     * A pattern in the format "{SYMBOL-NAME}" can be used to include a symbol with the given name in the string.
     * E.g. if "{HEART}" occurs within the string, it will be automatically replaced with the {@link Symbol#HEART} symbol.
     * If a pattern includes a symbol which could not be found, it is silently ignored and added as-is.
     *
     * @param string String to be displayed
     */
    public void print(String string) {
        print(string, DEFAULT_SCROLL_DIRECTION);
    }

    /**
     * Prints the given string to the LED matrix by scrolling each character in towards the given direction with the default scroll delay.
     * This method is blocking until the string has been fully printed and will both start and end with an empty display.
     * <p>
     * A pattern in the format "{SYMBOL-NAME}" can be used to include a symbol with the given name in the string.
     * E.g. if "{HEART}" occurs within the string, it will be automatically replaced with the {@link Symbol#HEART} symbol.
     * If a pattern includes a symbol which could not be found, it is silently ignored and added as-is.
     *
     * @param string          String to be displayed
     * @param scrollDirection Direction towards character should be scrolled in
     */
    public void print(String string, Direction scrollDirection) {
        print(string, scrollDirection, DEFAULT_SCROLL_DELAY);
    }

    /**
     * Prints the given string to the LED matrix by scrolling each character in towards the given direction with a custom scroll delay.
     * This method is blocking until the string has been fully printed and will both start and end with an empty display.
     * <p>
     * A pattern in the format "{SYMBOL-NAME}" can be used to include a symbol with the given name in the string.
     * E.g. if "{HEART}" occurs within the string, it will be automatically replaced with the {@link Symbol#HEART} symbol.
     * If a pattern includes a symbol which could not be found, it is silently ignored and added as-is.
     *
     * @param string          String to be displayed
     * @param scrollDirection Direction towards character should be scrolled in
     * @param scrollDelay     Delay in milliseconds between scroll operations
     */
    public void print(String string, Direction scrollDirection, long scrollDelay) {
        // Convert string to list of symbols
        final var symbols = convertToSymbols(string);

        // Immediately print a space to clear the current display
        print(Symbol.SPACE);

        // Display each symbol one after another by transitioning them into the display
        for (Symbol symbol : symbols) {
            transition(symbol, scrollDirection, scrollDelay);
        }

        // Transition to a space symbol to clear the current display at the end
        // Without this we would still see the last letter of the provided string
        transition(Symbol.SPACE, scrollDirection, scrollDelay);
    }

    /**
     * Converts a string into a list of symbols to print on the 8x8 LED matrix.
     * Any characters not supported by the symbol table will throw an {@link IllegalArgumentException}.
     * <p>
     * This method will also search for Symbol reference patterns in the provided string, which are represented as "{SYMBOL-NAME}".
     * If this pattern is found within the string, this method will try to lookup the symbol and if found add it instead of the pattern.
     * If no symbol with a given name is found, it gets silently ignored and added as-is to the list of output symbols.
     *
     * @param string String to parse and convert to symbols
     * @return List of symbols to print for representing the given string
     */
    protected List<Symbol> convertToSymbols(String string) {
        final List<Symbol> symbols = new ArrayList<>();

        // Initialize state for our loop
        final StringBuilder buffer = new StringBuilder();
        boolean referenceMode = false;

        // Loop over each character of the string and look for Symbol references
        for (int i = 0; i < string.length(); i++) {
            // Get the character at the current position within the string
            char c = string.charAt(i);

            if (c == '{') {
                // We encountered an opening curly brace, this might be the start of a Symbol reference
                // Enable reference mode and silently skip this character for now
                referenceMode = true;
            } else if (referenceMode && c == '}') {
                try {
                    // Attempt to find a symbol with the given name written between the curly braces
                    final var symbolName = buffer.toString().toUpperCase();
                    final var symbol = Symbol.valueOf(symbolName);
                    symbols.add(symbol);
                } catch (IllegalArgumentException e) {
                    // We have not found a symbol with this name, so add the buffer as-is to our list of symbols to output
                    // We also have to add the curly braces here, as they are NOT contained within the buffer
                    symbols.add(Symbol.BRACE_LEFT);
                    for (int j = 0; j < buffer.length(); j++) {
                        symbols.add(lookupSymbol(buffer.charAt(j)));
                    }
                    symbols.add(Symbol.BRACE_RIGHT);
                } finally {
                    // Clear the buffer and disable reference mode
                    buffer.delete(0, buffer.length());
                    referenceMode = false;
                }
            } else if (referenceMode) {
                // We are in reference mode but this is not a closing curly brace, so lets add the character to the buffer
                buffer.append(c);
            } else {
                // We are not in reference mode and therefore not currently processing any Symbol reference
                // Directly lookup the given character in the symbol table and add to list of symbols
                symbols.add(lookupSymbol(c));
            }
        }

        // If we are still in reference mode, add the opening curly brace and contents of the buffer as-is
        if (referenceMode) {
            symbols.add(Symbol.BRACE_LEFT);
            for (int i = 0; i < buffer.length(); i++) {
                symbols.add(lookupSymbol(buffer.charAt(i)));
            }
        }

        return symbols;
    }

    /**
     * Prints the given character on the LED matrix, which will be immediately displayed.
     * If no symbol associated with the given character can be found, an {@link IllegalArgumentException} will be thrown.
     *
     * @param c Character to display
     */
    public void print(char c) {
        print(lookupSymbol(c));
    }

    /**
     * Prints the given symbol on the LED matrix, which will be immediately displayed.
     *
     * @param symbol Symbol to display
     */
    public void print(Symbol symbol) {
        System.arraycopy(symbol.getRows(), 0, buffer, 0, HEIGHT);
        refresh();
    }

    /**
     * Transitions the current LED matrix display to the given symbol by gradually scrolling the symbol in.
     * This works by scrolling each column in one-by-one towards the default scroll direction with the default scroll delay.
     *
     * @param symbol New symbol to display
     */
    public void transition(Symbol symbol) {
        transition(symbol, DEFAULT_SCROLL_DIRECTION, DEFAULT_SCROLL_DELAY);
    }

    /**
     * Transitions the current LED matrix display to the given symbol by gradually scrolling the symbol in with the default scroll delay.
     * This works by scrolling each column in one-by-one towards the given scroll direction with the default scroll delay.
     *
     * @param symbol          New symbol to display
     * @param scrollDirection Desired scrolling direction, e.g. {@link Direction#LEFT} means the new symbol scrolls in from right towards left
     */
    public void transition(Symbol symbol, Direction scrollDirection) {
        transition(symbol, scrollDirection, DEFAULT_SCROLL_DELAY);
    }

    /**
     * Transitions the current LED matrix display to the given symbol by gradually scrolling the symbol in.
     * This works by scrolling each column in one-by-one towards the given scroll direction with the specified scroll delay.
     *
     * @param symbol          New symbol to display
     * @param scrollDirection Desired scrolling direction, e.g. {@link Direction#LEFT} means the new symbol scrolls in from right towards left
     * @param scrollDelay     Delay in milliseconds between each scrolled column
     */
    public void transition(Symbol symbol, Direction scrollDirection, long scrollDelay) {
        for (int i = 0; i < WIDTH; i++) {
            scroll(scrollDirection, ScrollMode.REPLACE, symbol.getRows(), i);
            sleep(scrollDelay);
        }
    }

    /**
     * Returns a {@link Symbol} which is associated with the given ASCII character.
     * Throws an {@link IllegalArgumentException} if no symbol associated with this character was found.
     *
     * @param c Character to lookup
     * @return Symbol associated to character
     */
    protected Symbol lookupSymbol(char c) {
        final var symbol = Symbol.getByChar(c);
        if (symbol == null) {
            throw new IllegalArgumentException("Character is not supported by LED matrix");
        }

        return symbol;
    }

    /**
     * Initializes a blank image with the same size as the LED matrix and calls the given consumer with a {@link Graphics2D} instance.
     * This allows to easily draw on the screen using regular drawing commands like {@link Graphics2D#drawLine(int, int, int, int)}.
     * The drawn image will be immediately displayed on the LED matrix.
     *
     * @param drawer Lambda function which draws on new image
     */
    public void draw(Consumer<Graphics2D> drawer) {
        // Create new 1-bit buffered image with same size as LED matrix
        final var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_BYTE_BINARY);
        final var graphics = image.createGraphics();

        // Call consumer and pass graphics context for drawing
        drawer.accept(graphics);

        // Draw image on LED matrix
        draw(image);
    }

    /**
     * Displays a specific area of the given {@link BufferedImage} on the LED matrix by enabling LEDs for non-black colors.
     * The area will start at the given X/Y position and has the same width and height as the LED matrix.
     * You MUST ensure that the full width/height is still within bounds or a {@link java.awt.image.RasterFormatException} will be thrown.
     * The drawn image will be immediately displayed on the LED matrix.
     *
     * @param image Image to partially display on the LED matrix
     * @param x     X coordinate where visible area should start
     * @param y     Y coordinate where visible area should start
     */
    public void draw(BufferedImage image, int x, int y) {
        draw(image.getSubimage(x, y, WIDTH, HEIGHT));
    }

    /**
     * Displays the given {@link BufferedImage} on the LED matrix by enabling LEDs for non-black colors.
     * The passed image MUST have the same size as the LED matrix and of type {@link BufferedImage#TYPE_BYTE_BINARY}.
     * Use the overloaded method {@link #draw(BufferedImage, int, int)} to only display a specific area of a bigger image.
     * The drawn image will be immediately displayed on the LED matrix.
     *
     * @param image Image to display on the LED matrix
     */
    public void draw(BufferedImage image) {
        // Ensure image has correct type
        if (image.getType() != BufferedImage.TYPE_BYTE_BINARY) {
            throw new IllegalArgumentException("Image must be of type BYTE_BINARY");
        }

        // Ensure image has correct size
        if (image.getWidth() != WIDTH || image.getHeight() != HEIGHT) {
            throw new IllegalArgumentException("Image must be exactly " + WIDTH + "x" + HEIGHT + " pixels");
        }

        // Copy image into buffer pixel-by-pixel by looping over Y and X coordinates
        // While retrieving the data buffer (which happens to have the same format) would be slightly quicker, it breaks with sub-images
        // As a slight optimization we first clear the buffer and then only call setPixel() when true
        clear();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (image.getRGB(x, y) != Color.BLACK.getRGB()) {
                    setPixel(x, y, true);
                }
            }
        }

        // Immediately draw the updated buffer to the LED matrix
        refresh();
    }

    /**
     * Helper method for extracting a single bit from a byte value.
     * The result will be returned as an integer to guarantee that further bit operations are handled correctly.
     *
     * @param value Byte value to read
     * @param bit   Bit which should be extracted in range 0-7
     * @return Extracted bit (0 or 1) as integer
     */
    private static int getBitFromByte(byte value, int bit) {
        return (((value & 0xFF) >> bit) & 0x1);
    }
    
    public byte[] getBuffer() {
    	return buffer;
    }
    
    public void overwriteBuffer(byte[] buffer) {
    	for(int i = 0; i < buffer.length && i < this.buffer.length; i++) {
    		this.buffer[i] = buffer[i];
    	}
    }

    /**
     * Returns the current SPI instance for the LED matrix.
     *
     * @return SPI instance
     */
    protected Spi getSpi() {
        return this.spi;
    }

    /**
     * Builds a new SPI instance for the LED matrix
     *
     * @param pi4j    Pi4J context
     * @param channel SPI channel
     * @param baud    SPI baud rate
     * @return SPI instance
     */
    private static SpiConfig buildSpiConfig(Context pi4j, int channel, int baud) {
        return Spi.newConfigBuilder(pi4j)
            .id("SPI" + channel)
            .name("LED Matrix")
            .address(channel)
            .baud(baud)
            .build();
    }

    /**
     * Specifies which mode should be used while scrolling the LED matrix.
     */
    protected enum ScrollMode {
        /**
         * Normally scroll the LED matrix in one direction, causing one row or column to be empty.
         */
        NORMAL,
        /**
         * Scroll the LED matrix in one direction and wrap the row or column around to the other side.
         */
        ROTATE,
        /**
         * Scroll the LED matrix in one direction and replace the now empty row or column with values from a new buffer.
         * This can be used to gradually transition from one buffer to another via scrolling.
         */
        REPLACE
    }
}