package org.drools.runtime.rule;

/**
 * A super-interface for all <code>StatefulRuleSession</code>s.
 * Although, users are encouraged to use <code>StatefulSession</code> interface instead of 
 * <code>WorkingMemory</code> interface, specially because of the <code>dispose()</code> method
 * that is only available in the <code>StatefulKnowledgeSession</code> interface.  
 * 
 * @see org.drools.runtime.StatefulKnowledgeSession 
 */
public interface StatefulRuleSession
    extends
    WorkingMemory {

    /**
     * Fire all Activations on the Agenda
     * @return
     *     returns the number of rules fired
     */
    int fireAllRules();

    /**
     * Fire Activations on the Agenda up to the given maximum number of activations, before returning
     * the control to the application.
     * In case the application wants to continue firing the rules later, from the point where it stopped,
     * it just needs to call <code>fireAllRules()</code> again.
     * 
     * @param max
     *     the maximum number of rules that should be fired
     * @return
     *     returns the number of rules fired
     */
    int fireAllRules(int max);

    /**
     * Fire all Activations on the Agenda
     * 
     * @param agendaFilter
     *      filters the activations that may fire
     * @return
     *      returns the number of rules fired
     */
    int fireAllRules(AgendaFilter agendaFilter);

    /**
     * Keeps firing activations until a halt is called. If in a given moment,
     * there is no activation to fire, it will wait for an activation to be
     * added to an active agenda group or rule flow group.
     * 
     * @throws IllegalStateException
     *             if this method is called when running in sequential mode
     */
    public void fireUntilHalt();

    /**
     * Keeps firing activations until a halt is called. If in a given moment,
     * there is no activation to fire, it will wait for an activation to be
     * added to an active agenda group or rule flow group.
     * 
     * @param agendaFilter
     *            filters the activations that may fire
     * 
     * @throws IllegalStateException
     *             if this method is called when running in sequential mode
     */
    public void fireUntilHalt(final AgendaFilter agendaFilter);
}
