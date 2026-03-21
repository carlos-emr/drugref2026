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
<%@page import="java.util.*,io.github.carlos_emr.drugref2026.ca.dpd.*"  %><%
Enumeration en = request.getParameterNames();
while(en.hasMoreElements()){
    System.out.println(">"+en.nextElement());
 }

String searchStr = request.getParameter("query");
if (searchStr == null){
    searchStr = request.getParameter("name");
    }
System.out.println("searc "+searchStr);
TablesDao queryDao = new TablesDao();
System.out.println("CALLING listSearchElement3");
Vector<Hashtable> vec=queryDao.listSearchElement3(searchStr);
System.out.println("VEC "+vec.size());

if ( request.getParameter("name") !=null ){
    for (Hashtable h: vec){
        Integer id = (Integer) h.get("id");
        System.out.println("querd "+queryDao.getSearchedDrug(id));
       out.write( h.get("name") +"\n");
    }
}else{
Hashtable d = new Hashtable();
d.put("results",vec);
response.setContentType("text/x-json");
out.write(d.toString());
}
%>