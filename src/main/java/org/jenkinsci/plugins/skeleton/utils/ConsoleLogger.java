package org.jenkinsci.plugins.skeleton.utils;

import hudson.model.BuildListener;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;


public class ConsoleLogger {

    private final BuildListener listener;
    private final SkeletonConsoleAnnotator annotator;

    public ConsoleLogger(BuildListener listener) {
        this.listener = listener;
        this.annotator = new SkeletonConsoleAnnotator(this.listener.getLogger());
    }

    public BuildListener getListener() {
        return listener;
    }

    public PrintStream getLogger() {
        return listener.getLogger();
    }

    public void logAnnot(String message) {
        byte[] msg = (message + "\n").getBytes(Charset.defaultCharset());
        try {
            annotator.eol(msg, msg.length);
        } catch (IOException e) {
            listener.getLogger().println("Problem with writing into console log: " + e.getMessage());
        }
    }

    public void log(String message) {
        listener.getLogger().println(message);
    }
}

