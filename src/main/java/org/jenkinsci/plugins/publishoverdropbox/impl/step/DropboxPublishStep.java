/*
 * The MIT License
 *
 * Copyright (C) 2017 by René de Groot
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.publishoverdropbox.impl.step;

import hudson.Extension;
import hudson.Util;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.publishoverdropbox.impl.DropboxPublisherPlugin;
import org.jenkinsci.plugins.publishoverdropbox.impl.DropboxTransfer;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;

public class DropboxPublishStep extends AbstractStepImpl {

    private final String sourceFiles;
    private final String remoteDirectory;
    private final String configName;
    private String excludes;
    private String removePrefix;
    private boolean remoteDirectorySDF;
    private boolean flatten;
    private boolean cleanRemote;
    private boolean pruneRoot;
    private int pruneDays;

    @DataBoundConstructor
    public DropboxPublishStep(final String sourceFiles, final String remoteDirectory, final String configName) {
        this.sourceFiles = sourceFiles;
        this.remoteDirectory = remoteDirectory;
        this.configName = configName;
    }

    public String getSourceFiles() {
        return sourceFiles;
    }

    public String getRemoteDirectory() {
        return remoteDirectory;
    }

    public String getConfigName() {
        return configName;
    }

    public ArrayList<DropboxTransfer> getTransfers() {
        ArrayList<DropboxTransfer> tranfers = new ArrayList<>();
        tranfers.add(new DropboxTransfer(sourceFiles, excludes, remoteDirectory, removePrefix, remoteDirectorySDF, flatten, cleanRemote, pruneRoot, pruneDays));
        return tranfers;
    }

    public String getExcludes() {
        return excludes;
    }

    @DataBoundSetter
    public void setExcludes(@CheckForNull String excludes) {
        this.excludes = Util.fixNull(excludes);
    }

    public String getRemovePrefix() {
        return removePrefix;
    }

    @DataBoundSetter
    public void setRemovePrefix(@CheckForNull String removePrefix) {
        this.removePrefix = Util.fixNull(removePrefix);
    }

    public boolean isRemoteDirectorySDF() {
        return remoteDirectorySDF;
    }

    @DataBoundSetter
    public void setRemoteDirectorySDF(boolean remoteDirectorySDF) {
        this.remoteDirectorySDF = remoteDirectorySDF;
    }

    public boolean isFlatten() {
        return flatten;
    }

    @DataBoundSetter
    public void setFlatten(boolean flatten) {
        this.flatten = flatten;
    }

    public boolean isCleanRemote() {
        return cleanRemote;
    }

    @DataBoundSetter
    public void setCleanRemote(boolean cleanRemote) {
        this.cleanRemote = cleanRemote;
    }

    public boolean isPruneRoot() {
        return pruneRoot;
    }

    @DataBoundSetter
    public void setPruneRoot(boolean pruneRoot) {
        this.pruneRoot = pruneRoot;
    }

    public int getPruneDays() {
        return pruneDays;
    }

    @DataBoundSetter
    public void setPruneDays(int pruneDays) {
        this.pruneDays = pruneDays > 0 ? pruneDays : 0;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(DropboxPublishStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "dropbox";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Publish to Dropbox folder";
        }


        public DropboxPublisherPlugin.Descriptor getPublisherPluginDescriptor() {
            return Jenkins.get().getDescriptorByType(DropboxPublisherPlugin.Descriptor.class);
        }
    }

}
