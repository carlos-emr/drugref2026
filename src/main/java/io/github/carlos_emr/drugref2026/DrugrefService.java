/*
 * Copyright (c) 2026 CARLOS EMR Project Contributors. All Rights Reserved.
 *
 * Originally: Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
package io.github.carlos_emr.drugref2026;

import java.io.IOException;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.drugref2026.util.SimpleXmlRpcServer;
import io.github.carlos_emr.drugref2026.util.MiscUtils;
/**
 * HTTP servlet that serves as the XML-RPC endpoint for the drugref2026 service.
 *
 * <p>This servlet receives XML-RPC POST requests from EMR clients (e.g., OSCAR/CARLOS),
 * deserializes them, dispatches method calls to the {@link Drugref} handler class via
 * {@link SimpleXmlRpcServer}, and returns the XML-RPC response.
 *
 * <p>Configured in {@code web.xml} and mapped to the drugref service URL. Only the
 * HTTP POST method is supported, as XML-RPC is a POST-only protocol.
 *
 * @author jaygallagher
 */
public class DrugrefService extends HttpServlet {

    /** The XML-RPC server that deserializes requests and invokes methods on the Drugref handler. */
    private SimpleXmlRpcServer xmlrpc;
    private static Logger logger = MiscUtils.getLogger();

    /**
     * Initializes the servlet by creating a {@link SimpleXmlRpcServer} backed by
     * a new {@link Drugref} instance. Called once by the servlet container on first load.
     *
     * @param config the servlet configuration provided by the container
     * @throws ServletException if initialization fails
     */
    public void init(ServletConfig config) throws ServletException {
        logger.debug("HERE-INIT");
        xmlrpc = new SimpleXmlRpcServer(new Drugref());
    }
    /**
     * Handles the HTTP POST method by delegating to the XML-RPC server.
     *
     * <p>Reads the XML-RPC request from the input stream, invokes the appropriate
     * method on the {@link Drugref} handler, and writes the XML-RPC response.
     *
     * @param request servlet request containing the XML-RPC payload
     * @param response servlet response where the XML-RPC result is written
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs during request/response processing
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        logger.debug("HERE-POST");
        response.setContentType("text/xml");
        xmlrpc.execute(request.getInputStream(), response.getOutputStream());
    }
}
