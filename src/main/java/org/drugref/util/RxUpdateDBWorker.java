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
package org.drugref.util;

import java.util.HashMap;
import java.util.List;
import org.drugref.Drugref;
import org.drugref.ca.dpd.fetch.DPDImport;
import org.drugref.ca.dpd.fetch.TempNewGenericImport;
import org.drugref.ca.dpd.history.HistoryUtil;

/**
 *
 * @author jackson
 */
public class RxUpdateDBWorker extends Thread{
    public RxUpdateDBWorker(){}
    public void run(){
        synchronized(this){
            Drugref.UPDATE_DB=true;

            DPDImport dpdImport =new DPDImport();
            long timeDataImport=0L;
            timeDataImport=dpdImport.doItDifferent();
            timeDataImport=(timeDataImport/1000)/60;
            TempNewGenericImport newGenericImport=new TempNewGenericImport();
            long timeGenericImport=0L;
            timeGenericImport=newGenericImport.run();//in miliseconds
            timeGenericImport=(timeGenericImport/1000)/60;
            dpdImport.setISMPmeds();
            HistoryUtil h=new HistoryUtil();
            h.addUpdateHistory();
            HashMap hm=dpdImport.numberTableRows();
            List<Integer> addedDescriptor=dpdImport.addDescriptorToSearchName();
            List<Integer> addedStrength=dpdImport.addStrengthToBrandName();

            Drugref.DB_INFO.put("tableRowNum", hm);
            Drugref.DB_INFO.put("timeImportDataMinutes", timeDataImport);
            Drugref.DB_INFO.put("timeImportGenericMinutes", timeGenericImport);
            Drugref.DB_INFO.put("descriptor", addedDescriptor);
            Drugref.DB_INFO.put("strength", addedStrength);

            Drugref.UPDATE_DB=false;
        }
    }
}
