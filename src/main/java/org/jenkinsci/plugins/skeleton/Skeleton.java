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
private String properties;
private String javaOpts;

@DataBoundConstructor
public Skeleton(JobSource jobSource,
		boolean downloadFiles)
{
	this.jobSource     = jobSource;
}

public JobSource getJobSource()
{
	return jobSource;
}
private void log(ConsoleLogger console, String message)
{
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

private boolean verifyFile(File jobFile, AbstractBuild<?, ?>build, ConsoleLogger console)
{
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
	return true;
}

public String getProperties()
{
	return properties;
}

private File prepareJob(AbstractBuild<?, ?>build, ConsoleLogger console) throws InterruptedException
{
	File jobFile = null;

	try {
		jobFile = jobSource.createJobFile(build, console.getListener());
	} catch (IOException ioe) {
// TODO : is this code necessary ?
		log(console,
		    "[skeleton] ERROR: Could not get canonical path to workspace:" + ioe);
		ioe.printStackTrace();
		build.setResult(Result.FAILURE);
	}
	return jobFile;
}

@Override
public boolean perform(AbstractBuild<?, ?>build, Launcher launcher, BuildListener listener)
throws InterruptedException
{
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
				buildCommandLine(build, listener, new FilePath(build.getWorkspace(), jobFile.getPath()), false);
			int result;
			try {
				int launcherResult = 0;
				Map<String, String> envVars = build.getEnvironment(listener);

				for (Map.Entry<String,
					       String>e : build.getBuildVariables().entrySet())
					envVars.put(e.getKey(), e.getValue());
				// TODO : use thos code to pass in the Execution Policy Options
				if (properties != null) {
					String origJavaOpts   = build.getBuildVariables().get("JAVA_OPTS");
					StringBuffer javaOpts = new StringBuffer(
						(origJavaOpts != null) ? origJavaOpts : "");
					Properties props      = parseProperties(properties);

					for (Entry<Object, Object>entry : props.entrySet())
						javaOpts.append(" -D" + entry.getKey() + "=" + entry.getValue());

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

				sb.append( "-ExecutionPolicy");
				sb.append(" ");
				sb.append( "ByPass");
				sb.append(" ");
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
			e.printStackTrace(listener.fatalError("Unable to delete script file " + jobFile.getName()));
		}
	}

	// TODO - add option to keep the script
	log(console, "[Skeleton] INFO: Deleting Job file  " + jobFile.getName());
	try {
		jobFile.delete();
	} catch (Exception e) {
		// Util.displayIOException(e,listener);
		e.printStackTrace(listener.fatalError("Unable to delete script file " + jobFile.getName()));
	}
	log(console, "[Skeleton] INFO: Script file deleted: " + jobFile.getName());
	return performStatus;
}

private String readJobFile(File jobFile, AbstractBuild< ?, ? >build)
{
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
public DescriptorImpl getDescriptor()
{
	return (DescriptorImpl) super.getDescriptor();
}

protected String[] buildCommandLine(AbstractBuild build, BuildListener listener, FilePath script, boolean isOnUnix) throws IOException,
InterruptedException
{
	ArrayList<String> list = new ArrayList<String>();

	EnvVars env = build.getEnvironment(listener);
	env.overrideAll(build.getBuildVariables());
	// TODO: variable resolver more efficient than calling env.expand(s)
	VariableResolver<String> vr = new VariableResolver.ByMap<String>(env);

	ArrayList<String> paths = new ArrayList<String>();
	paths.add(
		env.get("SYSTEMROOT") + "\\sysnative\\WindowsPowershell\\v1.0\\powershell.exe"
		);
	paths.add(
		env.get("SYSTEMROOT") + "\\system32\\WindowsPowershell\\v1.0\\powershell.exe"
		);
	String cmd = "powershell.exe"; // last hope in case of missing or not
	for (String path : paths) {
		File fp = new File(path);
		if (fp.exists())
			cmd = path; 
	}
	// selected installation
	list.add(cmd);

	list.add(script.getRemote());
	return list.toArray(new String[] {});
}

@Extension
public static final class DescriptorImpl extends BuildStepDescriptor<Builder>{
public DescriptorImpl()
{
	load();
	setupClient();
}

@Override
public boolean isApplicable(
	@SuppressWarnings("rawtypes") Class< ? extends AbstractProject>aClass)
{
	return true;
}

public String getDisplayName()
{
	return "Execute Skeleton task";
}

@Override
public boolean configure(StaplerRequest req, JSONObject formData) throws FormException
{
	save();
	return super.configure(req, formData);
}

private void setupClient()
{
}
}
private static final Logger LOGGER = Logger.getLogger(Skeleton.class.getName());
}
