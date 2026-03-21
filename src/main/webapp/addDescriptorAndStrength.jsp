<%--
  Copyright (c) 2026 CARLOS EMR Project Contributors. All Rights Reserved.

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU Affero General Public License as
  published by the Free Software Foundation, either version 3 of the
  License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  GNU Affero General Public License for more details.

  You should have received a copy of the GNU Affero General Public License
  along with this program. If not, see <https://www.gnu.org/licenses/>.
--%>
<%@page contentType="text/html" pageEncoding="MacRoman"%>
<%@page import="java.util.*" %>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
   "http://www.w3.org/TR/html4/loose.dtd">

<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=MacRoman">
        <title>JSP Page</title>
    </head>
    <body>
        <h1>Added Descriptor and Strength</h1>
         <%
        io.github.carlos_emr.drugref2026.ca.dpd.fetch.DPDImport dpdImport = new io.github.carlos_emr.drugref2026.ca.dpd.fetch.DPDImport();
        List addedDescriptor=dpdImport.addDescriptorToSearchName();
        List addedStrength=dpdImport.addStrengthToBrandName();
        %>
        <div  style="color: blue;" >Number of drug names being added descriptor: <a><%=addedDescriptor.size()%></a></div>
        <br><div id="des" style="display:none">ids in cd_drug_search <%=addedDescriptor%></div>
        <br><div style="color: blue;">Number of drug names being added strength: <a><%=addedStrength.size()%></a></div>
        <br><div id="str" style="display:none">ids in cd_drug_search <%=addedStrength%></div>
    </body>
</html>
