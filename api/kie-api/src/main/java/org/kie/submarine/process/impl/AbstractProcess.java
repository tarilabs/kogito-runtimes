package org.kie.submarine.process.impl;

import org.kie.submarine.process.Process;

public abstract class AbstractProcess<T> implements Process<T> {

    private final MapProcessInstanceManager<T> instances = new MapProcessInstanceManager<>();

    @Override
    public MapProcessInstanceManager<T> instances() {
        return instances;
    }

    protected abstract org.kie.api.definition.process.Process legacyProcess();
}
