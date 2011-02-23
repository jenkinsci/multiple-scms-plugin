package org.jenkinsci.plugins;

import hudson.scm.SCMRevisionState;

import java.util.HashMap;
import java.util.Map;

public class MultiSCMRevisionState extends SCMRevisionState {
	private final Map<String, SCMRevisionState> revisionStates;
	
	public MultiSCMRevisionState() {
		revisionStates = new HashMap<String, SCMRevisionState>(); 
	}

	public void add(String scmClass, SCMRevisionState scmState) {
		revisionStates.put(scmClass, scmState);
	}
	
	public SCMRevisionState get(String scmClass) {
		return revisionStates.get(scmClass);
	}
}
