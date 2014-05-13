package org.jenkinsci.plugins.skeleton;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.ParametersAction;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;


public class FileJobSource extends JobSource {

    private String jobPath;

    @DataBoundConstructor
    public FileJobSource(String jobName, String jobPath) {
        this.jobName = jobName;
        this.jobPath = jobPath;
    }

    /**
     * {@inheritDoc}
     */
    public String getJobPath() {
        return jobPath;
    }

    /**
     * Reads job XML from file and expands variable by calling
     * {@link JobSource#createDefaultJobFile(String, AbstractBuild, BuildListener)}. For security reasons file path is
     * assumes that file path is relative to workspace directory (i.e. file is within workspace).
     */
    @Override
    public File createJobFile(AbstractBuild<?, ?> build, BuildListener listener) throws InterruptedException,
            IOException {
        // TODO check, if file really exists
        // TODO check, is path is really relative to WS root
        FilePath fp = new FilePath(build.getWorkspace(), expandJobPath(build, listener)); 
        // TODO not very safe, if e.g. some malicious user provide path to a huge file                  
        String jobContent = fp.readToString(); 
        FilePath path = createDefaultJobFile(jobContent, build, listener);
        return new File(path.getRemote());
    }

    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptor(getClass());
    }
    
    protected String expandJobPath(AbstractBuild<?, ?> build, BuildListener listener) {
        String expandedPath = getJobPath();
        ParametersAction pa = build.getAction(ParametersAction.class);
        // expand build parameters
        if (pa != null)
            expandedPath = pa.substitute(build, expandedPath);
        // expand environment variables
        try {
            expandedPath = build.getEnvironment(listener).expand(expandedPath);
        } catch(IOException e) {
           LOGGER.warning("Cannot expand job path '" + expandedPath + "', caused by: " + e.getMessage());
        } catch(InterruptedException e) { // support JDK prior to JDK7 by separate catch blocks
            LOGGER.warning("Cannot expand job path '" + expandedPath + "', caused by: " + e.getMessage()); 
        }
        return expandedPath;
    }

    @Extension
    public static class DescriptorImpl extends JobSourceDescriptor {
        public String getDisplayName() {
            return "File job source";
        }
    }
    
    private static final Logger LOGGER = Logger.getLogger(FileJobSource.class.getName());

}

