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

package org.jenkinsci.plugins.publishoverdropbox.domain;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.publishoverdropbox.DropboxToken;
import org.jenkinsci.plugins.publishoverdropbox.domain.model.*;
import org.jenkinsci.plugins.publishoverdropbox.domain.model.requests.*;
import org.jenkinsci.plugins.publishoverdropbox.gson.RuntimeTypeAdapterFactory;
import org.jenkinsci.plugins.publishoverdropbox.impl.Messages;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.jenkinsci.plugins.publishoverdropbox.domain.JsonObjectRequest.Method.POST;


public class DropboxV2 implements DropboxAdapter {
    private static final String URL_TOKEN = "https://api.dropbox.com/oauth2/token";
    private static final String URL_ACCOUNT_INFO = "https://api.dropbox.com/2/users/get_current_account";
    private static final String URL_METADATA = "https://api.dropboxapi.com/2/files/get_metadata";
    private static final String URL_LIST_FOLDER = "https://api.dropboxapi.com/2/files/list_folder";
    private static final String URL_LIST_FOLDER_CONTINUE = "https://api.dropboxapi.com/2/files/list_folder/continue";
    private static final String URL_OPS_DELETE = "https://api.dropboxapi.com/2/files/delete";
    private static final String URL_CREATE_FOLDER = "https://api.dropboxapi.com/2/files/create_folder";

    private static final String URL_UPLOAD = "https://content.dropboxapi.com/2/files/upload";
    private static final String URL_UPLOAD_START = "https://content.dropboxapi.com/2/files/upload_session/start";
    private static final String URL_UPLOAD_APPEND = "https://content.dropboxapi.com/2/files/upload_session/append_v2";
    private static final String URL_UPLOAD_FINISH = "https://content.dropboxapi.com/2/files/upload_session/finish";
    private static final String PATH_SEPARATOR = "/";
    private static final String VALUE_AUTHORIZATION_CODE = "authorization_code";
    private static final long MILLISECONDS_PER_DAY = 24 * 60 * 60 * 1000;
    private static final long FOUR_MEGA_BYTE = 4 * 1024 * 1024;
    private static final String APPLICATION_JSON = "application/json";
    private static final String APPLICATION_OCTET_STREAM = "application/octet-stream";

    @VisibleForTesting
    long chunkSize = FOUR_MEGA_BYTE;
    private int timeout = -1;
    private final String accessToken;
    private final Gson gson;
    private AccountInfo userInfo;
    private FolderMetadata workingFolder;

    public DropboxV2(String accessToken) {
        this.accessToken = accessToken;
        this.gson = createGson();
    }

    public static Gson createGson() {
        RuntimeTypeAdapterFactory<Metadata> metadataAdapterFactory = RuntimeTypeAdapterFactory
                .of(Metadata.class, ".tag")
                .registerSubtype(FolderMetadata.class, "folder")
                .registerSubtype(FileMetadata.class, "file");
        return new GsonBuilder()
                .registerTypeAdapterFactory(metadataAdapterFactory)
                .create();
    }

    /**
     * Change the timeout value, values lower then 1000 are ignored and reset to default.
     *
     * @param timeout timeout in miliseconds
     */
    @Override
    public void setTimeout(int timeout) {
        if (timeout >= 1000) {
            this.timeout = timeout;
        } else {
            this.timeout = -1;
        }
    }

    @Override
    public int getTimeout() {
        return timeout;
    }

    @Override
    public boolean connect() throws RestException {
        userInfo = retrieveAccountInfo();

        return isConnected();
    }

    @Override
    public boolean isConnected() {
        return !StringUtils.isEmpty(accessToken) && userInfo != null && !userInfo.isDisabled();
    }

    @Override
    public boolean disconnect() {
        userInfo = null;
        return true;
    }

    private AccountInfo retrieveAccountInfo() throws RestException {
        URL url = getUrl(URL_ACCOUNT_INFO);
        JsonObjectRequest<AccountInfo> request = requestForPostUrlClassResponse(url, AccountInfo.class);

        try {
            return request.execute();
        } catch (IOException e) {
            throw new RestException(Messages.exception_rest_connection(), e);
        }
    }

