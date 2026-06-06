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

package org.jenkinsci.plugins.publishoverdropbox.impl;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.model.Describable;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import jenkins.plugins.publish_over.BPBuildInfo;
import jenkins.plugins.publish_over.BPHostConfiguration;
import jenkins.plugins.publish_over.BapPublisherException;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.jenkinsci.plugins.publishoverdropbox.DropboxToken;
import org.jenkinsci.plugins.publishoverdropbox.descriptor.DropboxHostConfigurationDescriptor;
import org.jenkinsci.plugins.publishoverdropbox.domain.DropboxClient;
import org.jenkinsci.plugins.publishoverdropbox.domain.DropboxV2;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.List;

public class DropboxHostConfiguration extends BPHostConfiguration<DropboxClient, Object> implements Describable<DropboxHostConfiguration> {

    public static final int DEFAULT_TIMEOUT = 300000;
    private static final long serialVersionUID = 1L;
    private DropboxToken token;
    private final int timeout;

    @DataBoundConstructor
    public DropboxHostConfiguration(final String name, final String token, final String remoteRootDir, final int timeout) {
        super(name, null, null, null, remoteRootDir, 0);
        this.timeout = timeout;
        this.token = token == null ? null : lookupTokenId(token);
    }

    private DropboxToken lookupTokenId(String tokenId) {
        List<DropboxToken> credentials = CredentialsProvider.lookupCredentials(DropboxToken.class, Jenkins.getActiveInstance(), null, (DomainRequirement) null);
        for (DropboxToken token : credentials) {
            if (tokenId.equals(token.getId())) {
                return token;
            }
        }
        return null;
    }

    public String getToken() {
        return token != null ? token.getId() : null;
    }

    public void setToken(final DropboxToken token) {
        this.token = token;
    }

    @Override
    public DropboxClient createClient(final BPBuildInfo buildInfo) {
        final DropboxClient client = new DropboxClient(createDropbox(), buildInfo);
        try {
            init(client);
        } catch (IOException ioe) {
            throw new BapPublisherException(Messages.exception_bap_createclient(ioe.getLocalizedMessage()), ioe);
        }
        return client;
    }

    private DropboxV2 createDropbox() {
        if (token == null) {
            throw new BapPublisherException(Messages.exception_bap_createclient("Missing token"));
        }
        return new DropboxV2(Secret.toString(token.getAccessCode()));
    }


    private void init(final DropboxClient client) throws IOException {
        configureDropboxclient(client);
        connect(client);

        changeToRootDirectory(client);
        setRootDirectoryInClient(client);
    }

    private void configureDropboxclient(final DropboxClient client) {
        client.setTimeout(timeout);
        client.setToken(Secret.toString(token.getAuthorizationCode()));
    }

    private void connect(final DropboxClient client) {
        if (!client.connect()) {
            exception(client, Messages.exception_bap_logInFailed(getToken()));
        }
    }

    private void setRootDirectoryInClient(final DropboxClient client) {
        if (isDirectoryAbsolute(getRemoteRootDir())) {
            client.setAbsoluteRemoteRoot(getRemoteRootDir());
        }
    }

    public DropboxHostConfigurationDescriptor getDescriptor() {
        return Jenkins.getActiveInstance().getDescriptorByType(DropboxHostConfigurationDescriptor.class);
    }

    protected HashCodeBuilder addToHashCode(final HashCodeBuilder builder) {
        return super.addToHashCode(builder)
                .append(token)
                .append(timeout);
    }

    protected EqualsBuilder addToEquals(final EqualsBuilder builder, final DropboxHostConfiguration that) {
        return super.addToEquals(builder, that)
                .append(token, that.token)
                .append(timeout, that.timeout);
    }

    protected ToStringBuilder addToToString(final ToStringBuilder builder) {
        return super.addToToString(builder)
                .append("token", token)
                .append("timeout", timeout);
    }

    public boolean equals(final Object that) {
        if (this == that) return true;
        if (that == null || getClass() != that.getClass()) return false;

        return addToEquals(new EqualsBuilder(), (DropboxHostConfiguration) that).isEquals();
    }

    public int hashCode() {
        return addToHashCode(new HashCodeBuilder()).toHashCode();
    }

    public String toString() {
        return addToToString(new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)).toString();
    }


}