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
 * JPA entity mapping to the {@code cd_companies} table in the Canadian Drug Product Database (DPD).
 *
 * <p>Represents the pharmaceutical manufacturers, distributors, and market authorization holders
 * associated with drug products approved for sale in Canada. Each record links a company to a
 * specific drug product via {@code drugCode}. A drug product may have multiple associated
 * companies (e.g., manufacturer and distributor).</p>
 *
 * <p>Key fields:</p>
 * <ul>
 *   <li>{@code drugCode} -- Health Canada's unique numeric identifier linking this company record to a drug product.</li>
 *   <li>{@code mfrCode} -- The manufacturer code assigned by Health Canada.</li>
 *   <li>{@code companyCode} -- Health Canada's unique numeric identifier for the company.</li>
 *   <li>{@code companyName} -- The legal name of the company (e.g., "PFIZER CANADA ULC").</li>
 *   <li>{@code companyType} -- The role of the company (e.g., "Manufacturer", "Distributor").</li>
 *   <li>{@code addressMailingFlag} -- Flag indicating this is the company's mailing address.</li>
 *   <li>{@code addressBillingFlag} -- Flag indicating this is the company's billing address.</li>
 *   <li>{@code addressNotificationFlag} -- Flag indicating this is the address for regulatory notifications.</li>
 *   <li>{@code streetName}, {@code cityName}, {@code province}, {@code country}, {@code postalCode} -- Full mailing address.</li>
 * </ul>
 *
 * <p>Source: Health Canada DPD extract file {@code comp.txt} (active products)
 * and {@code comp_ia.txt} (inactive products).</p>
 *
 * @author jackson
 */
