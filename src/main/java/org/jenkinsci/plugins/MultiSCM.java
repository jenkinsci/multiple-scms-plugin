package org.jenkinsci.plugins;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Saveable;
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
import hudson.util.DescribableList;
import hudson.util.FormValidation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.apache.commons.io.FileUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

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
public class MultiSCM extends SCM implements Saveable {

    private DescribableList<SCM,Descriptor<SCM>> scms =
        new DescribableList<SCM,Descriptor<SCM>>(this);
    
	@DataBoundConstructor
    public MultiSCM(List<SCM> scmList) throws IOException {
		scms.addAll(scmList);
    }
	
	@Exported
	public List<SCM> getConfiguredSCMs() {
		return scms.toList();
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

		FileOutputStream logStream = new FileOutputStream(changelogFile);
		OutputStreamWriter logWriter = new OutputStreamWriter(logStream);
		logWriter.write(String.format("<%s>\n", MultiSCMChangeLogParser.ROOT_XML_TAG));
		
		boolean checkoutOK = true;		
		for(SCM scm : scms) {
			String changeLogPath = changelogFile.getPath() + ".temp";
			File subChangeLog = new File(changeLogPath);
			checkoutOK = scm.checkout(build, launcher, workspace, listener, subChangeLog) && checkoutOK;
			
			String subLogText = FileUtils.readFileToString(subChangeLog);
			logWriter.write(String.format("<%s scm=\"%s\">\n<![CDATA[%s]]>\n</%s>\n",
					MultiSCMChangeLogParser.SUB_LOG_TAG,
					scm.getClass().getName(),
					subLogText,
					MultiSCMChangeLogParser.SUB_LOG_TAG));
			
			subChangeLog.delete();
		}
		logWriter.write(String.format("</%s>\n", MultiSCMChangeLogParser.ROOT_XML_TAG));
		logWriter.close();

		return checkoutOK;
	}

	@Override
	public ChangeLogParser createChangeLogParser() {
		return new MultiSCMChangeLogParser(scms.toList());
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

		public List<SCMDescriptor<?>> getApplicableSCMs(AbstractProject project) {
	    	List<SCMDescriptor<?>> scms = new ArrayList<SCMDescriptor<?>>();
	    		    	
	    	for(SCMDescriptor<?> scm : SCM._for(project)) {
	    		// Filter MultiSCM itself from the list of choices.
	    		// Theoretically it might work, but I see no practical reason to allow
	    		// nested MultiSCM configurations.
	    		if(!(scm instanceof DescriptorImpl) && !(scm instanceof NullSCM.DescriptorImpl))
	    			scms.add(scm);
	    	}
	    	
	    	return scms;
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

		@Override
		public SCM newInstance(StaplerRequest req, JSONObject formData)
				throws FormException {
			return super.newInstance(req, formData);
		}
	    
	}

	public void save() throws IOException {
		// TODO Auto-generated method stub
		
	}
}

