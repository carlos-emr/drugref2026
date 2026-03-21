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
package io.github.carlos_emr.drugref2026.plugin;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Query;
//import io.github.carlos_emr.drugref2026.ca.dpd.Interactions;
//import io.github.carlos_emr.drugref2026.ca.dpd.Interactions;
import io.github.carlos_emr.drugref2026.ca.dpd.Interactions;
import io.github.carlos_emr.drugref2026.util.JpaUtils;

/**
 * The Holbrook drug interaction plugin, providing evidence-based drug-drug interaction
 * data from the Holbrook interaction dataset.
 *
 * <p>This plugin extends {@link DrugrefApi} and registers a {@link checkInteractionsATC}
 * function that queries the database for interactions between drugs identified by their
 * ATC (Anatomical Therapeutic Chemical) codes. The underlying data comes from the
 * {@code interactions-holbrook.txt} resource file, which is loaded into the database
 * as {@link Interactions} entities.</p>
 *
 * <h3>Interaction data fields</h3>
 * <p>Each interaction record contains:</p>
 * <ul>
 *   <li>{@code affectingatc} / {@code affectedatc} -- ATC codes of the two drugs</li>
 *   <li>{@code affectingdrug} / {@code affecteddrug} -- drug names</li>
 *   <li>{@code significance} -- clinical significance rating (1=minor, 2=moderate, 3=major)</li>
 *   <li>{@code effect} -- effect code (A=augments, I=inhibits, n/N=no effect; lowercase=no clinical effect)</li>
 *   <li>{@code evidence} -- evidence quality (P=poor, F=fair, G=good)</li>
 *   <li>{@code comment} -- additional clinical notes</li>
 * </ul>
 *
 * <h3>ATC matching logic</h3>
 * <p>The {@link checkInteractionsATC} inner class takes a list of ATC codes, generates
 * all pairwise combinations, and queries the database for each pair using JPQL. For each
 * pair (drug[i], drug[j]) where i != j, it queries for interactions where
 * {@code affectingatc = drug[i]} and {@code affectedatc = drug[j]}.</p>
 *
 * @author jackson
 */
    //public Holbrook holBrook=new Holbrook();
  //  public DrugrefPlugin drugrefPlugin=new DrugrefPlugin();

    //public void drugrefApiHolbrook(String host="localhost", Integer port=8123,String database="drugref2", String user="drugref", String pwd="drugref", String backend="postgres")
    public class Holbrook extends DrugrefApi {

        /** Database host for Holbrook data. */
        private    String host = "localhost";
        /** Database port. */
        private    Integer port = 8123;
        /** Database name containing Holbrook interaction data. */
        private    String database = "drugref2";
        /** Database user. */
        private    String user = "drugref";
        /** Database password. */
        private    String pwd = "drugref";
        /** Database backend type. */
        private    String backend = "postgres";
   //     private    ApiBase ApiBaseObj=new ApiBase();
    //    private    ApiBase.DrugrefApi DrugrefApiObj = this.ApiBaseObj.new  DrugrefApi(host, port, database, user, pwd, backend) ;

       /**
        * Creates the Holbrook plugin, initializing the database connection and registering
        * the {@code interactions_byATC} capability backed by the {@link checkInteractionsATC}
        * function with a "_search_interactions" requires key.
        */
       public Holbrook() {
           super("localhost",8123,"drugref2","drugref","drugref","postgres");
           checkInteractionsATC cia = new checkInteractionsATC();
           Vector vec = new Vector();
           vec.addElement("interactions_byATC");
           this.addfunc(cia, "_search_interactions", vec);
      }

        /**
         * Returns a legend (lookup table) mapping coded values to their human-readable descriptions
         * for a given interaction data field.
         *
         * <p>Supported keywords:</p>
         * <ul>
         *   <li>{@code "effect"} -- Maps effect codes: A/a=augments, I/i=inhibits, n/N=no effect, space=unknown</li>
         *   <li>{@code "significance"} -- Maps significance levels: 1=minor, 2=moderate, 3=major, space=unknown</li>
         *   <li>{@code "evidence"} -- Maps evidence quality: P=poor, F=fair, G=good, space=unknown</li>
         * </ul>
         *
         * @param keyword the field name to get the legend for ("effect", "significance", or "evidence")
         * @return a Hashtable mapping coded values (String) to their descriptions (String)
         */
        public Hashtable legend(String keyword) {
            p("===start of legend===");
            Hashtable ha = new Hashtable();
            if (keyword.equals("effect")) {
                ha.put("a", "augments (no clinical effect)");
                ha.put("A", "augments");
                ha.put("i", "inhibits (no clinical effect)");
                ha.put("I", "inhibits");
                ha.put("n", "has no effect on");
                ha.put("N", "has no effect on");
                ha.put(" ", "unkown effect on");
            } else if (keyword.equals("significance")) {
                ha.put("1", "minor");
                ha.put("2", "moderate");
                ha.put("3", "major");
                ha.put(" ", "unknown");
            } else if (keyword.equals("evidence")) {
                ha.put("P", "poor");
                ha.put("F", "fair");
                ha.put("G", "goog");
                ha.put(" ", "unknown");
            }
            p("legend return",ha.toString());
            p("==end of legend==");
            return ha;
        }

        /**
         * Inner class that performs drug interaction checks by ATC code.
         *
         * <p>Used as a registered function in the {@link DrugrefApi} provider framework.
         * When invoked, it queries the database for all pairwise interactions among the
         * provided list of ATC codes.</p>
         */
        public class checkInteractionsATC {

            /**
             * Checks for drug interactions among a list of drugs identified by ATC codes.
             *
             * <p>For each pair of drugs in the list, queries the {@code Interactions} table
             * for records where one drug is the "affecting" drug and the other is the "affected"
             * drug. Both directions are checked because drug A may affect drug B differently
             * than drug B affects drug A.</p>
             *
             * <p>The algorithm removes the current drug from the candidate list to avoid
             * self-interaction checks, then iterates over all remaining drugs.</p>
             *
             * @param druglist a Vector of ATC code strings to check for pairwise interactions
             * @return a Vector of Hashtable objects, each containing interaction details
             *         (affectingatc, affectedatc, affectingdrug, affecteddrug, significance,
             *         effect, evidence, comment)
             */
            public Vector checkInteractionsATC(Vector druglist) {
                p("===start of checkInteractionsATC===");
                p("druglist",druglist.toString());

                Vector interactions = new Vector();
                // Iterate over each drug in the list and check it against all other drugs
                for (int i = 0; i < druglist.size(); i++) {
                    // Create a copy of the drug list and remove the current drug to avoid self-checks
                    Vector idrugs = new Vector(druglist);
                    String drug = (String) druglist.get(i);
                    idrugs.removeElement(drug);
                    // For each remaining drug, query for interactions where the current drug
                    // is the "affecting" drug and the other is the "affected" drug
                    for (int j = 0; j < idrugs.size(); j++) {
                        String query = "select hi from Interactions hi where hi.affectingatc=:affecting and hi.affectedatc=:affected";
                        //start to run query
                 //       p("before query");
                        EntityManager em = JpaUtils.createEntityManager();
                        EntityTransaction tx = em.getTransaction();
                        tx.begin();
                        Query queryOne = em.createQuery(query);
                        queryOne.setParameter("affecting", drug);
                        queryOne.setParameter("affected", idrugs.get(j));
                      //  p("affecting",drug);
                      //  p("affected",idrugs.get(j).toString());
                      //  p("query",query);

                        List<Interactions> results = new ArrayList();
                      try{ // p("before query");
                            results = queryOne.getResultList();                          
                           // p("after query");
                            tx.commit();

                        // Vector results = this.runquery(query, params);
                         // Convert each JPA Interactions entity to a Hashtable for the plugin API
                         for(Interactions result:results){
                            Hashtable interaction = new Hashtable();
                            interaction.put("affectingatc", result.getAffectingatc());
                            interaction.put("affectedatc", result.getAffectedatc());
                            interaction.put("affectingdrug", result.getAffectingdrug());
                            interaction.put("affecteddrug", result.getAffecteddrug());
                            interaction.put("significance", result.getSignificance());
                            interaction.put("effect", result.getEffect());
                            interaction.put("evidence", result.getEvidence());
                            interaction.put("comment", result.getComment());
                            p("***interaction",interaction.toString());
                            interactions.addElement(interaction);
                        }
                        }catch(Exception e){
                            e.printStackTrace();                            
                        }
                    }
                }
                p("interactions",interactions.toString());
                p("===end of checkInteractionsATC===");
                return interactions;
            }
        }
    }

    

