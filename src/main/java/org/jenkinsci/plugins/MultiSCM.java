package org.jenkinsci.plugins;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link MultiSCM} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)} method
 * will be invoked. 
 *
 * @author Kohsuke Kawaguchi
 */
public class MultiSCM extends SCM {

    public MultiSCM() {
    }

    @Override
	public SCMRevisionState calcRevisionsFromBuild(AbstractBuild<?, ?> build,
			Launcher launcher, TaskListener listener) throws IOException,
			InterruptedException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected PollingResult compareRemoteRevisionWith(
			AbstractProject<?, ?> project, Launcher launcher,
			FilePath workspace, TaskListener listener, SCMRevisionState baseline)
			throws IOException, InterruptedException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean checkout(AbstractBuild<?, ?> build, Launcher launcher,
			FilePath workspace, BuildListener listener, File changelogFile)
			throws IOException, InterruptedException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public ChangeLogParser createChangeLogParser() {
		// TODO Auto-generated method stub
		return null;
	}

	public List<SCMDescriptor<?>> getScms() {
		return new ArrayList<SCMDescriptor<?>>();
	}
    	
	/**
	 * Descriptor for {@link MultiSCM}. Used as a singleton.
	 * The class is marked as public so that it can be accessed from views.
	 *
	 * <p>
	 * See <tt>views/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
	 * for the actual HTML fragment for the configuration screen.
	 */
	@Extension // this marker indicates Hudson that this is an implementation of an extension point.
	public static final class DescriptorImpl extends SCMDescriptor<MultiSCM> {
		
	    public DescriptorImpl() {
			super(MultiSCMRepositoryBrowser.class);
			// TODO Auto-generated constructor stub
		}

		public List<SCMDescriptor<?>> getApplicableSCMs() {
	    	List<SCMDescriptor<?>> scms = new ArrayList<SCMDescriptor<?>>();
	    		    	
	    	for(SCMDescriptor<?> scm : SCM.all()) {
	    		// Filter MultiSCM itself from the list of choices.
	    		// Theoretically it might work, but I see no practical reason to allow
	    		// nested MultiSCM configurations.
	    		if(!(scm instanceof DescriptorImpl) && !(scm instanceof NullSCM.DescriptorImpl))
	    			scms.add(scm);
	    	}
	    	
	    	return scms;
	    }
		
	    /**
	     * Performs on-the-fly validation of the form field 'name'.
	     *
	     * @param value
	     *      This parameter receives the value that the user has typed.
	     * @return
	     *      Indicates the outcome of the validation. This is sent to the browser.
	     */
	    public FormValidation doCheckName(@QueryParameter String value) throws IOException, ServletException {
	        if(value.length()==0)
	            return FormValidation.error("Please set a name");
	        if(value.length()<4)
	            return FormValidation.warning("Isn't the name too short?");
	        return FormValidation.ok();
	    }
		
	    /**
	     * This human readable name is used in the configuration screen.
	     */
	    public String getDisplayName() {
	        return "Multiple SCMs";
	    }
	
	    @Override
	    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
	        // To persist global configuration information,
	        // set that to properties and call save().
	        // ^Can also use req.bindJSON(this, formData);
	        //  (easier when there are many fields; need set* methods for this, like setUseFrench)
	        save();
	        return super.configure(req,formData);
	    }	
	}
}

