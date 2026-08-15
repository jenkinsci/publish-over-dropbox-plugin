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

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import hudson.ProxyConfiguration;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.publishoverdropbox.impl.Messages;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

class JsonObjectRequest<T> {

    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String UTF_8 = "UTF-8";
    private static final String OCTET_STREAM = "application/octet-stream";
    private static final String PARAM_AUTHORIZATION = "Authorization";
    private static final String VALUE_BEARER = "Bearer ";
    private static final int TIMEOUT_30_SECONDS = 30000;

    private URL url;
    private InputStream bodyStream;
    private String contentType;
    private Gson gson;
    private Class<T> classOfT;
    private String bearerToken;
    private int timeout = TIMEOUT_30_SECONDS;
    private Map<String, String> headers = new HashMap<>();
    private Class classOfError;
    private Method method = Method.GET;

    enum Method {POST, GET, PUT, DELETE}

    static class Builder<T> {
        JsonObjectRequest<T> request;

        public Builder() {
            request = new JsonObjectRequest<>();
        }

        public Builder<T> url(URL url) {
            request.url = url;
            return this;
        }

        public Builder<T> gson(Gson gson) {
            request.gson = gson;
            return this;
        }

        public Builder<T> responseClass(Class<T> classOfT) {
            request.classOfT = classOfT;
            return this;
        }

        public Builder<T> responseErrorClass(Class classOfError) {
            request.classOfError = classOfError;
            return this;
        }

        public Builder<T> upload(InputStream content, String contentType) {
            request.contentType = contentType;
            request.bodyStream = content;
            return this;
        }

        public Builder<T> upload(String content, String contentType) {
            request.contentType = contentType;
            try {
                request.bodyStream = new ByteArrayInputStream(content.getBytes(UTF_8));
            } catch (UnsupportedEncodingException e) {
                // Ignored
            }
            return this;
        }

        public Builder<T> addHeader(@NonNull String key, @NonNull String value) {
            request.headers.put(key, value);
            return this;
        }

        public Builder<T> method(Method method) {
            request.method = method;
            return this;
        }

        public Builder<T> sign(String accessCode) {
            request.bearerToken = accessCode;
            return this;
        }

        public Builder<T> timeout(int timeout) {
            if (timeout > 1000) {
                request.timeout = timeout;
            } else {
                request.timeout = TIMEOUT_30_SECONDS;
            }
            return this;
        }

        public JsonObjectRequest<T> build() {
            return request;
        }
    }

    private JsonObjectRequest() {
    }

    public T execute() throws IOException {
        T model = null;
        HttpURLConnection connection;
        InputStream inputStream = null;
        InputStream errorStream = null;
        try {
            // Prepare
            connection = openConnection(url);
            connection.setReadTimeout(timeout);
            connection.setConnectTimeout(timeout);
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                final String asciiKey = httpHeaderEncode(entry.getKey());
                final String asciiHeader = httpHeaderEncode(entry.getValue());
                connection.addRequestProperty(asciiKey, asciiHeader);
            }
            if (bearerToken != null) {
                signWithBearerToken(connection);
            }
            connection.setRequestMethod(method.name());
            connection.setDoOutput(false);
            boolean responseBody = method == Method.GET || method == Method.POST;
            connection.setDoInput(responseBody);

            // Upload
            if (bodyStream != null) {
                connection.setDoOutput(true);
                upload(connection);
            }

            // Response
            int responseCode = connection.getResponseCode();
            String responseMessage = connection.getResponseMessage();
            if (responseCode < 200 || responseCode > 299) {
                errorStream = connection.getErrorStream();
                Object errorResponse;
                if (classOfError != null) {
                    errorResponse = IOUtils.toString(errorStream);
                    try {
                        errorResponse = readModel(gson, (String) errorResponse, classOfError);
                    } catch (JsonSyntaxException ignored) {
                    }
                } else {
                    errorResponse = IOUtils.toString(errorStream);
                }
                throw new IOException(errorResponse.toString(), new IOException(Messages.exception_http(responseCode, responseMessage)));
            }

            // Download
            if (responseBody) {
                inputStream = connection.getInputStream();
                model = readModel(gson, inputStream, classOfT);
            }
        } finally {
            closeQuietly(errorStream);
            closeQuietly(inputStream);
        }

        return model;
    }

    private HttpURLConnection openConnection(URL url) throws IOException {
        return (HttpURLConnection) ProxyConfiguration.open(url);

    }

    public static String httpHeaderEncode(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            final char charAt = text.charAt(i);
            if (charAt >= '\u007f') {
                sb.append(String.format("\\u%04X", (int) charAt));
            } else {
                sb.append(charAt);
            }
        }

        return sb.toString();
    }

    private void upload(HttpURLConnection connection) throws IOException {
        if (contentType != null) {
            connection.addRequestProperty(HEADER_CONTENT_TYPE, contentType);
        } else {
            // Leaving content type null will result in malformed requests, not setting it will result in an incorrect value
            connection.addRequestProperty(HEADER_CONTENT_TYPE, OCTET_STREAM);
        }
        DataOutputStream outputStream = null;
        try {
            OutputStream stream = connection.getOutputStream();
            outputStream = new DataOutputStream(stream);
            IOUtils.copyLarge(bodyStream, outputStream);
            outputStream.flush();
        } finally {
            closeQuietly(bodyStream);
            closeQuietly(outputStream);
        }
    }

    private void signWithBearerToken(HttpURLConnection connection) {
        connection.setRequestProperty(PARAM_AUTHORIZATION, VALUE_BEARER + bearerToken);
    }

    private static <MODEL> MODEL readModel(Gson gson, InputStream inputStream, Class<MODEL> classOfModel) throws IOException {
        MODEL model = null;
        if (inputStream != null && classOfModel != null) {
            InputStreamReader reader = null;
            try {
                reader = new InputStreamReader(new BufferedInputStream(inputStream, 4096), UTF_8);
                model = gson.fromJson(reader, classOfModel);
            } finally {
                closeQuietly(reader);
            }
        }
        return model;
    }

    private static <MODEL> MODEL readModel(Gson gson, String jsonData, Class<MODEL> classOfModel) {
        MODEL model = null;
        if (jsonData != null) {
            model = gson.fromJson(jsonData, classOfModel);
        }
        return model;
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                //Ignored
            }
        }
    }
}
