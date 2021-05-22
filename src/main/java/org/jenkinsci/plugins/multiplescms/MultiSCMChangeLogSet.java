package org.jenkinsci.plugins.multiplescms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.scm.RepositoryBrowser;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import java.util.HashSet;
import java.util.Set;

public class MultiSCMChangeLogSet extends ChangeLogSet<Entry> {
    private final HashMap<String, ChangeLogSetWrapper> changes;
    private final Set<String> kinds;

    protected MultiSCMChangeLogSet(Run<?, ?> build, RepositoryBrowser<?> browser) {
        super(build, browser);
        changes = new HashMap<String, ChangeLogSetWrapper>();
        kinds = new HashSet<String>();
    }

    public static class ChangeLogSetWrapper extends ChangeLogSet<Entry> {
        private List<Entry> logs;
        private Class clazz;
        private String friendlyName;

        public ChangeLogSetWrapper(AbstractBuild build, String friendlyName, Class handler) {
            super(build);
            this.logs = new ArrayList<Entry>();
            this.clazz = handler;
            this.friendlyName = friendlyName;
        }

        public ChangeLogSetWrapper(AbstractBuild build, RepositoryBrowser<?> browser, String friendlyName, Class handler) {
            super(build, browser);
            this.logs = new ArrayList<Entry>();
            this.clazz = handler;
            this.friendlyName = friendlyName;
        }

        public AbstractBuild getBuild() {
            return build;
        }

        public Class getHandlerClass() {
            return clazz;
        }

        public String getName() {
            return friendlyName;
        }

        public List<Entry> getLogs() {
            return logs;
        }

        public void addChanges(ChangeLogSet<? extends Entry> cls) {
            for(Entry e : cls)
                logs.add(e);
        }

        public Iterator<Entry> iterator() {
            return logs.iterator();
        }

        @Override
        public boolean isEmptySet() {
            return logs.isEmpty();
        }
    }

    private static class MultiSCMChangeLogSetIterator implements Iterator<Entry> {

        MultiSCMChangeLogSet set;
        Iterator<String> scmIter = null;
        String currentScm = null;
        Iterator<Entry> logIter = null;

        public MultiSCMChangeLogSetIterator(MultiSCMChangeLogSet set) {
            this.set = set;
            scmIter = set.changes.keySet().iterator();
        }

        public boolean hasNext() {
            if(logIter == null || !logIter.hasNext())
                return scmIter.hasNext();
            return true;
        }

        public Entry next() {
            if(logIter == null || !logIter.hasNext()) {
                currentScm = scmIter.next();
                logIter = set.changes.get(currentScm).logs.iterator();
            }
            return logIter.next();
        }

        public void remove() {
            throw new UnsupportedOperationException("Cannot remove changeset items");
        }
    }

    public Iterator<Entry> iterator() {
        return new MultiSCMChangeLogSetIterator(this);
    }

    @Override
    public boolean isEmptySet() {
        return changes.isEmpty();
    }

    public void add(String scmClass, String scmFriendlyName, ChangeLogSet<? extends Entry> cls) {
        if(!cls.isEmptySet()) {
            ChangeLogSetWrapper wrapper = changes.get(scmClass);
            if(wrapper == null) {
                wrapper = new ChangeLogSetWrapper(build, cls.getBrowser(), scmFriendlyName, cls.getClass());
                changes.put(scmClass, wrapper);
            }
            wrapper.addChanges(cls);
        }
        kinds.add(cls.getKind());
    }

    public Collection<ChangeLogSetWrapper> getChangeLogSetWrappers() {
        return changes.values();
    }

    @Override public String getKind() {
        if (kinds.size() == 1) {
            return kinds.iterator().next();
        } else {
            return "Multi" + kinds;
        }
    }
}
