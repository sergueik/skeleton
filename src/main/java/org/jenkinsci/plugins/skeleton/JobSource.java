package org.jenkinsci.plugins.skeleton;

import hudson.DescriptorExtensionList;
import hudson.FilePath;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.model.ParametersAction;

import java.io.File;
import java.io.IOException;

import jenkins.model.Jenkins;

public abstract class JobSource implements Describable<JobSource>{
  protected static final String DEFAULT_JOB_PREFIX = "jenkins_";
  protected static final String DEFAULT_JOB_SUFFIX = ".ps1";

  protected String jobName;

  public String getJobName() {
    return jobName;
  }

  public abstract File createJobFile(AbstractBuild< ? , ? >build,
                                     BuildListener listener) throws
  InterruptedException,
  IOException;

  public FilePath createDefaultJobFile(String jobContent,
                                       AbstractBuild< ? , ? >build,
                                       BuildListener listener)
  throws InterruptedException, IOException {
    String job          = jobContent;
    ParametersAction pa = build.getAction(ParametersAction.class );

    // expand build parameters
    if (pa != null) job = pa.substitute(build, job);

    // expand environment variables
    jobContent = build.getEnvironment(listener).expand(jobContent);

    // The newlines are not converted to platform specific
    FilePath path = build.getWorkspace().createTextTempFile(DEFAULT_JOB_PREFIX,
                                                            DEFAULT_JOB_SUFFIX,
                                                            jobContent,
                                                            true);
    return path;
  }

  public static DescriptorExtensionList<JobSource,
                                        JobSource.JobSourceDescriptor>all() {
    return Jenkins.getInstance().getDescriptorList(JobSource.class );
  }

  public static abstract class JobSourceDescriptor extends Descriptor<JobSource>{}
}
