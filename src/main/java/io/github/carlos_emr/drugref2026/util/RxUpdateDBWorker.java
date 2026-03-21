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

import java.util.HashMap;
import java.util.List;
import io.github.carlos_emr.drugref2026.Drugref;
import io.github.carlos_emr.drugref2026.ca.dpd.fetch.DPDImport;
import io.github.carlos_emr.drugref2026.ca.dpd.fetch.TempNewGenericImport;
import io.github.carlos_emr.drugref2026.ca.dpd.history.HistoryUtil;

/**
 * Background worker thread that orchestrates a full database update from Health Canada's
 * Drug Product Database (DPD).
 *
 * <p>This thread is launched by {@link Drugref#updateDB()} and performs the following
 * sequential steps:
 * <ol>
 *   <li>Import raw DPD data from Health Canada (drug products, active ingredients,
 *       therapeutic classes, routes, forms, etc.)</li>
 *   <li>Generate generic drug search entries from the imported data</li>
 *   <li>Flag ISMP (Institute for Safe Medication Practices) high-alert medications</li>
 *   <li>Record the update in the History table for auditing</li>
 *   <li>Enhance search data by adding descriptors and strength information to drug names</li>
 *   <li>Store update statistics (timing, row counts) in {@link Drugref#DB_INFO}</li>
 * </ol>
 *
 * <p>The {@link Drugref#UPDATE_DB} flag is set to {@code true} for the duration of the
 * update to prevent concurrent updates and to inform clients that data is being refreshed.
 *
 * @author jackson
 */
public class RxUpdateDBWorker extends Thread{

    /** Default constructor. */
    public RxUpdateDBWorker(){}

    /**
     * Executes the full database update sequence. Synchronized on {@code this} to
     * ensure the entire update runs atomically within this thread instance.
     */
    public void run(){
        synchronized(this){
            // Set the global flag so other threads/requests know an update is in progress
            Drugref.UPDATE_DB=true;

            // Step 1: Import DPD (Drug Product Database) data from Health Canada.
            // This downloads and imports drug products, active ingredients, therapeutic
            // classes, routes, pharmaceutical forms, packaging, etc.
            DPDImport dpdImport =new DPDImport();
            long timeDataImport=0L;
            timeDataImport=dpdImport.doItDifferent();
            timeDataImport=(timeDataImport/1000)/60; // Convert milliseconds to minutes

            // Step 2: Import generic drug entries. These are synthesized from the DPD data
            // to allow searching by generic (non-proprietary) drug names.
            TempNewGenericImport newGenericImport=new TempNewGenericImport();
            long timeGenericImport=0L;
            timeGenericImport=newGenericImport.run();//in miliseconds
            timeGenericImport=(timeGenericImport/1000)/60; // Convert milliseconds to minutes

            // Step 3: Flag ISMP high-alert medications in the database.
            // These drugs require special safeguards to reduce the risk of errors.
            dpdImport.setISMPmeds();

            // Step 4: Record this update in the History table for audit trail.
            HistoryUtil h=new HistoryUtil();
            h.addUpdateHistory();

            // Step 5: Enhance search data -- add pharmaceutical form descriptors and
            // strength values to drug search names for better search results.
            HashMap hm=dpdImport.numberTableRows();
            List<Integer> addedDescriptor=dpdImport.addDescriptorToSearchName();
            List<Integer> addedStrength=dpdImport.addStrengthToBrandName();

            // Step 6: Store update statistics for display via the admin interface.
            Drugref.DB_INFO.put("tableRowNum", hm);
            Drugref.DB_INFO.put("timeImportDataMinutes", timeDataImport);
            Drugref.DB_INFO.put("timeImportGenericMinutes", timeGenericImport);
            Drugref.DB_INFO.put("descriptor", addedDescriptor);
            Drugref.DB_INFO.put("strength", addedStrength);

            // Clear the update flag so the system returns to normal operation
            Drugref.UPDATE_DB=false;
        }
    }
}
