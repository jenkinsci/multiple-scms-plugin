package org.jenkinsci.plugins;

import java.io.IOException;
import java.net.URL;

import org.jenkinsci.plugins.MultiSCMChangeLogSet.MultiSCMChangeLog;

import hudson.scm.RepositoryBrowser;

public class MultiSCMRepositoryBrowser extends RepositoryBrowser<MultiSCMChangeLog> {

	private static final long serialVersionUID = 1L;

	@Override
	public URL getChangeSetLink(MultiSCMChangeLog changeSet) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

}
