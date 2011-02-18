package org.jenkinsci.plugins;

import java.util.Collection;
import java.util.Iterator;

import hudson.model.AbstractBuild;
import hudson.model.User;
import hudson.scm.ChangeLogSet;

import org.jenkinsci.plugins.MultiSCMChangeLogSet.MultiSCMChangeLog;

public class MultiSCMChangeLogSet extends ChangeLogSet<MultiSCMChangeLog> {

	protected MultiSCMChangeLogSet(AbstractBuild<?, ?> build) {
		super(build);
		// TODO Auto-generated constructor stub
	}

	public Iterator<MultiSCMChangeLog> iterator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isEmptySet() {
		// TODO Auto-generated method stub
		return false;
	}

	
	public static class MultiSCMChangeLog extends ChangeLogSet.Entry {

		@Override
		public String getMsg() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public User getAuthor() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Collection<String> getAffectedPaths() {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
}
