package org.kie.submarine.process;

import java.util.Collection;

public interface Process<T> {
    ProcessInstance<T> createInstance(T workingMemory);

    Collection<? extends ProcessInstance<T>> instances();
}
