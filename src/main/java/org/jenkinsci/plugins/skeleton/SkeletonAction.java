package org.jenkinsci.plugins.skeleton;

import hudson.model.Action;

public class SkeletonAction implements Action {
  private final int jobNumber;
  private final String beakerURL;

  public SkeletonAction(int    jobNumber,
                        String beakerURL) {
    this.jobNumber = jobNumber;
    this.beakerURL = beakerURL;
  }

  public String getIconFileName() {
    return "/plugin/skeleton/icons/beaker24.png";
  }

  public String getDisplayName() {
    return "Skeleton job J:" + jobNumber;
  }

  public String getUrlName() {
    return beakerURL + "/jobs/" + jobNumber;
  }

  public int getJobNumber() {
    return jobNumber;
  }
}
