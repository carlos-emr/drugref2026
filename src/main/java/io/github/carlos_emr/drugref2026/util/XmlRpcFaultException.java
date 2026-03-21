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

/**
 * Exception thrown when an XML-RPC server returns a fault response.
 *
 * <p>Replaces {@code org.apache.xmlrpc.XmlRpcException} from the removed
 * xmlrpc:xmlrpc:2.0.1 dependency. The public {@link #code} field preserves
 * the same API contract used by callers that inspect fault codes.</p>
 */
public class XmlRpcFaultException extends Exception {

    private static final long serialVersionUID = 1L;

    /** The XML-RPC fault code returned by the server. */
    public final int code;

    /**
     * Creates a new XML-RPC fault exception.
     *
     * @param code    int the fault code from the XML-RPC fault response
     * @param message String the fault string from the XML-RPC fault response
     */
    public XmlRpcFaultException(int code, String message) {
        super(message);
        this.code = code;
    }
}