    @Override
    public boolean changeWorkingDirectory(@Nonnull String relativePath) throws RestException {
        boolean hasSuccess = true;
        try {
            if (!StringUtils.isEmpty(relativePath)) {
                final Metadata metadata = retrieveMetaData(relativePath);
                if (metadata.isDir() && metadata instanceof FolderMetadata) {
                    workingFolder = (FolderMetadata) metadata;
                }
            }
        } catch (RestException e) {
            hasSuccess = false;
        }
        return hasSuccess && workingFolder != null && workingFolder.isDir();
    }

    @Override
    public FolderMetadata makeDirectory(@Nonnull String path) throws RestException {
        URL url = getUrl(URL_CREATE_FOLDER);
        FolderMetadata folder = null;
        String absolute = createAbsolutePath(path);

        try {
            Metadata metadata = retrieveMetaData(absolute);
            if (metadata.isDir()) {
                folder = (FolderMetadata) metadata;
            }
        } catch (RestException re) {
            folder = null;
        }

        if (folder == null) {
            CreateFolderRequest requestContent = new CreateFolderRequest();
            requestContent.setPath(absolute);
            JsonObjectRequest<FolderMetadata> request = requestPostRequestResponse(url, requestContent, FolderMetadata.class);
            try {
                folder = request.execute();
            } catch (IOException e) {
                throw new RestException(Messages.exception_dropbox_folder_create(path), e);
            }
        }

        return folder;
    }

    @Override
    public void cleanWorkingFolder() throws RestException {
        if (workingFolder.isDir()) {
            FolderContent contents = listFilesOfFolder(workingFolder);
            String cursor = null;
            do {
                if (cursor != null) {
                    contents = listFilesForCursor(cursor);
                }
                for (Metadata entry : contents.getEntries()) {
                    delete(entry);
                }
                if (contents.hasMore()) {
                    cursor = contents.getCursor();
                }
            } while (contents.hasMore() && cursor != null);

        } else {
            throw new RestException(Messages.exception_dropbox_folder_delete(workingFolder.getName()));
        }
    }

    void delete(@Nonnull Metadata metadata) throws RestException {
        final String path = metadata.getPathLower();
        delete(path);
    }

    void delete(@Nonnull String path) throws RestException {
        URL url = getUrl(URL_OPS_DELETE);
        DeleteRequest requestContent = new DeleteRequest();
        String absolute = createAbsolutePath(path);
        requestContent.setPath(absolute);
        if (StringUtils.isNotEmpty(path) && !"/".equals(path)) {
            JsonObjectRequest<Metadata> request = requestPostRequestResponse(url, requestContent, Metadata.class);
            try {
                request.execute();
            } catch (IOException e) {
                throw new RestException(Messages.exception_dropbox_folder_delete(path), e);
            }
        } else {
            throw new RestException(Messages.exception_dropbox_folder_delete(path));
        }
    }

    @Override
    public void pruneFolder(@Nonnull String path, int pruneRootDays) throws RestException {
        Date cutoff = new Date(System.currentTimeMillis() - pruneRootDays * MILLISECONDS_PER_DAY);
        String absolute = createAbsolutePath(path);
        FolderContent contents = listFilesOfPath(absolute);
        String cursor = null;
        do {
            if (cursor != null) {
                contents = listFilesForCursor(cursor);
            }
            for (Metadata entry : contents.getEntries()) {
                boolean isModifiedSince = isEntryModifiedSince(entry, cutoff);
                if (!isModifiedSince) {
                    delete(entry.getPathLower());
                }
            }
            // Paging of the listing
            cursor = contents.getCursor();
        }
        while (contents.hasMore() && cursor != null);
    }

