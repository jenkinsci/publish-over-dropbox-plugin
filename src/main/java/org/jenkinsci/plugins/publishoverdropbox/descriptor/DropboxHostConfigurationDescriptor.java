/*
 * The MIT License
 *
 * Copyright (C) 2015 by René de Groot
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

package org.jenkinsci.plugins.publishoverdropbox.descriptor;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.plugins.publish_over.BPValidators;
import org.jenkinsci.plugins.publishoverdropbox.DropboxToken;
import org.jenkinsci.plugins.publishoverdropbox.impl.DropboxHostConfiguration;
import org.jenkinsci.plugins.publishoverdropbox.impl.DropboxPublisherPlugin;
import org.jenkinsci.plugins.publishoverdropbox.impl.Messages;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

import java.util.List;

@Extension
public class DropboxHostConfigurationDescriptor extends Descriptor<DropboxHostConfiguration> {

    public DropboxHostConfigurationDescriptor() {
        super(DropboxHostConfiguration.class);
    }

    @Override
    public String getDisplayName() {
        return Messages.hostconfig_descriptor();
    }

    public int getDefaultTimeout() {
        return DropboxHostConfiguration.DEFAULT_TIMEOUT;
    }

    public FormValidation doCheckName(@QueryParameter final String value) {
        return BPValidators.validateName(value);
    }

    public FormValidation doCheckTimeout(@QueryParameter final String value) {
        return FormValidation.validateNonNegativeInteger(value);
    }

    public FormValidation doCheckRemoteRootDir(@QueryParameter String remoteRootDir) {
        if (remoteRootDir.matches("/.*")) return FormValidation.ok();
        else return FormValidation.error(Messages.hostconfig_formvalidation_root());
    }

    public ListBoxModel doFillTokenItems() {
        ListBoxModel items = new ListBoxModel();
        for (DropboxToken token : getDropboxTokens()) {
            items.add(token.getDescription(), token.getId());
        }
        if (items.size() > 0) {
            items.get(0).selected = true;
        }
        return items;
    }

    private List<DropboxToken> getDropboxTokens() {
        return CredentialsProvider.lookupCredentials(DropboxToken.class, Jenkins.get(), null, (DomainRequirement) null);
    }

    public FormValidation doTestConnection(final StaplerRequest2 request, final StaplerResponse2 response) {
        final DropboxPublisherPlugin.Descriptor pluginDescriptor = Jenkins.get().getDescriptorByType(
                DropboxPublisherPlugin.Descriptor.class);
        return pluginDescriptor.doTestConnection(request, response);
    }

    public jenkins.plugins.publish_over.view_defaults.HostConfiguration.Messages getCommonFieldNames() {
        return new jenkins.plugins.publish_over.view_defaults.HostConfiguration.Messages();
    }

}