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

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.xml.sax.SAXParseException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SimpleXmlRpcClient}.
 * Adapted from the carlos-emr/carlos project's SimpleXmlRpcClientTest.
 */
@DisplayName("SimpleXmlRpcClient")
class SimpleXmlRpcClientTest {

    private HttpServer mockServer;
    private SimpleXmlRpcClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockServer.start();
        int port = mockServer.getAddress().getPort();
        client = new SimpleXmlRpcClient("http://localhost:" + port);
    }

    @AfterEach
    void tearDown() {
        mockServer.stop(0);
    }

    private void serveXml(String responseBody) {
        mockServer.createContext("/", exchange -> {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });
    }

    private void serveHttpStatus(int statusCode) {
        mockServer.createContext("/", exchange -> {
            exchange.sendResponseHeaders(statusCode, -1);
            exchange.getResponseBody().close();
        });
    }

    @Test
    @DisplayName("should throw exception when response contains XXE DOCTYPE declaration")
    void shouldThrowException_whenResponseContainsXxeDoctype() {
        String xxePayload = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<methodResponse><params><param><value>&xxe;</value></param></params></methodResponse>";
        serveXml(xxePayload);

        assertThatThrownBy(() -> client.execute("test", new Vector()))
                .isInstanceOf(SAXParseException.class);
    }

    @Test
    @DisplayName("should throw XmlRpcFaultException with correct code when server returns fault")
    void shouldThrowXmlRpcFaultException_whenServerReturnsFault() {
        String faultResponse = "<?xml version=\"1.0\"?>"
                + "<methodResponse><fault><value><struct>"
                + "<member><name>faultCode</name><value><int>42</int></value></member>"
                + "<member><name>faultString</name><value><string>Drug not found</string></value></member>"
                + "</struct></value></fault></methodResponse>";
        serveXml(faultResponse);

        assertThatThrownBy(() -> client.execute("get_drug_by_DIN", new Vector()))
                .isInstanceOf(XmlRpcFaultException.class)
                .satisfies(e -> assertThat(((XmlRpcFaultException) e).code).isEqualTo(42))
                .hasMessageContaining("Drug not found");
    }

    @Test
    @DisplayName("should return Vector when response contains an array")
    void shouldReturnVector_whenResponseContainsArray() throws Exception {
        String arrayResponse = "<?xml version=\"1.0\"?>"
                + "<methodResponse><params><param><value>"
                + "<array><data>"
                + "<value><string>aspirin</string></value>"
                + "<value><string>ibuprofen</string></value>"
                + "</data></array>"
                + "</value></param></params></methodResponse>";
        serveXml(arrayResponse);

        Object result = client.execute("atc", new Vector());

        assertThat(result).isInstanceOf(Vector.class);
        @SuppressWarnings("unchecked")
        Vector<Object> vec = (Vector<Object>) result;
        assertThat(vec).containsExactly("aspirin", "ibuprofen");
    }

    @Test
    @DisplayName("should return Hashtable when response contains a struct")
    void shouldReturnHashtable_whenResponseContainsStruct() throws Exception {
        String structResponse = "<?xml version=\"1.0\"?>"
                + "<methodResponse><params><param><value>"
                + "<struct>"
                + "<member><name>din</name><value><string>12345</string></value></member>"
                + "<member><name>count</name><value><int>1</int></value></member>"
                + "</struct>"
                + "</value></param></params></methodResponse>";
        serveXml(structResponse);

        Object result = client.execute("get_drug", new Vector());

        assertThat(result).isInstanceOf(Hashtable.class);
        Hashtable<?, ?> ht = (Hashtable<?, ?>) result;
        assertThat(ht.get("din")).isEqualTo("12345");
        assertThat(ht.get("count")).isEqualTo(1);
    }

    @Test
    @DisplayName("should throw IOException when server returns non-200 HTTP status")
    void shouldThrowIoException_whenServerReturnsNonOkHttpStatus() {
        serveHttpStatus(503);

        assertThatThrownBy(() -> client.execute("atc", new Vector()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("503");
    }
}
