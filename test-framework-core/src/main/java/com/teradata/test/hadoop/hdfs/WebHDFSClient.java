/*
 * Copyright 2013-2015, Teradata, Inc. All rights reserved.
 */

package com.teradata.test.hadoop.hdfs;

import com.google.common.net.HostAndPort;
import com.jayway.jsonpath.JsonPath;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.net.HostAndPort.fromParts;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_TEMPORARY_REDIRECT;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * HDFS client based on WebHDFS REST API.
 */
public class WebHDFSClient
        implements HdfsClient
{

    private static final Logger logger = getLogger(WebHDFSClient.class);

    private static final JsonPath GETFILESTATUS_LENGTH_JON_PATH = JsonPath.compile("$.FileStatus.length");

    private final HostAndPort nameNode;

    private final CloseableHttpClient httpClient;

    @Inject
    public WebHDFSClient(
            @Named("hdfs.webhdfs.host") String webHdfsNameNodeHost,
            @Named("hdfs.webhdfs.port") int webHdfsNameNodePort)
    {
        this.nameNode = fromParts(checkNotNull(webHdfsNameNodeHost), webHdfsNameNodePort);
        checkArgument(webHdfsNameNodePort > 0, "Invalid name node WebHDFS port number: %s", webHdfsNameNodePort);

        this.httpClient = HttpClients.createDefault();
    }

    @PreDestroy
    public void closeHttpClient()
            throws IOException
    {
        httpClient.close();
    }

    @Override
    public void createDirectory(String path, String username)
    {
        HttpPut mkdirRequest = new HttpPut(buildUri(path, username, "MKDIRS"));
        try (CloseableHttpResponse response = httpClient.execute(mkdirRequest)) {
            if (response.getStatusLine().getStatusCode() != SC_OK) {
                throw invalidStatusException("MKDIRS", path, username, mkdirRequest, response);
            }
            logger.debug("Created directory {} - username: {}", path, username);
        }
        catch (IOException e) {
            throw new RuntimeException("Could not save file " + path + " in hdfs, user: " + username, e);
        }
    }

    @Override
    public void saveFile(String path, String username, InputStream input)
    {
        String writeRedirectUri = executeAndGetRedirectUri(new HttpPut(buildUri(path, username, "CREATE")));
        HttpPut writeRequest = new HttpPut(writeRedirectUri);
        writeRequest.setEntity(new InputStreamEntity(input));

        try (CloseableHttpResponse response = httpClient.execute(writeRequest)) {
            if (response.getStatusLine().getStatusCode() != SC_CREATED) {
                throw invalidStatusException("CREATE", path, username, writeRequest, response);
            }
            logger.debug("Save file {} - username: {}", path, username);
        }
        catch (IOException e) {
            throw new RuntimeException("Could not save file " + path + " in hdfs, user: " + username, e);
        }
    }

    @Override
    public void loadFile(String path, String username, OutputStream outputStream)
    {
        HttpGet readRequest = new HttpGet(buildUri(path, username, "OPEN"));
        try (CloseableHttpResponse response = httpClient.execute(readRequest)) {
            if (response.getStatusLine().getStatusCode() != SC_OK) {
                throw invalidStatusException("OPEN", path, username, readRequest, response);
            }

            IOUtils.copy(response.getEntity().getContent(), outputStream);

            logger.debug("Loaded file {} - username: {}", path, username);
        }
        catch (IOException e) {
            throw new RuntimeException("Could not read file " + path + " in hdfs, user: " + username, e);
        }
    }

    @Override
    public long getLength(String path, String username)
    {
        HttpGet readRequest = new HttpGet(buildUri(path, username, "GETFILESTATUS"));
        try (CloseableHttpResponse response = httpClient.execute(readRequest)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == SC_NOT_FOUND) {
                return -1;
            }
            else if (statusCode != SC_OK) {
                throw invalidStatusException("GETFILESTATUS", path, username, readRequest, response);
            }

            return ((Integer) GETFILESTATUS_LENGTH_JON_PATH.read(response.getEntity().getContent())).longValue();
        }
        catch (IOException e) {
            throw new RuntimeException("Could not read file " + path + " in hdfs, user: " + username, e);
        }
    }

    private String executeAndGetRedirectUri(HttpUriRequest request)
    {
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            if (response.getStatusLine().getStatusCode() != SC_TEMPORARY_REDIRECT) {
                throw new RuntimeException("Expected redirect for request: " + request);
            }
            return response.getFirstHeader("Location").getValue();
        }
        catch (IOException e) {
            throw new RuntimeException("Could not execute request " + request, e);
        }
    }

    private URI buildUri(String path, String username, String operation)
    {
        try {
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            return new URIBuilder()
                    .setScheme("http")
                    .setHost(nameNode.getHostText())
                    .setPort(nameNode.getPort())
                    .setPath("/webhdfs/v1" + checkNotNull(path))
                    .setParameter("op", checkNotNull(operation))
                    .setParameter("user.name", checkNotNull(username))
                    .setParameter("overwrite", "true")
                    .build();
        }
        catch (URISyntaxException e) {
            throw new RuntimeException("Could not create save file URI" +
                    ", nameNode: " + nameNode +
                    ", path: " + path +
                    ", username: " + username);
        }
    }

    private RuntimeException invalidStatusException(String operation, String path, String username, HttpRequest request, HttpResponse response)
            throws IOException
    {
        return new RuntimeException("Operation " + operation +
                " on file " + path + " failed, user: " + username +
                ", status: " + response.getStatusLine().getStatusCode() + " " + response.getStatusLine().getReasonPhrase() +
                ", content: " + IOUtils.toString(response.getEntity().getContent()) +
                ", request: " + request.getRequestLine().getMethod() + " " + request.getRequestLine().getUri());
    }
}