package org.jenkinsci.plugins.multiplescms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.scm.SCM;
import java.util.HashSet;
import java.util.Set;

public class MultiSCMChangeLogSet extends ChangeLogSet<MultiSCMChangeLogEntry> {

	private final HashMap<SCM, ChangeLogSetWrapper> changes;
    private final Set<String> kinds;
	
	protected MultiSCMChangeLogSet(AbstractBuild<?, ?> build) {
		super(build);
		changes = new HashMap<SCM, ChangeLogSetWrapper>();
        kinds = new HashSet<String>();
	}

	public static class ChangeLogSetWrapper {
		private AbstractBuild build;
		private List<Entry> logs;
        private List<MultiSCMChangeLogEntry> multiLogs;
		private Class clazz;
		private String friendlyName;
		
		public ChangeLogSetWrapper(AbstractBuild build, String friendlyName, Class handler) {
			this.build = build;
			this.logs = new ArrayList<Entry>();
            this.multiLogs = new ArrayList<MultiSCMChangeLogEntry>();
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

        public List<MultiSCMChangeLogEntry> getMultiLogs() {
            return multiLogs;
        }

		public void addChanges(MultiSCMChangeLogEntry multiLog) {
            logs.add(multiLog.getDelegate());
            multiLogs.add(multiLog);
		}
	}
		
	private class MultiSCMChangeLogSetIterator implements Iterator<MultiSCMChangeLogEntry> {

		MultiSCMChangeLogSet set;
		Iterator<SCM> scmIter = null;
		SCM currentScm = null;
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

		public MultiSCMChangeLogEntry next() {
			if(logIter == null || !logIter.hasNext()) {
				currentScm = scmIter.next();
				logIter = set.changes.get(currentScm).logs.iterator();
			}			
			return new MultiSCMChangeLogEntry(currentScm, logIter.next(), build, set);
		}

		public void remove() {
			throw new UnsupportedOperationException("Cannot remove changeset items");
		}		
	}
	
	public Iterator<MultiSCMChangeLogEntry> iterator() {
		return new MultiSCMChangeLogSetIterator(this);
	}

	@Override
	public boolean isEmptySet() {
		return changes.isEmpty();
	}
	
	public void add(SCM scm, ChangeLogSet<? extends Entry> cls) {
		if(!cls.isEmptySet()) {
			ChangeLogSetWrapper wrapper = changes.get(scm);
			if(wrapper == null) {
                String friendlyName = MultiSCMRevisionState.keyFor(scm, build.getWorkspace(), build).replaceFirst("^\\Q" + scm.getType() + "\\E", scm.getDescriptor().getDisplayName());
				wrapper = new ChangeLogSetWrapper(build, friendlyName, cls.getClass());
				changes.put(scm, wrapper);
			}
            for (ChangeLogSet.Entry e : cls) {
                wrapper.addChanges(new MultiSCMChangeLogEntry(scm, e, build, this));
            }
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
