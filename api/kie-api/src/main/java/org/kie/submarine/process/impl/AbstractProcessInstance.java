package org.kie.submarine.process.impl;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.ProcessRuntime;
import org.kie.submarine.process.Process;
import org.kie.submarine.process.ProcessInstance;
import org.kie.submarine.rules.impl.ListDataSource;

public abstract class AbstractProcessInstance<T> implements ProcessInstance<T> {

    private final T variables;
    private final AbstractProcess<T> process;
    private final ProcessRuntime rt;
    private org.kie.api.runtime.process.ProcessInstance legacyProcessInstance;

    public AbstractProcessInstance(AbstractProcess<T> process, T variables, ProcessRuntime rt) {
        this.process = process;
        this.rt = rt;
        this.variables = variables;
    }

    public void start() {
        Map<String, Object> map = bind(variables);
        String id = process.legacyProcess().getId();
        this.legacyProcessInstance =
                rt.createProcessInstance(id, map);
        process.instances().update(
                legacyProcessInstance.getId(), this);
        this.rt.startProcess(id);
        unbind(variables, map);
    }

    public void abort() {
        if (legacyProcessInstance == null) return;
        long pid = legacyProcessInstance.getId();
        process.instances().remove(pid);
        this.rt.abortProcessInstance(pid);
    }

    @Override
    public Process<T> process() {
        return process;
    }

    @Override
    public T variables() {
        return variables;
    }

    // this must be overridden at compile time
    private Map<String, Object> bind(T variables) {
        HashMap<String, Object> vmap = new HashMap<>();
        try {
            for (Field f : variables.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object v = null;
                v = f.get(variables);
                vmap.put(f.getName(), v);
            }
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
        vmap.put("$v", variables);
        return vmap;
    }

    private Map<String, Object> unbind(T variables, Map<String, Object> vmap) {
        try {
            for (Field f : variables.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                f.set(variables, vmap.get(f.getName()));
            }
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
        vmap.put("$v", variables);
        return vmap;
    }
}
