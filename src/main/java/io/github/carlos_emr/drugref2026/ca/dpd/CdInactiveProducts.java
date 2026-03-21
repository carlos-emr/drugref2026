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
package io.github.carlos_emr.drugref2026.ca.dpd;

import java.io.Serializable;
import java.util.Date;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

/**
 * JPA entity mapping to the {@code cd_inactive_products} table in the Canadian Drug Product Database (DPD).
 *
 * <p>Represents drug products that have been discontinued, cancelled, or otherwise removed
 * from the Canadian market. These products are no longer actively marketed but retain their
 * DIN and historical record for reference purposes (e.g., for patients still using remaining
 * stock, or for adverse event reporting).</p>
 *
 * <p>This table is used at search time to flag results as "inactive" so clinicians know the
 * product may no longer be available.</p>
 *
 * <p>Key fields:</p>
 * <ul>
 *   <li>{@code drugCode} -- Health Canada's unique numeric identifier for the drug product.</li>
 *   <li>{@code drugIdentificationNumber} -- The 8-digit DIN of the inactive product.</li>
 *   <li>{@code brandName} -- The brand name under which the product was marketed.</li>
 *   <li>{@code historyDate} -- The date on which the product became inactive (cancelled/dormant).</li>
 * </ul>
 *
 * <p>Source: Health Canada DPD extract file {@code inactive.txt}.</p>
 *
 * @author jackson
 */
@Entity
@Table(name = "cd_inactive_products")
@NamedQueries({@NamedQuery(name = "CdInactiveProducts.findAll", query = "SELECT c FROM CdInactiveProducts c"), @NamedQuery(name = "CdInactiveProducts.findByDrugCode", query = "SELECT c FROM CdInactiveProducts c WHERE c.drugCode = :drugCode"), @NamedQuery(name = "CdInactiveProducts.findByDrugIdentificationNumber", query = "SELECT c FROM CdInactiveProducts c WHERE c.drugIdentificationNumber = :drugIdentificationNumber"), @NamedQuery(name = "CdInactiveProducts.findByBrandName", query = "SELECT c FROM CdInactiveProducts c WHERE c.brandName = :brandName"), @NamedQuery(name = "CdInactiveProducts.findByHistoryDate", query = "SELECT c FROM CdInactiveProducts c WHERE c.historyDate = :historyDate"), @NamedQuery(name = "CdInactiveProducts.findById", query = "SELECT c FROM CdInactiveProducts c WHERE c.id = :id")})
public class CdInactiveProducts implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "drug_code")
    private Integer drugCode;
    @Column(name = "drug_identification_number", length = 8)
    private String drugIdentificationNumber;
    @Column(name = "brand_name", length = 200)
    private String brandName;
    @Column(name = "history_date")
    @Temporal(TemporalType.DATE)
    private Date historyDate;
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;

    public CdInactiveProducts() {
    }

    public CdInactiveProducts(Integer id) {
        this.id = id;
    }

    public Integer getDrugCode() {
        return drugCode;
    }

    public void setDrugCode(Integer drugCode) {
        this.drugCode = drugCode;
    }

    public String getDrugIdentificationNumber() {
        return drugIdentificationNumber;
    }

    public void setDrugIdentificationNumber(String drugIdentificationNumber) {
        this.drugIdentificationNumber = drugIdentificationNumber;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public Date getHistoryDate() {
        return historyDate;
    }

    public void setHistoryDate(Date historyDate) {
        this.historyDate = historyDate;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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
        if (!(object instanceof CdInactiveProducts)) {
            return false;
        }
        CdInactiveProducts other = (CdInactiveProducts) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "io.github.carlos_emr.drugref2026.ca.dpd.CdInactiveProducts[id=" + id + "]";
    }

}