@Entity
@Table(name = "cd_companies")
@NamedQueries({@NamedQuery(name = "CdCompanies.findAll", query = "SELECT c FROM CdCompanies c"), @NamedQuery(name = "CdCompanies.findByDrugCode", query = "SELECT c FROM CdCompanies c WHERE c.drugCode = :drugCode"), @NamedQuery(name = "CdCompanies.findByMfrCode", query = "SELECT c FROM CdCompanies c WHERE c.mfrCode = :mfrCode"), @NamedQuery(name = "CdCompanies.findByCompanyCode", query = "SELECT c FROM CdCompanies c WHERE c.companyCode = :companyCode"), @NamedQuery(name = "CdCompanies.findByCompanyName", query = "SELECT c FROM CdCompanies c WHERE c.companyName = :companyName"), @NamedQuery(name = "CdCompanies.findByCompanyType", query = "SELECT c FROM CdCompanies c WHERE c.companyType = :companyType"), @NamedQuery(name = "CdCompanies.findByAddressMailingFlag", query = "SELECT c FROM CdCompanies c WHERE c.addressMailingFlag = :addressMailingFlag"), @NamedQuery(name = "CdCompanies.findByAddressBillingFlag", query = "SELECT c FROM CdCompanies c WHERE c.addressBillingFlag = :addressBillingFlag"), @NamedQuery(name = "CdCompanies.findByAddressNotificationFlag", query = "SELECT c FROM CdCompanies c WHERE c.addressNotificationFlag = :addressNotificationFlag"), @NamedQuery(name = "CdCompanies.findByAddressOther", query = "SELECT c FROM CdCompanies c WHERE c.addressOther = :addressOther"), @NamedQuery(name = "CdCompanies.findBySuiteNumber", query = "SELECT c FROM CdCompanies c WHERE c.suiteNumber = :suiteNumber"), @NamedQuery(name = "CdCompanies.findByStreetName", query = "SELECT c FROM CdCompanies c WHERE c.streetName = :streetName"), @NamedQuery(name = "CdCompanies.findByCityName", query = "SELECT c FROM CdCompanies c WHERE c.cityName = :cityName"), @NamedQuery(name = "CdCompanies.findByProvince", query = "SELECT c FROM CdCompanies c WHERE c.province = :province"), @NamedQuery(name = "CdCompanies.findByCountry", query = "SELECT c FROM CdCompanies c WHERE c.country = :country"), @NamedQuery(name = "CdCompanies.findByPostalCode", query = "SELECT c FROM CdCompanies c WHERE c.postalCode = :postalCode"), @NamedQuery(name = "CdCompanies.findByPostOfficeBox", query = "SELECT c FROM CdCompanies c WHERE c.postOfficeBox = :postOfficeBox"), @NamedQuery(name = "CdCompanies.findById", query = "SELECT c FROM CdCompanies c WHERE c.id = :id")})
public class CdCompanies implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "drug_code")
    private Integer drugCode;
    @Column(name = "mfr_code", length = 5)
    private String mfrCode;
    @Column(name = "company_code")
    private Integer companyCode;
    @Column(name = "company_name", length = 80)
    private String companyName;
    @Column(name = "company_type", length = 40)
    private String companyType;
    @Column(name = "address_mailing_flag")
    private String addressMailingFlag;
    @Column(name = "address_billing_flag")
    private String addressBillingFlag;
    @Column(name = "address_notification_flag")
    private String addressNotificationFlag;
    @Column(name = "address_other", length = 20)
    private String addressOther;
    @Column(name = "suite_number", length = 20)
    private String suiteNumber;
    @Column(name = "street_name", length = 80)
    private String streetName;
    @Column(name = "city_name", length = 60)
    private String cityName;
    @Column(name = "province", length = 40)
    private String province;
    @Column(name = "country", length = 40)
    private String country;
    @Column(name = "postal_code", length = 20)
    private String postalCode;
    @Column(name = "post_office_box", length = 15)
    private String postOfficeBox;
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;

    public CdCompanies() {
    }

    public CdCompanies(Integer id) {
        this.id = id;
    }

    public Integer getDrugCode() {
        return drugCode;
    }

    public void setDrugCode(Integer drugCode) {
        this.drugCode = drugCode;
    }

    public String getMfrCode() {
        return mfrCode;
    }

    public void setMfrCode(String mfrCode) {
        this.mfrCode = mfrCode;
    }

    public Integer getCompanyCode() {
        return companyCode;
    }

    public void setCompanyCode(Integer companyCode) {
        this.companyCode = companyCode;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getCompanyType() {
        return companyType;
    }

    public void setCompanyType(String companyType) {
        this.companyType = companyType;
    }

    public String getAddressMailingFlag() {
        return addressMailingFlag;
    }

    public void setAddressMailingFlag(String addressMailingFlag) {
        this.addressMailingFlag = addressMailingFlag;
    }

    public String getAddressBillingFlag() {
        return addressBillingFlag;
    }

    public void setAddressBillingFlag(String addressBillingFlag) {
        this.addressBillingFlag = addressBillingFlag;
    }

    public String getAddressNotificationFlag() {
        return addressNotificationFlag;
    }

    public void setAddressNotificationFlag(String addressNotificationFlag) {
        this.addressNotificationFlag = addressNotificationFlag;
    }

    public String getAddressOther() {
        return addressOther;
    }

    public void setAddressOther(String addressOther) {
        this.addressOther = addressOther;
    }

    public String getSuiteNumber() {
        return suiteNumber;
    }

    public void setSuiteNumber(String suiteNumber) {
        this.suiteNumber = suiteNumber;
    }

    public String getStreetName() {
        return streetName;
    }

    public void setStreetName(String streetName) {
        this.streetName = streetName;
    }

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getPostOfficeBox() {
        return postOfficeBox;
    }

    public void setPostOfficeBox(String postOfficeBox) {
        this.postOfficeBox = postOfficeBox;
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
        if (!(object instanceof CdCompanies)) {
            return false;
        }
        CdCompanies other = (CdCompanies) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "io.github.carlos_emr.drugref2026.ca.dpd.CdCompanies[id=" + id + "]";
    }

}
