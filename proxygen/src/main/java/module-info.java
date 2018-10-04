module proxygen {
    requires freemarker;
    requires com.squareup.javapoet;
    requires java.compiler;
    requires java.logging;
    requires java.desktop;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.core;
    requires libmodule;

    opens com.gen to com.fasterxml.jackson.databind;


}