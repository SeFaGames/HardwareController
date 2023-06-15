module com.pi4j.example {
    // Pi4J MODULES
    requires com.pi4j;
    requires com.pi4j.plugin.pigpio;

    // SLF4J MODULES
    requires org.slf4j;
    requires org.slf4j.simple;

    uses com.pi4j.extension.Extension;
    uses com.pi4j.provider.Provider;

    // allow access to classes in the following namespaces for Pi4J annotation processing
    opens de.fabiansiemens.hardwarecontroller to com.pi4j;
}
