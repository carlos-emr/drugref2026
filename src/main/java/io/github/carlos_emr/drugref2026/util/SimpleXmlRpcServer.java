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

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Vector;

import org.apache.logging.log4j.Logger;

/**
 * Minimal XML-RPC 1.0 server that dispatches method calls to a handler object
 * via reflection. Replaces the removed {@code org.apache.xmlrpc.XmlRpcServer}.
 *
 * <p>The handler's public methods are the XML-RPC endpoints. Method names in
 * the XML-RPC request are matched to Java method names on the handler. Parameter
 * types are matched by count and the handler method is invoked via reflection.</p>
 */
public class SimpleXmlRpcServer {

    private static final Logger logger = MiscUtils.getLogger();

    private final Object handler;

    /**
     * Creates a server that dispatches XML-RPC calls to the given handler object.
     *
     * @param handler the object whose public methods serve as XML-RPC endpoints
     */
    public SimpleXmlRpcServer(Object handler) {
        this.handler = handler;
    }

    /**
     * Reads an XML-RPC request from the input stream, invokes the named method
     * on the handler, and writes the XML-RPC response to the output stream.
     *
     * @param in  the request input stream
     * @param out the response output stream
     */
    public void execute(InputStream in, OutputStream out) {
        String responseXml;
        try {
            String requestXml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Object[] parsed = XmlRpcUtils.parseRequest(requestXml);
            String methodName = (String) parsed[0];
            @SuppressWarnings("unchecked")
            Vector<Object> params = (Vector<Object>) parsed[1];

            logger.debug("XML-RPC call: " + methodName + " with " + params.size() + " params");

            Object result = invokeMethod(methodName, params);
            responseXml = XmlRpcUtils.buildResponse(result);
        } catch (XmlRpcFaultException e) {
            responseXml = XmlRpcUtils.buildFaultResponse(e.code, e.getMessage());
        } catch (Exception e) {
            logger.error("XML-RPC execution error", e);
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            responseXml = XmlRpcUtils.buildFaultResponse(0, message);
        }

        try {
            byte[] bytes = responseXml.getBytes(StandardCharsets.UTF_8);
            out.write(bytes);
            out.flush();
        } catch (Exception e) {
            logger.error("Failed to write XML-RPC response", e);
        }
    }

    /**
     * Invokes the named method on the handler object via reflection.
     * Matches by method name and parameter count, then attempts type coercion
     * for common XML-RPC to Java type mismatches (e.g., Integer to int, String to int).
     */
    private Object invokeMethod(String methodName, Vector<Object> params) throws Exception {
        Method[] methods = handler.getClass().getMethods();

        for (Method method : methods) {
            if (!method.getName().equals(methodName)) continue;
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length != params.size()) continue;

            Object[] args = new Object[paramTypes.length];
            boolean match = true;
            for (int i = 0; i < paramTypes.length; i++) {
                args[i] = coerce(params.get(i), paramTypes[i]);
                if (args[i] == null && paramTypes[i].isPrimitive()) {
                    match = false;
                    break;
                }
            }

            if (match) {
                try {
                    Object result = method.invoke(handler, args);
                    return result;
                } catch (java.lang.reflect.InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof Exception ex) throw ex;
                    throw e;
                }
            }
        }

        throw new XmlRpcFaultException(0, "No method '" + methodName + "' with "
                + params.size() + " parameter(s) found on handler " + handler.getClass().getName());
    }

    /**
     * Coerces an XML-RPC value to the expected Java parameter type.
     * Handles common conversions like Integer to int/long, String to int, etc.
     */
    private Object coerce(Object value, Class<?> targetType) {
        if (value == null) return null;

        // Direct assignability
        if (targetType.isInstance(value)) return value;

        // Primitive type handling
        if (targetType == int.class || targetType == Integer.class) {
            if (value instanceof Integer i) return i;
            if (value instanceof String s) return Integer.parseInt(s);
        }
        if (targetType == long.class || targetType == Long.class) {
            if (value instanceof Integer i) return (long) i;
            if (value instanceof String s) return Long.parseLong(s);
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (value instanceof Boolean b) return b;
            if (value instanceof String s) return Boolean.parseBoolean(s);
        }
        if (targetType == double.class || targetType == Double.class) {
            if (value instanceof Double d) return d;
            if (value instanceof Integer i) return (double) i;
            if (value instanceof String s) return Double.parseDouble(s);
        }
        if (targetType == String.class) {
            return value.toString();
        }

        // Vector/Hashtable pass through (XML-RPC arrays/structs)
        if (targetType == Vector.class && value instanceof Vector) return value;
        if (targetType == java.util.Hashtable.class && value instanceof java.util.Hashtable) return value;

        return value;
    }
}
