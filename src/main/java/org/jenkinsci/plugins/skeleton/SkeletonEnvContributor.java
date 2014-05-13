package org.jenkinsci.plugins.skeleton;

import java.io.IOException;
import java.util.logging.Logger;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;



@Extension
public class SkeletonEnvContributor extends EnvironmentContributor {

    @Override
    public void buildEnvironmentFor(@SuppressWarnings("rawtypes") Run r, EnvVars envs, TaskListener listener)
            throws IOException, InterruptedException {
        SkeletonAction bba = r.getAction(SkeletonAction.class);
        if (bba != null)
            try {
                envs.put("BEAKER_JOB_ID", String.valueOf(bba.getJobNumber()));
            } catch (NumberFormatException e) {
                LOGGER.warning("Cannot convert " + bba.getJobNumber() + " to integer");
            }
    }
    
    private static final Logger LOGGER = Logger.getLogger(SkeletonEnvContributor.class.getName());
}
