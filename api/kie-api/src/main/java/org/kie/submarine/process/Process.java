package org.kie.submarine.process;

public interface Process<T> {
    ProcessInstance<T> createInstance(T workingMemory);

    ProcessInstanceManager<T> instances();
}
