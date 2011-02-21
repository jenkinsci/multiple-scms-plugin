package org.jenkinsci.plugins;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import hudson.model.AbstractBuild;
import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;

import org.jenkinsci.plugins.MultiSCMChangeLogSet.MultiSCMChangeLog;

public class MultiSCMChangeLogSet extends ChangeLogSet<MultiSCMChangeLog> {

	private final List<MultiSCMChangeLog> changes;
	
	protected MultiSCMChangeLogSet(AbstractBuild<?, ?> build) {
		super(build);
		changes = new ArrayList<MultiSCMChangeLog>();
	}

	public Iterator<MultiSCMChangeLog> iterator() {
		return changes.iterator();
	}

	@Override
	public boolean isEmptySet() {
		return changes.isEmpty();
	}
	
	public static class MultiSCMChangeLog extends ChangeLogSet.Entry {
		
		ChangeLogSet.Entry contained;
		
		public MultiSCMChangeLog(ChangeLogSet.Entry containedEntry) {
			contained = containedEntry;
		}

		@Override
		public String getMsg() {
			return contained.getMsg();
		}

		@Override
		public User getAuthor() {
			return contained.getAuthor();
		}

		@Override
		public Collection<String> getAffectedPaths() {
			return contained.getAffectedPaths();
		}
		
	}

	public void add(String scmClass, ChangeLogSet<? extends Entry> cls) {
		for(ChangeLogSet.Entry e : cls)
		changes.add(new MultiSCMChangeLog(e));
	}
}
