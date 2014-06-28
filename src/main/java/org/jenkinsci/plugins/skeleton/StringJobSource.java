package org.jenkinsci.plugins.skeleton;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;

import java.io.File;
import java.io.IOException;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;

public class StringJobSource extends JobSource {
  private final String jobContent;

  @DataBoundConstructor
  public StringJobSource(String jobName,
                         String jobContent) {
    this.jobName    = jobName;
    this.jobContent = jobContent;
  }

  private static String Join(String r[], String d)
  {
    if (r.length == 0) return "";

    StringBuilder sb = new StringBuilder();
    int i;

    for (i = 0; i < r.length - 1; i++) sb.append(r[i] + d);
    return sb.toString() + r[i];
  }

  private static String[] Push(String[] array, String push) {
    String[] longer = new String[array.length + 1];
    System.arraycopy(array, 0, longer, 0, array.length);
    longer[array.length] = push;
    return longer;
  }

  public String getJobContent() {
    return jobContent;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public File createJobFile(AbstractBuild< ? ,
                                           ? >build,
                            BuildListener     listener) throws
  InterruptedException, IOException {
    String[] lines = null;
    String splitPattern = "\n";
    String joinPattern  = "\r\n";
    lines = jobContent.split(splitPattern);

    FilePath path = createDefaultJobFile(Join(Push(lines,
                                                   ""),
                                              joinPattern), build, listener);
    return new File(path.getRemote());
  }

  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl)Jenkins.getInstance().getDescriptor(getClass());
  }

  @Extension
  public static class DescriptorImpl extends JobSourceDescriptor {
    public String getDisplayName() {
      return "String script source";
    }
  }
}
