package org.kie.submarine.process.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.Optional;

import org.kie.submarine.process.ProcessInstance;
import org.kie.submarine.process.ProcessInstanceManager;

class MapProcessInstanceManager<T> implements ProcessInstanceManager<T> {

    private final HashMap<Long, ProcessInstance<T>> instances = new HashMap<>();

    @Override
    public Optional<? extends ProcessInstance<T>> findById(long id) {
        return Optional.ofNullable(instances.get(id));
    }

    @Override
    public Collection<? extends ProcessInstance<T>> values() {
        return instances.values();
    }

    public Collection<? extends ProcessInstance<T>> all() {
        return instances.values();
    }

    void update(long id, ProcessInstance<T> instance) {
        instances.put(id, instance);
    }

    void remove(long id) {
        instances.remove(id);
    }
}
