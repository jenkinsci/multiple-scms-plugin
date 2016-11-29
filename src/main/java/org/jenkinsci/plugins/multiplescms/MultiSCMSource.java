/*
 * The MIT License
 *
 * Copyright (c) 2016 Martin Weber
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMHeadObserver.Collector;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceDescriptor;

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
    protected void retrieve(SCMHeadObserver observer, TaskListener listener) throws IOException, InterruptedException {
	final MultiSCMHeadObserver multiObserver = new MultiSCMHeadObserver(scmSources.size());

	listener.getLogger().println("Collecting branches that exist in each SCM...");
	// process all SCMs, but try to not delete existing sub-projects...
	boolean retrieveOk = true;
	for (SCMSource scmSource : scmSources) {
	    multiObserver.beginObserving(scmSource);
	    Method method = getDescriptor().getRetrieveMethod(scmSource.getDescriptor());
	    /*
	     * invoke scmSource.retrieve(observer, listener). Unfortunately,
	     * that method is not accessible to us, so we use reflection
	     */
	    try {
		if (!method.isAccessible()) {
		    method.setAccessible(true);
		}
		listener.getLogger().print("* ");
		method.invoke(scmSource, multiObserver, listener);
		multiObserver.endObserving();
	    } catch (InvocationTargetException ex) {
		listener.error(ex.getCause().getMessage());
		retrieveOk = false;
	    } catch (IllegalAccessException ex) {
		// Should not happen, we set it accessible above
		ex.printStackTrace();
		retrieveOk = false;
	    } catch (IllegalArgumentException ex) {
		ex.printStackTrace();
		retrieveOk = false;
	    }
	}

	if (!retrieveOk) {
	    listener.error("Failed to fetch one or more SCMs. Builds may fail.");
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
			(SCMRevision[]) bRevs.toArray(new SCMRevision[bRevs.size()]));
		observer.observe(branch, rev);
	    }
	}
	listener.getLogger().println("Done collecting branches.");
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
    public static class DescriptorImpl extends SCMSourceDescriptor {
	private static Logger logger = Logger.getLogger(MultiSCMSource.class.getName());

	private Map<SCMSourceDescriptor, Method> applicableSCMs;

	@Override
	public String getDisplayName() {
	    return "Multiple SCMs";
	}

	/**
	 * Get the {@link SCMSourceDescriptor}s that are appropriate for a
	 * MultiSCMSource.
	 */
	public Set<SCMSourceDescriptor> getScmSourceDescriptors() {
	    if (applicableSCMs == null) {
		applicableSCMs = calcApplicableSCMs();
	    }
	    return applicableSCMs.keySet();
	}

	private Method getRetrieveMethod(Descriptor<SCMSource> descriptor) {
	    // init method map..
	    getScmSourceDescriptors();
	    return applicableSCMs.get(descriptor);
	}

	private Map<SCMSourceDescriptor, Method> calcApplicableSCMs() {
	    Map<SCMSourceDescriptor, Method> result = new HashMap<SCMSourceDescriptor, Method>(4);
	    Jenkins j = Jenkins.getInstance();
	    if (j != null) {
		for (Descriptor<SCMSource> d : j.getDescriptorList(SCMSource.class)) {
		    // Filter MultiSCM itself from the list of choices.
		    if (!(d instanceof MultiSCMSource.DescriptorImpl)) {
			final SCMSourceDescriptor descr = (SCMSourceDescriptor) d;
			Method retrieveMethod = determineRetrieveMethod(descr.clazz);
			if (retrieveMethod == null) {
			    // no matching method found in class hierarchy
			    final String msg = String.format(
				    "Ignoring SCMSource `%s` since no matching `retrieve` method could be found in class hierarchy.",
				    descr.clazz.getName());
			    logger.warning(msg);
			} else {
			    result.put(descr, retrieveMethod);
			}
		    }
		}
	    }
	    return result;
	}

	/**
	 * Searches for the protected
	 * {@link SCMSource#retrieve(SCMHeadObserver observer, TaskListener listener)}
	 * method of the specified class. Unfortunately for our purpose, that
	 * retrieve-method is not public.
	 * 
	 * @return the Method object or <code>null</code> if none could be found
	 *         in the class hierarchy
	 */
	private static Method determineRetrieveMethod(Class<?> clazz) {
	    if (clazz != null && SCMSource.class.isAssignableFrom(clazz)) {
		try {
		    return clazz.getDeclaredMethod("retrieve",
			    new Class[] { SCMHeadObserver.class, TaskListener.class });
		} catch (NoSuchMethodException ex) {
		    // try super class
		    return determineRetrieveMethod(clazz.getSuperclass());
		}
	    }
	    return null; // no method found in class hierarchy
	}
    } // DescriptorImpl

}
