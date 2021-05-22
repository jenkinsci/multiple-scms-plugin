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

import java.util.ArrayList;
import java.util.List;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;

/**
 * @author weber
 *
 */
class MultiSCMHeadObserver extends SCMHeadObserver {

    private final List<Collector> result;
    private Collector currentObserver;
    private SCMSource currentSource;

    /**
     * 
     */
    public MultiSCMHeadObserver(int numSources) {
	result = new ArrayList<SCMHeadObserver.Collector>(numSources);
    }

    /**
     * Notified before a new SCM source will be observed. Must be invoked prior
     * to {@link #observe}.
     * 
     * @param newSource
     */
    public void beginObserving(SCMSource newSource) {
	if (newSource != currentSource) {
	    currentObserver = new SCMHeadObserver.Collector();
	    currentSource = newSource;
	}
    }

    /**
     * Notified after a SCM source has been successfully observed. Must be
     * invoked after to {@link #observe}. May be not invoked, if an error
     * occurred in the calling code.
     */
    public void endObserving() {
	result.add(currentObserver);
	currentObserver = null;
	currentSource = null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see jenkins.scm.api.SCMHeadObserver#observe(jenkins.scm.api.SCMHead,
     * jenkins.scm.api.SCMRevision)
     */
    @Override
    public void observe(SCMHead head, SCMRevision revision) {
	currentObserver.observe(head, revision);
    }

    /**
     * Returns the collected results. For each invocation sequence of
     * {@link #beginObserving(SCMSource)}/{@link #endObserving()}, the returned
     * list will contain a corresponding {@code Collector} object.
     * 
     *
     * @return the collected results.
     */
    @NonNull
    public List<Collector> result() {
	return result;
    }
}
