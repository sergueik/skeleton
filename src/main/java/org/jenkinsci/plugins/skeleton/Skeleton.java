package org.jenkinsci.plugins.skeleton;

import hudson.Extension;
import hudson.FilePath;
import hudson.EnvVars;
import hudson.Util;
import hudson.util.VariableResolver;
import hudson.util.FormValidation;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.skeleton.utils.ConsoleLogger;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class Skeleton extends Builder {
  private final JobSource jobSource;
 // private final boolean   downloadFiles;
  private String properties;
  private String javaOpts;

  @DataBoundConstructor
  public Skeleton(JobSource jobSource,
                  boolean   downloadFiles) {
    this.jobSource     = jobSource;
   // this.downloadFiles = downloadFiles;
   // System.out.println("download is " + downloadFiles);
  }

  public JobSource getJobSource() {
    return jobSource;
  }
/*
  public boolean getDownloadFiles() {
    return downloadFiles;
  }
*/
  private void log(ConsoleLogger console, String message) {
    console.logAnnot(message);
  }

  public static Properties parseProperties(final String properties)
  throws IOException
  {
    Properties props = new Properties();

    if (properties != null) {
      try {
        props.load(new StringReader(properties));
      } catch (NoSuchMethodError err) {
        props.load(new ByteArrayInputStream(properties.getBytes()));
      }
    }

    return props;
  }

  private boolean verifyFile(File jobFile,
                             AbstractBuild< ? , ? >build,
                             ConsoleLogger console) {
    if (jobFile == null) return false;

    FilePath fp = new FilePath(build.getWorkspace(), jobFile.getPath());

    try {
      if (!fp.exists()) {
        log(console,
            "[Skeleton] ERROR: Job file " + fp.getName() +
            " doesn't exists on channel" + fp.getChannel() + "!");

        // build.setResult(Result.FAILURE);
        return false;
      }
    } catch (IOException e) {
        log(console,
          "[Skeleton] ERROR: failed to verify that " + fp.getName() +
          " exists on channel" + fp.getChannel()
          + "! IOException cought, check Jenkins log for more details");

      // build.setResult(Result.FAILURE);
      return false;
    } catch (InterruptedException e) {
      // TODO log exception
    }
    // TODO verify it's valid XML file

    return true;
  }

  public String getProperties() {
    return properties;
  }

  private File prepareJob(AbstractBuild< ? ,
                                         ? >build,
                          ConsoleLogger     console) throws InterruptedException
  {
    File jobFile = null;

    try {
      jobFile = jobSource.createJobFile(build, console.getListener());
    } catch (IOException ioe) {
        log(console,
          "[skeleton] ERROR: Could not get canonical path to workspace:" + ioe);
      ioe.printStackTrace();
      build.setResult(Result.FAILURE);
    }
    return jobFile;
  }

  @Override
  public boolean perform(AbstractBuild< ? , ? >build,
                         Launcher launcher,
                         BuildListener listener)
  throws InterruptedException {
    boolean performStatus =  false;
    ConsoleLogger console = new ConsoleLogger(listener);
    File jobFile          = prepareJob(build, console);

    if (!verifyFile(jobFile, build, console)) {
      try {
        jobFile.delete();
      } catch (Exception e) {
        // Util.displayIOException(e,listener);
        e.printStackTrace(listener.fatalError("Unable to delete script file " +
                                              jobFile.getName()));
      }
      return false;
    }

        log(console,
        "[Skeleton] INFO: Job file  " + jobFile.getName() + " prepared");
    String jobXml = readJobFile(jobFile, build);

    if (jobSource == null) {
      listener.fatalError("There is no script configured for this builder");
      return false;
    } else {
      try {
        String[] cmd =
          buildCommandLine(build, listener,
                           new FilePath(build.getWorkspace(),
                                        jobFile.getPath()), false);
        int result;
        try {
          int launcherResult          = 0;
          Map<String, String> envVars = build.getEnvironment(listener);

          /*
             hudson.plugins.groovy.GroovyInstallation installation =
                getGroovy();
             if(installation != null) {
             envVars.put("GROOVY_HOME", installation.getHome());
             }
           */
          for (Map.Entry<String,
                         String>e : build.getBuildVariables().entrySet()) {
            envVars.put(e.getKey(), e.getValue());
          }

          if (properties != null) {
            String origJavaOpts   = build.getBuildVariables().get("JAVA_OPTS");
            StringBuffer javaOpts = new StringBuffer(
              (origJavaOpts != null) ? origJavaOpts : "");
            Properties props      = parseProperties(properties);

            for (Entry<Object, Object>entry : props.entrySet()) {
              javaOpts.append(" -D" + entry.getKey() + "=" + entry.getValue());
            }

            // Add javaOpts at the end
            if (this.javaOpts != null) // backward compatibility
              javaOpts.append(" " + this.javaOpts);

            envVars.put("JAVA_OPTS", javaOpts.toString());
          }

          envVars.put("$PATH_SEPARATOR", ":::");

          StringBuffer sb = new StringBuffer();

          for (String c : cmd) {
            sb.append(c);
            sb.append(" ");
          }
          log(console, "[Skeleton] INFO: Command  " + sb.toString());
          result = 0;
          result = launcher.launch().cmds(cmd).envs(envVars).stdout(listener).pwd(
            build.getWorkspace()).join();
        } catch (IOException e) {
          // Util.displayIOException(e,listener);
          e.printStackTrace(listener.fatalError("command execution failed"));
          result = -1;
        }
        performStatus = (result == 0);
      } catch (Exception e) {
        e.printStackTrace(listener.fatalError("Unable to delete script file " +
                                              jobFile.getName()));
      }
    }

    // TODO - add option to keep the script
          log(console, "[Skeleton] INFO: Deleting Job file  " + jobFile.getName());
    try {
      jobFile.delete();
    } catch (Exception e) {
      // Util.displayIOException(e,listener);
      e.printStackTrace(listener.fatalError("Unable to delete script file " +
                                            jobFile.getName()));
    }
          log(console, "[Skeleton] INFO: Script file deleted: " + jobFile.getName());
    return performStatus;
  }

  private String readJobFile(File jobFile, AbstractBuild< ? , ? >build) {
    String srcContent = null;

    try {
      FilePath fp = new FilePath(build.getWorkspace(), jobFile.getPath());
      srcContent = fp.readToString();
      System.out.println("Powershell Script: " + srcContent);
    } catch (IOException e) {
      LOGGER.log(Level.INFO, "Skeleton error: failed to read script file:"
                 + jobFile.getPath(), e);
    }
    return srcContent;
  }

  @Override
  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) super.getDescriptor();
  }

  protected String[] buildCommandLine(AbstractBuild build,
                                      BuildListener listener,
                                      FilePath      script,
                                      boolean       isOnUnix) throws IOException,
  InterruptedException  {
    ArrayList<String> list = new ArrayList<String>();

    // prepare variable resolver - more efficient than calling env.expand(s)
    EnvVars env = build.getEnvironment(listener);
    env.overrideAll(build.getBuildVariables());
    VariableResolver<String> vr = new VariableResolver.ByMap<String>(env);


    String cmd = "powershell.exe"; // last hope in case of missing or not
                                   // selected installation

    /*
       hudson.plugins.groovy.GroovyInstallation installation = getGroovy();
       if(installation != null) {
            installation =
               installation.forNode(Computer.currentComputer().getNode(),
               listener);
            installation = installation.forEnvironment(env);
        cmd = installation.getExecutable(script.getChannel());
        //some misconfiguration, reverting back to default groovy cmd
        if(null == cmd){
            cmd = "groovy";
            listener.getLogger().println("[GROOVY WARNING] Groovy executable is
               NULL, please chekc your Groovy configuration, trying fallback
               'groovy' instead.");
        }
       }
     */
    list.add(cmd);

    // Add groovy parameters

    /*

       if(parameters != null) {
        StringTokenizer tokens = new StringTokenizer(parameters);
        while(tokens.hasMoreTokens()) {
            list.add(Util.replaceMacro(tokens.nextToken(),vr));
        }
       }

       list.add(script.getRemote());
     */

    // Add script parameters

    /*
       if(scriptParameters != null) {
        StringTokenizer tokens = new StringTokenizer(scriptParameters);
        ParametersAction parameters = build.getAction(ParametersAction.class);
        while(tokens.hasMoreTokens()) {
            String token = tokens.nextToken();
            //first replace parameter from parameterized build
            if (parameters != null) {
                token = parameters.substitute(build, token);
            }
            //then replace evn vars
            token = Util.replaceMacro(token,vr);
            list.add(token);
        }
       }
     */

    list.add(script.getRemote());
    return list.toArray(new String[] {});
  }

  @Extension
  public static final class DescriptorImpl extends BuildStepDescriptor<Builder>{
    public DescriptorImpl() {
      load();
      setupClient();
    }

    @Override
    public boolean isApplicable(
      @SuppressWarnings("rawtypes") Class< ? extends AbstractProject>aClass) {
      return true;
    }

    public String getDisplayName() {
      return "Execute Skeleton task";
    }

    @Override
    public boolean configure(StaplerRequest req,
                             JSONObject     formData) throws FormException {
      save();
      return super.configure(req, formData);
    }

    private void setupClient() {}
  }
  private static final Logger LOGGER = Logger.getLogger(Skeleton.class.getName());
}
