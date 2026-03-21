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
package org.drugref.plugin;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.Query;
//import org.drugref.ca.dpd.Interactions;
//import org.drugref.ca.dpd.Interactions;
import org.drugref.ca.dpd.Interactions;
import org.drugref.util.JpaUtils;

/**
 *
 * @author jackson
 */
    //public Holbrook holBrook=new Holbrook();
  //  public DrugrefPlugin drugrefPlugin=new DrugrefPlugin();

    //public void drugrefApiHolbrook(String host="localhost", Integer port=8123,String database="drugref2", String user="drugref", String pwd="drugref", String backend="postgres")
    public class Holbrook extends DrugrefApi {

        private    String host = "localhost";
        private    Integer port = 8123;
        private    String database = "drugref2";
        private    String user = "drugref";
        private    String pwd = "drugref";
        private    String backend = "postgres";
   //     private    ApiBase ApiBaseObj=new ApiBase();
    //    private    ApiBase.DrugrefApi DrugrefApiObj = this.ApiBaseObj.new  DrugrefApi(host, port, database, user, pwd, backend) ;

       public Holbrook() {
           super("localhost",8123,"drugref2","drugref","drugref","postgres");
           checkInteractionsATC cia = new checkInteractionsATC();
           Vector vec = new Vector();
           vec.addElement("interactions_byATC");
           this.addfunc(cia, "_search_interactions", vec);
      }

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

        public class checkInteractionsATC {

            public Vector checkInteractionsATC(Vector druglist) {
                p("===start of checkInteractionsATC===");
                p("druglist",druglist.toString());

                Vector interactions = new Vector();
                for (int i = 0; i < druglist.size(); i++) {
                    Vector idrugs = new Vector(druglist);
                    String drug = (String) druglist.get(i);
                    idrugs.removeElement(drug);
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

    

