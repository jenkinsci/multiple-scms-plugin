package org.jenkinsci.plugins.multiplescms;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.scm.ChangeLogSet;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCM;

import java.io.IOException;
import java.net.URL;

public class MultiSCMRepositoryBrowser extends RepositoryBrowser<ChangeLogSet.Entry> {

	private static final long serialVersionUID = 1L;

	@Override public URL getChangeSetLink(ChangeLogSet.Entry changeSet) throws IOException {
        if (changeSet instanceof MultiSCMChangeLogEntry) { // from digest.jelly
            MultiSCMChangeLogEntry mcle = (MultiSCMChangeLogEntry) changeSet;
            return getChangeSetLink(mcle.getScm().getEffectiveBrowser(), mcle.getDelegate());
        } else { // from index.jelly
            SCM scm = MultiSCMChangeLogParser.scmBySet.get(changeSet.getParent());
            if (scm != null) {
                return getChangeSetLink(scm.getEffectiveBrowser(), changeSet);
            } else {
                return null;
            }
        }
	}

    @SuppressWarnings({"unchecked", "rawtypes"}) // pending Class<E> RepositoryBrowser.typeToken() this is unavoidable
    private static URL getChangeSetLink(RepositoryBrowser browser, ChangeLogSet.Entry changeSet) throws IOException {
        return browser.getChangeSetLink(changeSet);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "multi";
        }
    }

}