    private boolean isEntryModifiedSince(@Nonnull Metadata metadata, @Nonnull Date cutoff) throws RestException {
        boolean isModifiedSince = false;
        if (metadata instanceof FileMetadata) {
            Date lastModified = parseDate(((FileMetadata) metadata).getServerModified());
            isModifiedSince = lastModified.after(cutoff);
        } else if (metadata instanceof FolderMetadata) {
            FolderContent contents = listFilesOfFolder((FolderMetadata) metadata);
            String cursor = null;
            contents:
            do {
                if (cursor != null) {
                    contents = listFilesForCursor(cursor);
                }
                List<Metadata> entries = contents.getEntries();
                // Evaluate file date first since that is available
                for (Metadata entry : entries) {
                    if (entry instanceof FileMetadata) {
                        isModifiedSince = isEntryModifiedSince(entry, cutoff);
                        if (isModifiedSince) {
                            break contents;
                        }
                    }
                }
                // Traverse the given folders after evaluating the files
                for (Metadata entry : entries) {
                    if (entry instanceof FolderMetadata) {
                        isModifiedSince = isEntryModifiedSince(entry, cutoff);
                        if (isModifiedSince) {
                            break contents;
                        }
                    }
                }
                // Paging of the listing
                cursor = contents.getCursor();
            }
            while (contents.hasMore() && cursor != null);
        }

        return isModifiedSince;
    }

