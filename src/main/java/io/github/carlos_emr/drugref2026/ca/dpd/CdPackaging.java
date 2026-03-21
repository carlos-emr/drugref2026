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
 * JPA entity mapping to the {@code cd_packaging} table in the Canadian Drug Product Database (DPD).
 *
 * <p>Represents the packaging information for drug products approved in Canada. Each record
 * describes a specific package configuration (size, type, and UPC barcode) for a given drug
 * product. A single drug product may have multiple packaging options (e.g., bottles of 30,
 * 100, or 500 tablets).</p>
 *
 * <p>Key fields:</p>
 * <ul>
 *   <li>{@code drugCode} -- Health Canada's unique numeric identifier linking this package to its drug product.</li>
 *   <li>{@code upc} -- The Universal Product Code (barcode) for this specific package.</li>
 *   <li>{@code packageSizeUnit} -- The unit of measurement for the package size (e.g., "ML", "G", "EA").</li>
 *   <li>{@code packageType} -- The type of packaging (e.g., "BOTTLE", "BLISTER PACK", "VIAL", "TUBE").</li>
 *   <li>{@code packageSize} -- The numeric size of the package.</li>
 *   <li>{@code productInforation} -- Additional product information (note: field name reflects original DPD spelling).</li>
 * </ul>
 *
 * <p>Source: Health Canada DPD extract file {@code package.txt} (active products)
 * and {@code package_ia.txt} (inactive products).</p>
 *
 * @author jackson
 */
@Entity
@Table(name = "cd_packaging")
@NamedQueries({@NamedQuery(name = "CdPackaging.findAll", query = "SELECT c FROM CdPackaging c"), @NamedQuery(name = "CdPackaging.findByDrugCode", query = "SELECT c FROM CdPackaging c WHERE c.drugCode = :drugCode"), @NamedQuery(name = "CdPackaging.findByUpc", query = "SELECT c FROM CdPackaging c WHERE c.upc = :upc"), @NamedQuery(name = "CdPackaging.findByPackageSizeUnit", query = "SELECT c FROM CdPackaging c WHERE c.packageSizeUnit = :packageSizeUnit"), @NamedQuery(name = "CdPackaging.findByPackageType", query = "SELECT c FROM CdPackaging c WHERE c.packageType = :packageType"), @NamedQuery(name = "CdPackaging.findByPackageSize", query = "SELECT c FROM CdPackaging c WHERE c.packageSize = :packageSize"), @NamedQuery(name = "CdPackaging.findByProductInforation", query = "SELECT c FROM CdPackaging c WHERE c.productInforation = :productInforation"), @NamedQuery(name = "CdPackaging.findById", query = "SELECT c FROM CdPackaging c WHERE c.id = :id")})
public class CdPackaging implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "drug_code")
    private Integer drugCode;
    @Column(name = "upc", length = 12)
    private String upc;
    @Column(name = "package_size_unit", length = 40)
    private String packageSizeUnit;
    @Column(name = "package_type", length = 40)
    private String packageType;
    @Column(name = "package_size", length = 5)
    private String packageSize;
    @Column(name = "product_inforation", length = 80)
    private String productInforation;
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;

    public CdPackaging() {
    }

    public CdPackaging(Integer id) {
        this.id = id;
    }

    public Integer getDrugCode() {
        return drugCode;
    }

    public void setDrugCode(Integer drugCode) {
        this.drugCode = drugCode;
    }

    public String getUpc() {
        return upc;
    }

    public void setUpc(String upc) {
        this.upc = upc;
    }

    public String getPackageSizeUnit() {
        return packageSizeUnit;
    }

    public void setPackageSizeUnit(String packageSizeUnit) {
        this.packageSizeUnit = packageSizeUnit;
    }

    public String getPackageType() {
        return packageType;
    }

    public void setPackageType(String packageType) {
        this.packageType = packageType;
    }

    public String getPackageSize() {
        return packageSize;
    }

    public void setPackageSize(String packageSize) {
        this.packageSize = packageSize;
    }

    public String getProductInforation() {
        return productInforation;
    }

    public void setProductInforation(String productInforation) {
        this.productInforation = productInforation;
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
        if (!(object instanceof CdPackaging)) {
            return false;
        }
        CdPackaging other = (CdPackaging) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "io.github.carlos_emr.drugref2026.ca.dpd.CdPackaging[id=" + id + "]";
    }

}
