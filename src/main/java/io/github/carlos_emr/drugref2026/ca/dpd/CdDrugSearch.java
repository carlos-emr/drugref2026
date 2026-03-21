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
package io.github.carlos_emr.drugref2026.ca.dpd;

import java.io.Serializable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

/**
 * JPA entity mapping to the {@code cd_drug_search} table, a pre-built search index derived
 * from raw DPD data.
 *
 * <p>This table is not part of the original Health Canada DPD extract. It is built locally by
 * {@link io.github.carlos_emr.drugref2026.ca.dpd.fetch.ConfigureSearchData} during the DPD
 * import process. It consolidates searchable drug names from multiple sources into a single
 * flat table optimized for type-ahead drug search in clinical applications.</p>
 *
 * <p>Key fields:</p>
 * <ul>
 *   <li>{@code drugCode} -- A reference identifier whose meaning depends on the category:
 *       <ul>
 *         <li>For brand names (cat 13): the DPD drug_code of the specific product.</li>
 *         <li>For ATC codes (cat 8): the ATC number (e.g., "N02BE01").</li>
 *         <li>For generics (cat 11/12): the auto-generated search table id (self-referencing).</li>
 *         <li>For ingredients (cat 14): the active_ingredient_code from the DPD.</li>
 *         <li>For new generics (cat 18/19): a composite key of ai_group_no + form_code.</li>
 *       </ul>
 *   </li>
 *   <li>{@code category} -- Numeric category code defining the type of search entry:
 *       <ul>
 *         <li>8 = ATC (Anatomical Therapeutic Chemical) classification code</li>
 *         <li>10 = AHFS (American Hospital Formulary Service) classification code (deprecated July 2022)</li>
 *         <li>11 = Generic compound (single active ingredient generic name)</li>
 *         <li>12 = Generic (composite/multi-ingredient generic name)</li>
 *         <li>13 = Brand name (the marketed product name)</li>
 *         <li>14 = Individual ingredient name</li>
 *         <li>18 = New generic -- single-ingredient formulation derived from AI group + form</li>
 *         <li>19 = New generic compound -- multi-ingredient formulation derived from AI group + form</li>
 *       </ul>
 *   </li>
 *   <li>{@code name} -- The searchable display name shown to users (e.g., "TYLENOL 500MG",
 *       "ACETAMINOPHEN", "N02BE01 ANILIDES").</li>
 * </ul>
 *
 * @author jackson
 */
@Entity
@Table(name = "cd_drug_search")
@NamedQueries({@NamedQuery(name = "CdDrugSearch.findAll", query = "SELECT c FROM CdDrugSearch c"), @NamedQuery(name = "CdDrugSearch.findById", query = "SELECT c FROM CdDrugSearch c WHERE c.id = :id"), @NamedQuery(name = "CdDrugSearch.findByDrugCode", query = "SELECT c FROM CdDrugSearch c WHERE c.drugCode = :drugCode"), @NamedQuery(name = "CdDrugSearch.findByCategory", query = "SELECT c FROM CdDrugSearch c WHERE c.category = :category"), @NamedQuery(name = "CdDrugSearch.findByName", query = "SELECT c FROM CdDrugSearch c WHERE c.name = :name")})
public class CdDrugSearch implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "drug_code")
    private String drugCode;
    @Column(name = "category")
    private Integer category;
    @Column(name = "name")
    private String name;

    public CdDrugSearch() {
    }

    public CdDrugSearch(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getDrugCode() {
        return drugCode;
    }

    public void setDrugCode(String drugCode) {
        this.drugCode = drugCode;
    }

    public Integer getCategory() {
        return category;
    }

    public void setCategory(Integer category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof CdDrugSearch)) {
            return false;
        }
        CdDrugSearch other = (CdDrugSearch) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "io.github.carlos_emr.drugref2026.ca.dpd.CdDrugSearch[id=" + id + "]";
    }

}
