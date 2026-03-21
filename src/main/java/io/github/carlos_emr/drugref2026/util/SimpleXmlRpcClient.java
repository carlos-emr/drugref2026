/*
 * Copyright (c) 2026 CARLOS EMR Project Contributors. All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.carlos_emr.drugref2026.util;

import java.io.IOException;
import java.io.StringReader;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Minimal XML-RPC 1.0 client using Java 21's built-in {@link HttpClient}.
 *
 * <p>Adapted from the carlos-emr/carlos project's SimpleXmlRpcClient.
 * Replaces the removed {@code xmlrpc:xmlrpc:2.0.1} dependency.</p>
 */
public class SimpleXmlRpcClient {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .proxy(ProxySelector.getDefault())
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private static final DateTimeFormatter[] DATETIME_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    };

    private final String serverUrl;

    public SimpleXmlRpcClient(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public Object execute(String methodName, Vector params) throws Exception {
        String requestXml = buildRequest(methodName, params);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl))
                .header("Content-Type", "text/xml")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestXml))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("XML-RPC server returned HTTP " + response.statusCode()
                    + " for '" + methodName + "' at " + serverUrl);
        }
        return XmlRpcUtils.parseResponse(response.body());
    }

    private String buildRequest(String methodName, Vector params) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\"?>");
        sb.append("<methodCall><methodName>").append(XmlRpcUtils.escapeXml(methodName)).append("</methodName>");
        sb.append("<params>");
        for (int i = 0; i < params.size(); i++) {
            sb.append("<param>");
            XmlRpcUtils.serializeValue(sb, params.get(i));
            sb.append("</param>");
        }
        sb.append("</params></methodCall>");
        return sb.toString();
    }
}
