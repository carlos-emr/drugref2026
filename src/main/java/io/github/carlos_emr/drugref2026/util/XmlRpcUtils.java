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

import java.io.StringReader;
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
 * Shared XML-RPC serialization/deserialization utilities used by both
 * {@link SimpleXmlRpcClient} and {@link SimpleXmlRpcServer}.
 */
public final class XmlRpcUtils {

    private static final DateTimeFormatter[] DATETIME_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    };

    private XmlRpcUtils() {}

    /** Escapes the five XML special characters. */
    public static String escapeXml(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Recursively serializes a Java value into an XML-RPC value element. */
    @SuppressWarnings("unchecked")
    public static void serializeValue(StringBuilder sb, Object value) {
        sb.append("<value>");
        if (value == null) {
            sb.append("<string></string>");
        } else if (value instanceof String s) {
            sb.append("<string>").append(escapeXml(s)).append("</string>");
        } else if (value instanceof Integer i) {
            sb.append("<int>").append(i).append("</int>");
        } else if (value instanceof Boolean b) {
            sb.append("<boolean>").append(b ? "1" : "0").append("</boolean>");
        } else if (value instanceof Double d) {
            sb.append("<double>").append(d).append("</double>");
        } else if (value instanceof Date date) {
            LocalDateTime ldt = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
            sb.append("<dateTime.iso8601>").append(DATETIME_FORMATTERS[0].format(ldt)).append("</dateTime.iso8601>");
        } else if (value instanceof byte[] bytes) {
            sb.append("<base64>").append(Base64.getEncoder().encodeToString(bytes)).append("</base64>");
        } else if (value instanceof Vector v) {
            sb.append("<array><data>");
            for (int i = 0; i < v.size(); i++) {
                serializeValue(sb, v.get(i));
            }
            sb.append("</data></array>");
        } else if (value instanceof Hashtable ht) {
            sb.append("<struct>");
            var keys = ht.keys();
            while (keys.hasMoreElements()) {
                Object key = keys.nextElement();
                sb.append("<member><name>").append(escapeXml(key.toString())).append("</name>");
                serializeValue(sb, ht.get(key));
                sb.append("</member>");
            }
            sb.append("</struct>");
        } else {
            sb.append("<string>").append(escapeXml(value.toString())).append("</string>");
        }
        sb.append("</value>");
    }

    /** Creates a DocumentBuilderFactory with XXE protection enabled. */
    public static DocumentBuilderFactory createSafeDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory;
    }

    /** Parses an XML-RPC response body, returning the result value or throwing on fault. */
    public static Object parseResponse(String responseXml) throws Exception {
        DocumentBuilderFactory factory = createSafeDocumentBuilderFactory();
        Document doc = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(responseXml)));

        Element root = doc.getDocumentElement();

        NodeList faultList = root.getElementsByTagName("fault");
        if (faultList.getLength() > 0) {
            Element faultValue = getFirstChildElement(faultList.item(0));
            @SuppressWarnings("unchecked")
            Hashtable<String, Object> fault = (Hashtable<String, Object>) parseValue(faultValue);
            int code = fault.get("faultCode") instanceof Integer i ? i : 0;
            String msg = fault.get("faultString") instanceof String s ? s : "Unknown fault";
            throw new XmlRpcFaultException(code, msg);
        }

        NodeList paramList = root.getElementsByTagName("param");
        if (paramList.getLength() > 0) {
            Element valueElem = getFirstChildElement(paramList.item(0));
            return parseValue(valueElem);
        }

        return null;
    }

    /** Parses an XML-RPC request body, returning method name and params. */
    public static Object[] parseRequest(String requestXml) throws Exception {
        DocumentBuilderFactory factory = createSafeDocumentBuilderFactory();
        Document doc = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(requestXml)));

        Element root = doc.getDocumentElement();

        NodeList methodNameList = root.getElementsByTagName("methodName");
        String methodName = methodNameList.item(0).getTextContent();

        Vector<Object> params = new Vector<>();
        NodeList paramList = root.getElementsByTagName("param");
        for (int i = 0; i < paramList.getLength(); i++) {
            Element valueElem = getFirstChildElement(paramList.item(i));
            params.add(parseValue(valueElem));
        }

        return new Object[]{methodName, params};
    }

    /** Deserializes a single XML-RPC value node into its Java equivalent. */
    public static Object parseValue(Node valueNode) throws Exception {
        Element child = getFirstChildElement(valueNode);
        if (child == null) {
            return valueNode.getTextContent();
        }

        String tag = child.getTagName();
        String text = child.getTextContent();

        return switch (tag) {
            case "string" -> text;
            case "int", "i4" -> {
                try {
                    yield Integer.parseInt(text);
                } catch (NumberFormatException e) {
                    throw new XmlRpcFaultException(0, "Invalid <" + tag + "> value: '" + text + "'");
                }
            }
            case "boolean" -> "1".equals(text);
            case "double" -> {
                try {
                    yield Double.parseDouble(text);
                } catch (NumberFormatException e) {
                    throw new XmlRpcFaultException(0, "Invalid <double> value: '" + text + "'");
                }
            }
            case "dateTime.iso8601" -> parseDateTimeIso8601(text);
            case "base64" -> Base64.getDecoder().decode(text);
            case "array" -> parseArray(child);
            case "struct" -> parseStruct(child);
            default -> text;
        };
    }

    private static Date parseDateTimeIso8601(String text) throws XmlRpcFaultException {
        for (DateTimeFormatter fmt : DATETIME_FORMATTERS) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(text, fmt);
                return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new XmlRpcFaultException(0, "Cannot parse dateTime.iso8601 value: '" + text + "'");
    }

    private static Vector<Object> parseArray(Element arrayElem) throws Exception {
        Vector<Object> vec = new Vector<>();
        NodeList dataNodes = arrayElem.getElementsByTagName("data");
        if (dataNodes.getLength() > 0) {
            Node data = dataNodes.item(0);
            for (Node n = data.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n.getNodeType() == Node.ELEMENT_NODE && "value".equals(n.getNodeName())) {
                    vec.add(parseValue(n));
                }
            }
        }
        return vec;
    }

    private static Hashtable<String, Object> parseStruct(Element structElem) throws Exception {
        Hashtable<String, Object> ht = new Hashtable<>();
        for (Node n = structElem.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && "member".equals(n.getNodeName())) {
                String name = null;
                Object value = null;
                for (Node c = n.getFirstChild(); c != null; c = c.getNextSibling()) {
                    if (c.getNodeType() != Node.ELEMENT_NODE) continue;
                    if ("name".equals(c.getNodeName())) {
                        name = c.getTextContent();
                    } else if ("value".equals(c.getNodeName())) {
                        value = parseValue(c);
                    }
                }
                if (name != null && value != null) {
                    ht.put(name, value);
                }
            }
        }
        return ht;
    }

    /** Returns the first child Element of the given node, or null if none exist. */
    public static Element getFirstChildElement(Node node) {
        for (Node n = node.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) n;
            }
        }
        return null;
    }

    /** Builds an XML-RPC response envelope for a successful result. */
    public static String buildResponse(Object result) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\"?>");
        sb.append("<methodResponse><params><param>");
        serializeValue(sb, result);
        sb.append("</param></params></methodResponse>");
        return sb.toString();
    }

    /** Builds an XML-RPC fault response envelope. */
    public static String buildFaultResponse(int faultCode, String faultString) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\"?>");
        sb.append("<methodResponse><fault><value><struct>");
        sb.append("<member><name>faultCode</name><value><int>").append(faultCode).append("</int></value></member>");
        sb.append("<member><name>faultString</name><value><string>").append(escapeXml(faultString)).append("</string></value></member>");
        sb.append("</struct></value></fault></methodResponse>");
        return sb.toString();
    }
}
