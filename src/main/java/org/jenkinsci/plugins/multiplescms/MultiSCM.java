package org.jenkinsci.plugins.multiplescms;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.Hudson;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.util.DescribableList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.sf.json.JSONArray;

import net.sf.json.JSONObject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

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

        MultiSCMRevisionState revisionStates = new MultiSCMRevisionState();

        for(SCM scm : scms) {
            SCMRevisionState scmState = scm.calcRevisionsFromBuild(build, launcher, listener);
            revisionStates.add(scm, build.getWorkspace(), build, scmState);
        }

        return revisionStates;
    }

    @Override
    public void buildEnvVars(AbstractBuild<?,?> build, Map<String, String> env) {
        // Add each SCM's env vars, appending indices where needed to avoid collisions
        for (int i = 0; i < scms.size(); i++) {
            try {
                EnvVars currScmVars = new EnvVars();
                scms.get(i).buildEnvVars(build, currScmVars);
                for (Entry<String, String> entry : currScmVars.entrySet()) {
                    if (env.containsKey(entry.getKey())) {
                        // We have a collision; append the index of this SCM to the env var name
                        env.put(entry.getKey() + "_" + i, entry.getValue());
                    } else {
                        // No collision; just put the var as usual
                        env.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            catch(NullPointerException npe)
            {}
            
        }
    }

    @Override
    protected PollingResult compareRemoteRevisionWith(
            AbstractProject<?, ?> project, Launcher launcher,
            FilePath workspace, TaskListener listener, SCMRevisionState baseline)
            throws IOException, InterruptedException {

        MultiSCMRevisionState baselineStates = baseline instanceof MultiSCMRevisionState ? (MultiSCMRevisionState) baseline : null;
        MultiSCMRevisionState currentStates = new MultiSCMRevisionState();

        Change overallChange = Change.NONE;

        for(SCM scm : scms) {
            SCMRevisionState scmBaseline = baselineStates != null ? baselineStates.get(scm, workspace, null) : null;
            if (scmBaseline instanceof MultiSCMRevisionState
                    && !(scm instanceof MultiSCM)) {
                continue;
            }
            PollingResult scmResult = scm.poll(project, launcher, workspace, listener, scmBaseline != null ? scmBaseline : SCMRevisionState.NONE);
            currentStates.add(scm, workspace, null, scmResult.remote);
            if(scmResult.change.compareTo(overallChange) > 0)
                overallChange = scmResult.change;
        }
        return new PollingResult(baselineStates, currentStates, overallChange);
    }

    @Override
    public void checkout(Run<?, ?> build, Launcher launcher,
            FilePath workspace, TaskListener listener, File changelogFile, SCMRevisionState baseline)
            throws IOException, InterruptedException {

       final MultiSCMRevisionState oldBaseline = baseline instanceof MultiSCMRevisionState ? (MultiSCMRevisionState) baseline : null;
        final MultiSCMRevisionState revisionState = new MultiSCMRevisionState();
        build.addAction(revisionState);
		
        final Run<?, ?> f_build = build;
        final Launcher f_launcher = launcher;
        final FilePath f_workspace = workspace;
        final TaskListener f_listener = listener;
        final File f_changelogFile = changelogFile;
        //final SCMRevisionState f_baseline = baseline;

        final HashSet<Object> scmActions = new HashSet<Object>();

        FileOutputStream logStream = new FileOutputStream(changelogFile);
        final OutputStreamWriter logWriter = new OutputStreamWriter(logStream);
        logWriter.write(String.format("<%s>\n", MultiSCMChangeLogParser.ROOT_XML_TAG));
		
        ArrayList<Thread> Threads = new ArrayList<Thread>();

        int i = 0;

        for(final SCM scm : scms) {
            if(i++ == scms.size() - 1){
                Threads.add(new Thread(new Runnable()  
                {
                    @Override  
                    public void run()  
                    {
                        File subChangeLog = f_changelogFile != null ? new File(f_changelogFile.getPath() + ".temp") : null;
                        SCMRevisionState workspaceRevision = null;
                        if (oldBaseline != null) {
                            workspaceRevision = oldBaseline.get(scm, f_workspace, f_build instanceof AbstractBuild ? (AbstractBuild) f_build : null);
                        }
                        try{
                            scm.checkout(f_build, f_launcher, f_workspace, f_listener, subChangeLog, workspaceRevision);
                        }
                        catch(Exception ex){}

                        List<Action> actions = f_build.getActions();
                        for(Action a : actions) {
                            if(!scmActions.contains(a) && a instanceof SCMRevisionState && !(a instanceof MultiSCMRevisionState)) {
                                try{
                                    scmActions.add(a);
                                }catch(Exception ex){}
                                try{
                                    revisionState.add(scm, f_workspace, f_build, (SCMRevisionState) a);
                                }catch(Exception ex){}
                            }
                        }
                        if (subChangeLog != null && subChangeLog.exists()) {
                            String subLogText = "";
                            try{
                                subLogText = FileUtils.readFileToString(subChangeLog);
                            }
                            catch(Exception ex){}
                            //Dont forget to escape the XML in case there is any CDATA sections
                            try{
                                logWriter.write(String.format("<%s scm=\"%s\">\n<![CDATA[%s]]>\n</%s>\n",MultiSCMChangeLogParser.SUB_LOG_TAG,scm.getKey(),StringEscapeUtils.escapeXml(subLogText),MultiSCMChangeLogParser.SUB_LOG_TAG));
                            }catch(Exception ex){}
                            subChangeLog.delete();
                        }
                    }
                }));
                Threads.get(i-1).start();
            }
            else{
                Threads.add(new Thread(new Runnable()
                {
                    @Override  
                    public void run()  
                    {
                        File subChangeLog = f_changelogFile != null ? new File(f_changelogFile.getPath() + ".temp") : null;
                        SCMRevisionState workspaceRevision = null;
                        if (oldBaseline != null) {
                            workspaceRevision = oldBaseline.get(scm, f_workspace, f_build instanceof AbstractBuild ? (AbstractBuild) f_build : null);
                        }
                        try{
                            scm.checkout(f_build, f_launcher, f_workspace, f_listener, subChangeLog, workspaceRevision);
                        }
                        catch(Exception ex){}

                        List<Action> actions = f_build.getActions();
                        for(Action a : actions) {
                            if(!scmActions.contains(a) && a instanceof SCMRevisionState && !(a instanceof MultiSCMRevisionState)) {
                                try{
                                    scmActions.add(a);
                                }catch(Exception ex){}
                                try{
                                    revisionState.add(scm, f_workspace, f_build, (SCMRevisionState) a);
                                }catch(Exception ex){}
                            }
                        }
                        if (subChangeLog != null && subChangeLog.exists()) {
                            subChangeLog.delete();
                        }
                    }
                }));
                Threads.get(i-1).start();
            }
			
        }
        boolean thread_finish = false;
        while(!thread_finish){
            for(Thread th: Threads){
                if(th.isAlive()){
                    thread_finish = false;
                    break;
                }
                thread_finish = true;
            }
            Thread.sleep(1000);
        }
        logWriter.write(String.format("</%s>\n", MultiSCMChangeLogParser.ROOT_XML_TAG));
        logWriter.close();
    }

    @Override
    public FilePath[] getModuleRoots(FilePath workspace, AbstractBuild build) {
        ArrayList<FilePath> paths = new ArrayList<FilePath>();
        for(SCM scm : scms) {
            FilePath[] p = scm.getModuleRoots(workspace, build);
            for(FilePath p2 : p)
                paths.add(p2);
        }
        return paths.toArray(new FilePath[paths.size()]);
    }

    // Only return supportsPolling when all scms do report back that
    // they supports polling
    @Override
    public boolean supportsPolling()
    {
      for(SCM scm : scms) {
        if (!scm.supportsPolling()) return false;
      }
      return true;
    }

    // When one scm does require a workspace we return true, else
    // we don't need a workspace for polling
    @Override
    public boolean requiresWorkspaceForPolling()
    {
      for(SCM scm : scms) {
        if (scm.requiresWorkspaceForPolling()) return true;
      }
      return false;
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new MultiSCMChangeLogParser(scms.toList());
    }

    public void save() throws IOException {
        // TODO Auto-generated method stub
    }

    @Extension // this marker indicates Hudson that this is an implementation of an extension point.
    public static final class DescriptorImpl extends SCMDescriptor<MultiSCM> {
        public DescriptorImpl() {
            super(MultiSCMRepositoryBrowser.class);
            // TODO Auto-generated constructor stub
        }

        public List<SCMDescriptor<?>> getApplicableSCMs(AbstractProject<?, ?> project) {
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
            save();
            return super.configure(req,formData);
        }

        @Override
        public SCM newInstance(StaplerRequest req, JSONObject formData)
                throws FormException {
            // Read descriptions and invoke newInstance() for each of them
            List<SCM> scmList = new LinkedList<SCM>();
            if (formData.containsKey("scmList")) {
                JSONObject scm = formData.optJSONObject("scmList");
                if (scm == null) {
                    for (Object obj : formData.getJSONArray("scmList")) {
                       readItem(req, (JSONObject)obj, scmList);
                    }
                } else {
                    readItem(req, scm, scmList);
                }
            }

            // return list and wrap exception
            try {
                return new MultiSCM(scmList);
            } catch (IOException ex) {
                throw new FormException(ex, "scmList");
            }
        }

        private static void readItem(StaplerRequest req, JSONObject obj, List<SCM> dest) throws FormException {
            String staplerClass = obj.getString("stapler-class");
            Descriptor<SCM> d = (Descriptor<SCM>) Hudson.getInstance().getDescriptor(staplerClass);
            dest.add(d.newInstance(req, obj));
        }
    }
}
