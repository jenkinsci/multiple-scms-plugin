package org.jenkinsci.plugins.multiplescms;

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
        for(SCM scm : scms) {
            try {
                scm.buildEnvVars(build, env);
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

        MultiSCMRevisionState oldBaseline = baseline instanceof MultiSCMRevisionState ? (MultiSCMRevisionState) baseline : null;
        MultiSCMRevisionState revisionState = new MultiSCMRevisionState();
        build.addAction(revisionState);

        HashSet<Object> scmActions = new HashSet<Object>();

        FileOutputStream logStream = new FileOutputStream(changelogFile);
        OutputStreamWriter logWriter = new OutputStreamWriter(logStream);
        logWriter.write(String.format("<%s>\n", MultiSCMChangeLogParser.ROOT_XML_TAG));

        for(SCM scm : scms) {
            File subChangeLog = changelogFile != null ? new File(changelogFile.getPath() + ".temp") : null;
            SCMRevisionState workspaceRevision = null;
            if (oldBaseline != null) {
                            workspaceRevision = oldBaseline.get(scm, workspace, build instanceof AbstractBuild ? (AbstractBuild) build : null);
            }
            scm.checkout(build, launcher, workspace, listener, subChangeLog, workspaceRevision);

            List<Action> actions = build.getActions();
            for(Action a : actions) {
                if(!scmActions.contains(a) && a instanceof SCMRevisionState && !(a instanceof MultiSCMRevisionState)) {
                    scmActions.add(a);
                    revisionState.add(scm, workspace, build, (SCMRevisionState) a);
                }
            }
            if (subChangeLog != null && subChangeLog.exists()) {
                String subLogText = FileUtils.readFileToString(subChangeLog);
                //Dont forget to escape the XML in case there is any CDATA sections
                logWriter.write(String.format("<%s scm=\"%s\">\n<![CDATA[%s]]>\n</%s>\n",
                        MultiSCMChangeLogParser.SUB_LOG_TAG,
                        scm.getKey(),
                        StringEscapeUtils.escapeXml(subLogText),
                        MultiSCMChangeLogParser.SUB_LOG_TAG));

                subChangeLog.delete();
            }
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
