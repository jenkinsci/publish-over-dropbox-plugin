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
 * all copies or su;bstantial portions of the Software.
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

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.DaemonThreadFactory;
import hudson.util.NamingThreadFactory;
import jenkins.model.Jenkins;
import jenkins.plugins.publish_over.*;
import org.jenkinsci.plugins.publishoverdropbox.domain.DropboxClient;
import org.jenkinsci.plugins.publishoverdropbox.impl.DropboxPublisher;
import org.jenkinsci.plugins.publishoverdropbox.impl.DropboxPublisherPlugin;
import org.jenkinsci.plugins.publishoverdropbox.impl.Messages;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static jenkins.util.BuildListenerAdapter.wrap;
import java.io.Serializable;

public class DropboxPublishStepExecution extends StepExecution implements BPHostConfigurationAccess<DropboxClient, Serializable> {

    private static final long serialVersionUID = 1L;

    private static ExecutorService executorService;

    @StepContextParameter
    private transient TaskListener taskListener;
    @StepContextParameter
    private transient Run run;
    @StepContextParameter
    private transient FilePath workspace;
    @StepContextParameter
    private transient EnvVars envVars;
    @Inject
    private transient DropboxPublishStep step;
    private transient boolean canceled;
    private transient Throwable cancelCause;

    @Override
    public boolean start() throws Exception {
        getExecutorService().submit(new Runnable() {
            @Override
            public void run() {
                DropboxPublisher publisher = new DropboxPublisher(step.getConfigName(), false, step.getTransfers(), true, true, null, null);
                ArrayList<DropboxPublisher> publishers = new ArrayList<>(Arrays.asList(publisher));
                TaskListener listener = wrap(taskListener);
                String consolePrefix = Messages.console_message_prefix();
                BPBuildEnv currentBuildEnv = new BPBuildEnv(envVars, workspace, run.getTimestamp());
                final BPBuildInfo buildInfo = new BPBuildInfo(listener, consolePrefix, Jenkins.get().getRootPath(), currentBuildEnv, null);

                // Reusing the delegate from publish-to base plugin from the pre-step era
                BPInstanceConfig<DropboxPublisher> delegate = new BPInstanceConfig<>(publishers, false, false, false, null, null);
                delegate.setHostConfigurationAccess(DropboxPublishStepExecution.this);

                try {
                    Result result = delegate.perform(buildInfo);
                    if (result != Result.SUCCESS) {
                        setCanceled(true);
                        cancelCause = new Exception(result.toString());
                    }
                } catch (Exception e) {
                    setCanceled(true);
                    cancelCause = e;
                }

                if (isCanceled()) {
                    getContext().onFailure(cancelCause);
                } else {
                    getContext().onSuccess(null);
                }
            }
        });

        return false;
    }

    @Override
    public void stop(@NonNull Throwable cause) throws Exception {
        cancelCause = cause;
        setCanceled(true);
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    public boolean isCanceled() {
        return canceled;
    }

    private static synchronized ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = Executors.newCachedThreadPool(
                    new NamingThreadFactory(
                            new DaemonThreadFactory(), "org.jenkinsci.plugins.publishoverdropbox.impl.step.DropboxPublishStepExecution"));
        }

        return executorService;
    }

    @Override
    public BPHostConfiguration<DropboxClient, Serializable> getConfiguration(String name) {
        final DropboxPublisherPlugin.Descriptor pluginDescriptor = Jenkins.get().getDescriptorByType(DropboxPublisherPlugin.Descriptor.class);
        return pluginDescriptor.getConfiguration(step.getConfigName());
    }
}
