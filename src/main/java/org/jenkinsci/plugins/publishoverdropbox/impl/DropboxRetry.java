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

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jenkins.plugins.publish_over.Retry;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;

public class DropboxRetry extends Retry implements Describable<DropboxRetry> {


    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public DropboxRetry(final int retries, final long retryDelay) {
        super(retries, retryDelay);
    }

    public DropboxRetryDescriptor getDescriptor() {
        return Jenkins.get().getDescriptorByType(DropboxRetryDescriptor.class);
    }

    public boolean equals(final Object that) {
        if (this == that) return true;
        if (that == null || getClass() != that.getClass()) return false;

        return addToEquals(new EqualsBuilder(), (DropboxRetry) that).isEquals();
    }

    public int hashCode() {
        return addToHashCode(new HashCodeBuilder()).toHashCode();
    }

    public String toString() {
        return addToToString(new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)).toString();
    }

    @Extension
    @SuppressRestrictedWarnings(jenkins.plugins.publish_over.view_defaults.Retry.Messages.class)
    public static class DropboxRetryDescriptor extends Descriptor<DropboxRetry> {

        @Override
        public String getDisplayName() {
            return Messages.retry_descriptor();
        }

        public FormValidation doCheckRetries(@QueryParameter final String value) {
            return FormValidation.validateNonNegativeInteger(value);
        }

        public FormValidation doCheckRetryDelay(@QueryParameter final String value) {
            return FormValidation.validatePositiveInteger(value);
        }

        public jenkins.plugins.publish_over.view_defaults.Retry.Messages getCommonFieldNames() {
            return new jenkins.plugins.publish_over.view_defaults.Retry.Messages();
        }

    }

}