    @VisibleForTesting
    Date parseDate(String serverModified) throws RestException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        try {
            return df.parse(serverModified);
        } catch (ParseException e) {
            throw new RestException(Messages.exception_dropbox_folder_prunedate(serverModified), e);
        }
    }

    /**
     * @param name    name of the new file to store the content in
     * @param content data stream of the content
     * @param length  content size in bytes
     */

    @Override
    public void storeFile(@Nonnull String name, @Nonnull InputStream content, long length) throws RestException {
        if (length <= chunkSize) {
            singleStore(name, content, length);
        } else {
            chunkedStore(name, content, length);
        }
    }

    /* *
     * Private helpers
     * */

    private FileMetadata singleStore(@Nonnull String name, @Nonnull InputStream content, long length) throws RestException {
        URL url = getUrl(URL_UPLOAD);
        UploadRequest uploadRequest = new UploadRequest();
        uploadRequest.setPath(createPath(name));
        JsonObjectRequest<FileMetadata> request = requestForUpload(url, uploadRequest, FileMetadata.class, content, length);
        final FileMetadata fileMetadata;
        try {
            fileMetadata = request.execute();
        } catch (IOException e) {
            throw new RestException(Messages.exception_dropbox_file_upload_simple(name), e);
        }

        return fileMetadata;
    }

    private void chunkedStore(String name, InputStream content, long length) throws RestException {
        // Start session
        InputStream chunkStream;
        long offSet = 0;
        URL startUrl = getUrl(URL_UPLOAD_START);
        chunkStream = new ChunkedInputStream(content, chunkSize);
        SessionStart startContent = new SessionStart();
        JsonObjectRequest<Session> startRequest = requestForUpload(startUrl, startContent, Session.class, chunkStream, chunkSize);
        Session session;
        try {
            session = startRequest.execute();
        } catch (IOException e) {
            throw new RestException(Messages.exception_store_chunk_end(name), e);
        }
        offSet += chunkSize;

        while (length - offSet > chunkSize) {
            // Add chunk to session
            URL appendUrl = getUrl(URL_UPLOAD_APPEND);
            chunkStream = new ChunkedInputStream(content, chunkSize);
            SessionAppend appendContent = new SessionAppend();
            appendContent.cursor.setOffset(offSet);
            appendContent.cursor.setSessionId(session.getSessionId());
            JsonObjectRequest<ErrorResponse> appendRequest = requestForUpload(appendUrl, appendContent, ErrorResponse.class, chunkStream, chunkSize);
            try {
                appendRequest.execute();
            } catch (IOException e) {
                throw new RestException(Messages.exception_store_chunk_continue(name), e);
            }
            offSet += chunkSize;
        }
        // Commit uploader
        URL finishUrl = getUrl(URL_UPLOAD_FINISH);
        chunkStream = new ChunkedInputStream(content, chunkSize);
        SessionFinish finishContent = new SessionFinish();
        finishContent.cursor.setSessionId(session.getSessionId());
        finishContent.cursor.setOffset(offSet);
        finishContent.commit.setPath(createPath(name));
        JsonObjectRequest<FileMetadata> finishRequest = requestForUpload(finishUrl, finishContent, FileMetadata.class, chunkStream, length - offSet);
        try {
            finishRequest.execute();
        } catch (IOException e) {
            throw new RestException(Messages.exception_store_chunk_end(name), e);
        }
    }

    @VisibleForTesting
    FolderContent listFilesOfFolder(@Nonnull FolderMetadata folder) throws RestException {
        return listFilesOfPath(folder.getPathLower());
    }

    private FolderContent listFilesOfPath(@Nonnull String path) throws RestException {
        URL url = getUrl(URL_LIST_FOLDER);
        ListFolderRequest requestContent = new ListFolderRequest();
        requestContent.setPath(path);
        JsonObjectRequest<FolderContent> request = requestPostRequestResponse(url, requestContent, FolderContent.class);

        final FolderContent content;
        try {
            content = request.execute();
        } catch (IOException e) {
            throw new RestException(Messages.exception_dropbox_folder_list(path), e);
        }

        return content;
    }

    private FolderContent listFilesForCursor(String cursor) throws RestException {
        URL url = getUrl(URL_LIST_FOLDER_CONTINUE);

        CursorRequest requestContent = new CursorRequest();
        requestContent.setCursor(cursor);
        JsonObjectRequest<FolderContent> request = requestPostRequestResponse(url, requestContent, FolderContent.class);

        final FolderContent content;
        try {
            content = request.execute();
        } catch (IOException e) {
            throw new RestException(Messages.exception_dropbox_folder_list_cursor(), e);
        }

        return content;
    }

    @VisibleForTesting
    Metadata retrieveMetaData(String relative) throws RestException {
        URL url = getUrl(URL_METADATA);
        MetadataRequest requestContent = new MetadataRequest();
        String absolute = createAbsolutePath(relative);
        requestContent.setPath(absolute);
        JsonObjectRequest<Metadata> request = requestPostRequestResponse(url, requestContent, Metadata.class);

        final Metadata metadata;
        try {
            metadata = request.execute();
        } catch (IOException e) {
            throw new RestException(Messages.exception_dropbox_file_metadata(relative), e);
        }

        return metadata;
    }

    private <T> JsonObjectRequest<T> requestForPostUrlClassResponse(URL url, Class<T> classOfT) {
        JsonObjectRequest.Builder<T> builder = new JsonObjectRequest.Builder<>();
        builder.url(url)
                .gson(gson)
                .method(POST)
                .responseClass(classOfT)
                .responseErrorClass(ErrorResponse.class)
                .sign(accessToken)
                .timeout(timeout);

        return builder.build();
    }

    private <T> JsonObjectRequest<T> requestPostRequestResponse(URL url, Object requestContent, Class<T> classOfT) {
        String content = gson.toJson(requestContent);
        JsonObjectRequest.Builder<T> builder = new JsonObjectRequest.Builder<>();
        builder.url(url)
                .gson(gson)
                .method(POST)
                .upload(content, APPLICATION_JSON)
                .responseClass(classOfT)
                .responseErrorClass(ErrorResponse.class)
                .sign(accessToken)
                .timeout(timeout);

        return builder.build();
    }

    private <T> JsonObjectRequest<T> requestForUpload(URL url, Object requestContent, Class<T> responseClass, @Nonnull InputStream content, long length) {
        JsonObjectRequest.Builder<T> builder = new JsonObjectRequest.Builder<T>()
                .url(url)
                .gson(gson)
                .method(POST)
                .upload(content, APPLICATION_OCTET_STREAM)
                .addHeader("Dropbox-API-Arg", gson.toJson(requestContent))
                .addHeader("Content-Length", Long.toString(length))
                .responseClass(responseClass)
                .responseErrorClass(ErrorResponse.class)
                .sign(accessToken)
                .timeout(timeout);

        return builder.build();
    }

    private static URL getUrl(String urlSource) throws RestException {
        URL url;
        try {
            url = new URLBuilder(urlSource).build();
        } catch (URISyntaxException | MalformedURLException e) {
            throw new RestException(Messages.exception_dropbox_url(), e);
        }

        return url;
    }

    private String createAbsolutePath(final String path) {
        StringBuilder sb = new StringBuilder();
        if (path.startsWith(PATH_SEPARATOR)) {
            // paths starting with / are already absolute
            sb.append(path);
        } else {
            // relative paths are prefix with the working folder
            if (workingFolder != null) {
                sb.append(workingFolder.getPathLower());
            }
            // When working folder is the root the path could end with '/'
            if (sb.length() == 0 || !PATH_SEPARATOR.equals(sb.substring(sb.length() - 1))) {
                sb.append(PATH_SEPARATOR);
            }
            sb.append(path);
        }

        return sb.toString();
    }

    private String createPath(String name) {
        String path;
        if (workingFolder.getPathLower().endsWith(PATH_SEPARATOR)) {
            path = workingFolder.getPathLower() + name;
        } else {
            path = workingFolder.getPathLower() + PATH_SEPARATOR + name;
        }
        return path;
    }

    /* *
     * Static token helpers
     * */

    public static Secret convertAuthorizationToAccessCode(Secret secretAuthorizationCode) throws RestException {
        String authorizationCode = Secret.toString(secretAuthorizationCode);
        if (StringUtils.isEmpty(authorizationCode)) {
            return Secret.fromString(null);
        }

        String accessToken = readAccessTokenFromProvider(authorizationCode);
        if (accessToken == null) {
            accessToken = readAccessTokenFromWeb(authorizationCode);
        }

        return Secret.fromString(accessToken);
    }

    private static String readAccessTokenFromWeb(String authorizationCode) throws RestException {
        String accessToken;
        URL url = getUrl(URL_TOKEN);
        FormBuilder formBuilder = new FormBuilder()
                .appendQueryParameter("code", authorizationCode)
                .appendQueryParameter("grant_type", VALUE_AUTHORIZATION_CODE)
                .appendQueryParameter("client_id", Config.CLIENT_ID);
        try {
            // Apply production config not included in source distribution
            Class privateConfig = Class.forName("org.jenkinsci.plugins.publishoverdropbox.domain.C");
            Class[] argClass = {formBuilder.getClass()};
            Method method = privateConfig.getDeclaredMethod("a", argClass);
            method.invoke(null, formBuilder);
        } catch (InvocationTargetException | NoSuchMethodException | ClassNotFoundException | IllegalAccessException e) {
            // Apply local development parameters
            formBuilder.appendQueryParameter("client_secret", Config.CLIENT_SECRET);
        }
        TokenResponse response;
        try {
            JsonObjectRequest<TokenResponse> request = new JsonObjectRequest.Builder<TokenResponse>()
                    .gson(new Gson())
                    .responseClass(TokenResponse.class)
                    .url(url)
                    .upload(formBuilder.build(), FormBuilder.CONTENT_TYPE)
                    .responseErrorClass(ErrorResponse.class)
                    .build();
            response = request.execute();
        } catch (IOException e) {
            throw new RestException(Messages.exception_accesstokens(), e);
        }
        accessToken = response.getAccessToken();

        return accessToken;
    }

    private static String readAccessTokenFromProvider(String authorizationCode) {
        String accessToken = null;
        List<DropboxToken> tokens = CredentialsProvider.lookupCredentials(DropboxToken.class, Jenkins.getInstance(), null, (DomainRequirement) null);
        for (DropboxToken token : tokens) {
            String providerAuthorizationCode = Secret.toString(token.getAuthorizationCode());
            if (providerAuthorizationCode.equals(authorizationCode)) {
                accessToken = Secret.toString(token.getAccessCode());
            }
        }

        return accessToken;
    }

    Metadata getWorkingFolder() {
        return this.workingFolder;
    }
}
