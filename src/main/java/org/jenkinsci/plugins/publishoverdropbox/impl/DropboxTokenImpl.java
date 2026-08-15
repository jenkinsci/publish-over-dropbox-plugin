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

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import hudson.Extension;
import hudson.util.Secret;
import org.jenkinsci.plugins.publishoverdropbox.DropboxToken;
import org.jenkinsci.plugins.publishoverdropbox.domain.DropboxV2;
import org.jenkinsci.plugins.publishoverdropbox.domain.model.RestException;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

public class DropboxTokenImpl extends BaseStandardCredentials implements DropboxToken {

    static final long serialVersionUID = 43L;

    @NonNull
    private final Secret authorizationCode;
    @NonNull
    private final Secret accessCode;

    @DataBoundConstructor
    public DropboxTokenImpl(CredentialsScope scope, String id, @NonNull Secret authorizationCode, String description) throws IOException {
        super(scope, id, description);
        this.authorizationCode = authorizationCode;
        try {
            this.accessCode = DropboxV2.convertAuthorizationToAccessCode(authorizationCode);
        } catch (RestException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @NonNull
    @Override
    public Secret getAuthorizationCode() {
        return authorizationCode;
    }

    @NonNull
    @Override
    public Secret getAccessCode() {
        return accessCode;
    }

    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.DropboxTokenImpl_api_token();
        }
    }
}
