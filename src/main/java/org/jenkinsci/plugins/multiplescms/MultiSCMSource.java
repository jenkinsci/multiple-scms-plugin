/*
 * The MIT License
 *
 * Copyright (c) 2016-2018 Martin Weber
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.multiplescms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMHeadObserver.Collector;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.impl.NullSCMSource;

/**
 * An {@code SCMSource} implementation that monitors and fetches multiple SCMs.
 *
 * @author Martin Weber
 */
public class MultiSCMSource extends SCMSource {

    private List<SCMSource> scmSources;

    @DataBoundConstructor
    public MultiSCMSource(@CheckForNull String id, List<SCMSource> scmSources) {
	super(id);
	this.scmSources = Util.fixNull(scmSources);
    }

    public List<SCMSource> getScmSources() {
	return scmSources;
    }

    // @DataBoundSetter
    public void setScmSources(List<SCMSource> scmSources) {
	if (scmSources != null) {
	    this.scmSources.addAll(scmSources);
	}
    }

    /*
     * (non-Javadoc)
     *
     * @see jenkins.scm.api.SCMSource#retrieve(jenkins.scm.api.SCMHeadObserver,
     * hudson.model.TaskListener)
     */
    @Override
    protected void retrieve(SCMSourceCriteria criteria, SCMHeadObserver observer, SCMHeadEvent<?> event,
        TaskListener listener) throws IOException, InterruptedException {
      final MultiSCMHeadObserver multiObserver = new MultiSCMHeadObserver(scmSources.size());

      // process all SCMs, but try to not delete existing sub-projects...
      for (SCMSource scmSource : scmSources) {
        multiObserver.beginObserving(scmSource);
        scmSource.fetch(criteria, multiObserver, event, listener);
        multiObserver.endObserving();
      }

      // retain only branches that exist in every source...
      Collector smallestByBranchCount = null;
      final List<Collector> multiSCMCollectors = multiObserver.result();
      {
          int smallestSize = Integer.MAX_VALUE;
          for (Collector entry : multiSCMCollectors) {
              int size = entry.result().size();
              if (size < smallestSize) {
                  smallestSize = size;
                  smallestByBranchCount = entry;
              }
          }
      }

      if (smallestByBranchCount != null) {
          listener.getLogger().println("Collecting branches...");
          // gather potential branches...
          // NOTE: assume SCMHead has by-branch-name equality
          final Set<SCMHead> branches = new HashSet<SCMHead>(smallestByBranchCount.result().keySet());
          // remove non-existing branches...
          for (Iterator<SCMHead> iter = branches.iterator(); iter.hasNext();) {
              final SCMHead branch = iter.next();
              for (Collector collector : multiSCMCollectors) {
                  if (!collector.result().containsKey(branch)) {
                      iter.remove();
                      break;
                  }
              }
          }
          if(branches.isEmpty()) {
            listener.getLogger().println("!!! None of the branches exists in EACH SCM.");
          } else {
            for (SCMHead branch : branches) {
              listener.getLogger().printf("*  Branch `%s` exists in each SCM.%n", branch.getName());
            }
          }
          listener.getLogger().println("Done collecting branches.");
          // feed remaining branches into observer...
          for (SCMHead branch : branches) {
              final List<SCMRevision> bRevs = new ArrayList<SCMRevision>(multiSCMCollectors.size());
              for (Collector res : multiSCMCollectors) {
                  final SCMRevision scmRevision = res.result().get(branch);
                  if (scmRevision != null) {
                      bRevs.add(scmRevision);
                  }
              }
              final MultiSCMRevision rev = new MultiSCMRevision(branch,
                      bRevs.toArray(new SCMRevision[bRevs.size()]));
              observer.observe(branch, rev);
          }
      }
    }

    /*
     * (non-Javadoc)
     *
     * @see jenkins.scm.api.SCMSource#build(jenkins.scm.api.SCMHead,
     * jenkins.scm.api.SCMRevision)
     */
    @Override
    public SCM build(SCMHead head, SCMRevision revision) {
	List<SCM> ret = new ArrayList<SCM>(scmSources.size());
	for (SCMSource scmSource : scmSources) {
	    ret.add(scmSource.build(head, revision));
	}
	try {
	    return new MultiSCM(ret);
	} catch (IOException neverThrown) {
	    throw new RuntimeException(neverThrown);
	}
    }

    /**
     * Overridden for better type safety.
     */
    @Override
    public DescriptorImpl getDescriptor() {
	return (DescriptorImpl) super.getDescriptor();
    }

    // //////////////////////////////////////////////////////////////////
    // inner classes
    // //////////////////////////////////////////////////////////////////
    /**
     * An {@code SCMRevision} implementation that hold multiple SCMRevisions.
     *
     * @author Martin Weber
     */
    private static class MultiSCMRevision extends SCMRevision {
	/**
	 *
	 */
	private static final long serialVersionUID = 1L;
	private final SCMRevision[] revisions;

	/**
	 */
	protected MultiSCMRevision(SCMHead head, SCMRevision[] revisions) {
	    super(head);
	    if (revisions == null) {
		throw new NullPointerException("revisions");
	    }
	    this.revisions = revisions;
	}

	@Override
	public boolean equals(Object o) {
	    if (this == o) {
		return true;
	    }
	    if (o == null || getClass() != o.getClass()) {
		return false;
	    }

	    MultiSCMRevision that = (MultiSCMRevision) o;
	    if (!getHead().equals(that.getHead())) {
		return false;
	    }
	    if (!Arrays.equals(revisions, that.revisions))
		return false;
	    return true;
	}

	@Override
	public int hashCode() {
	    final int prime = 31;
	    int result = 1;
	    result = prime * result + Arrays.hashCode(revisions);
	    return result;
	}

	@Override
	public String toString() {
	    StringBuilder b = new StringBuilder();
	    int iMax = revisions.length - 1;
	    for (int i = 0; i < revisions.length; i++) {
		b.append(revisions[i]);
		if (i == iMax)
		    break;
		b.append(":");
	    }
	    return b.toString();
	}
    } // MultiSCMRevision

    @Extension
    @Symbol(value = { "multipleSCMs" })
    public static class DescriptorImpl extends SCMSourceDescriptor {
	@Override
	public String getDisplayName() {
	    return "Multiple SCMs";
	}

    /**
     * Returns the {@link SCMSourceDescriptor} instances that are appropriate
     * for the current context.
     *
     * @return the {@link SCMDescriptor} instances
     */
    @SuppressWarnings("unused") // used by stapler binding
    public static List<SCMSourceDescriptor> getScmSourceDescriptors() {
      List<SCMSourceDescriptor> result = new ArrayList<SCMSourceDescriptor>(
          ExtensionList.lookup(SCMSourceDescriptor.class));
      for (Iterator<SCMSourceDescriptor> iterator = result.iterator(); iterator.hasNext();) {
        SCMSourceDescriptor d = iterator.next();
        if (NullSCMSource.class.equals(d.clazz) || MultiSCMSource.class.equals(d.clazz)) {
          iterator.remove();
        }
      }
      return result;
    }
  } // DescriptorImpl

}
