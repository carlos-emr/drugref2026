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
package org.drugref.ca.dpd;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

/**
 *
 * @author jackson
 */
@Entity
@Table(name = "cd_active_ingredients", catalog = "drugref2")
@NamedQueries({@NamedQuery(name = "CdActiveIngredients.findAll", query = "SELECT c FROM CdActiveIngredients c"), @NamedQuery(name = "CdActiveIngredients.findByDrugCode", query = "SELECT c FROM CdActiveIngredients c WHERE c.drugCode = :drugCode"), @NamedQuery(name = "CdActiveIngredients.findByActiveIngredientCode", query = "SELECT c FROM CdActiveIngredients c WHERE c.activeIngredientCode = :activeIngredientCode"), @NamedQuery(name = "CdActiveIngredients.findByIngredient", query = "SELECT c FROM CdActiveIngredients c WHERE c.ingredient = :ingredient"), @NamedQuery(name = "CdActiveIngredients.findByIngredientSuppliedInd", query = "SELECT c FROM CdActiveIngredients c WHERE c.ingredientSuppliedInd = :ingredientSuppliedInd"), @NamedQuery(name = "CdActiveIngredients.findByStrength", query = "SELECT c FROM CdActiveIngredients c WHERE c.strength = :strength"), @NamedQuery(name = "CdActiveIngredients.findByStrengthUnit", query = "SELECT c FROM CdActiveIngredients c WHERE c.strengthUnit = :strengthUnit"), @NamedQuery(name = "CdActiveIngredients.findByStrengthType", query = "SELECT c FROM CdActiveIngredients c WHERE c.strengthType = :strengthType"), @NamedQuery(name = "CdActiveIngredients.findByDosageValue", query = "SELECT c FROM CdActiveIngredients c WHERE c.dosageValue = :dosageValue"), @NamedQuery(name = "CdActiveIngredients.findByBase", query = "SELECT c FROM CdActiveIngredients c WHERE c.base = :base"), @NamedQuery(name = "CdActiveIngredients.findByDosageUnit", query = "SELECT c FROM CdActiveIngredients c WHERE c.dosageUnit = :dosageUnit"), @NamedQuery(name = "CdActiveIngredients.findByNotes", query = "SELECT c FROM CdActiveIngredients c WHERE c.notes = :notes"), @NamedQuery(name = "CdActiveIngredients.findById", query = "SELECT c FROM CdActiveIngredients c WHERE c.id = :id")})
public class CdActiveIngredients implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "drug_code")
    private Integer drugCode;
    @Column(name = "active_ingredient_code")
    private Integer activeIngredientCode;
    @Column(name = "ingredient", length = 240)
    private String ingredient;
    @Column(name = "ingredient_supplied_ind")
    private String ingredientSuppliedInd;
    @Column(name = "strength", length = 20)
    private String strength;
    @Column(name = "strength_unit", length = 40)
    private String strengthUnit;
    @Column(name = "strength_type", length = 40)
    private String strengthType;
    @Column(name = "dosage_value", length = 20)
    private String dosageValue;
    @Column(name = "base")
    private String base;
    @Column(name = "dosage_unit", length = 40)
    private String dosageUnit;
    @Column(name = "notes", length = 2147483647)
    private String notes;
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;

    public CdActiveIngredients() {
    }

    public CdActiveIngredients(Integer id) {
        this.id = id;
    }

    public Integer getDrugCode() {
        return drugCode;
    }

    public void setDrugCode(Integer drugCode) {
        this.drugCode = drugCode;
    }

    public Integer getActiveIngredientCode() {
        return activeIngredientCode;
    }

    public void setActiveIngredientCode(Integer activeIngredientCode) {
        this.activeIngredientCode = activeIngredientCode;
    }

    public String getIngredient() {
        return ingredient;
    }

    public void setIngredient(String ingredient) {
        this.ingredient = ingredient;
    }

    public String getIngredientSuppliedInd() {
        return ingredientSuppliedInd;
    }

    public void setIngredientSuppliedInd(String ingredientSuppliedInd) {
        this.ingredientSuppliedInd = ingredientSuppliedInd;
    }

    public String getStrength() {
        return strength;
    }

    public void setStrength(String strength) {
        this.strength = strength;
    }

    public String getStrengthUnit() {
        return strengthUnit;
    }

    public void setStrengthUnit(String strengthUnit) {
        this.strengthUnit = strengthUnit;
    }

    public String getStrengthType() {
        return strengthType;
    }

    public void setStrengthType(String strengthType) {
        this.strengthType = strengthType;
    }

    public String getDosageValue() {
        return dosageValue;
    }

    public void setDosageValue(String dosageValue) {
        this.dosageValue = dosageValue;
    }

    public String getBase() {
        return base;
    }

    public void setBase(String base) {
        this.base = base;
    }

    public String getDosageUnit() {
        return dosageUnit;
    }

    public void setDosageUnit(String dosageUnit) {
        this.dosageUnit = dosageUnit;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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
        if (!(object instanceof CdActiveIngredients)) {
            return false;
        }
        CdActiveIngredients other = (CdActiveIngredients) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.drugref.ca.dpd.CdActiveIngredients[id=" + id + "]";
    }

}
