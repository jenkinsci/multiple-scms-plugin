package org.jenkinsci.plugins.multiplescms;

import hudson.model.AbstractBuild;
import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.AffectedFile;
import hudson.scm.ChangeLogSet.Entry;
import hudson.scm.SCM;
import java.util.Collection;
import org.kohsuke.stapler.export.Exported;

public class MultiSCMChangeLogEntry extends ChangeLogSet.Entry {

    private final SCM scm;
    private final ChangeLogSet.Entry delegate;
    private final AbstractBuild<?,?> build;
    
    public MultiSCMChangeLogEntry(SCM scm, Entry delegate, AbstractBuild<?,?> build, MultiSCMChangeLogSet parent) {
        this.scm = scm;
        this.delegate = delegate;
        this.build = build;
        setParent(parent);
    }
    
    public SCM getScm() {
        return scm;
    }

    @Exported(name="scm")
    public String getScmKey() {
        return MultiSCMRevisionState.keyFor(scm, build.getWorkspace(), build);
    }
    
    public Entry getDelegate() {
        return delegate;
    }

    @Override public String getMsg() {
        return delegate.getMsg();
    }

    @Override public User getAuthor() {
        return delegate.getAuthor();
    }

    @Override public Collection<String> getAffectedPaths() {
        return delegate.getAffectedPaths();
    }

    @Override public Collection<? extends AffectedFile> getAffectedFiles() {
        return delegate.getAffectedFiles();
    }

    @Override public String getMsgAnnotated() {
        return delegate.getMsgAnnotated();
    }

    @Override public String getMsgEscaped() {
        return delegate.getMsgEscaped();
    }

}
