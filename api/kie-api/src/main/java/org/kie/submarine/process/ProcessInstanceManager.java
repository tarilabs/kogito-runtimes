package org.kie.submarine.process;

import java.util.Collection;
import java.util.Optional;

public interface ProcessInstanceManager<T> {

    Optional<? extends ProcessInstance<T>> findById(long i);

    Collection<? extends ProcessInstance<T>> values();
}
