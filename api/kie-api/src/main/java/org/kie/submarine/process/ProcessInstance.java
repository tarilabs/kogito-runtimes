package org.kie.submarine.process;

public interface ProcessInstance<T> {
    Process<T> process();
    void start();
}
