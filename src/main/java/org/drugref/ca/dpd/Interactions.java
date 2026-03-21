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
package org.drugref.ca.dpd;

import java.io.Serializable;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 *
 * @author jackson
 */
@Entity
@Table(name = "interactions", uniqueConstraints = {@UniqueConstraint(columnNames = {"affectingatc", "affectedatc", "effect"})})
@NamedQueries({@NamedQuery(name = "Interactions.findAll", query = "SELECT i FROM Interactions i"), @NamedQuery(name = "Interactions.findById", query = "SELECT i FROM Interactions i WHERE i.id = :id"), @NamedQuery(name = "Interactions.findByAffectingatc", query = "SELECT i FROM Interactions i WHERE i.affectingatc = :affectingatc"), @NamedQuery(name = "Interactions.findByAffectedatc", query = "SELECT i FROM Interactions i WHERE i.affectedatc = :affectedatc"), @NamedQuery(name = "Interactions.findByEffect", query = "SELECT i FROM Interactions i WHERE i.effect = :effect"), @NamedQuery(name = "Interactions.findBySignificance", query = "SELECT i FROM Interactions i WHERE i.significance = :significance"), @NamedQuery(name = "Interactions.findByEvidence", query = "SELECT i FROM Interactions i WHERE i.evidence = :evidence"), @NamedQuery(name = "Interactions.findByComment", query = "SELECT i FROM Interactions i WHERE i.comment = :comment"), @NamedQuery(name = "Interactions.findByAffectingdrug", query = "SELECT i FROM Interactions i WHERE i.affectingdrug = :affectingdrug"), @NamedQuery(name = "Interactions.findByAffecteddrug", query = "SELECT i FROM Interactions i WHERE i.affecteddrug = :affecteddrug")})
public class Interactions implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @Column(name = "id", nullable = false)
    private Integer id;
    @Column(name = "affectingatc", length = 7)
    private String affectingatc;
    @Column(name = "affectedatc", length = 7)
    private String affectedatc;
    @Column(name = "effect")
    private String effect;
    @Column(name = "significance")
    private String significance;
    @Column(name = "evidence")
    private String evidence;
    @Column(name = "comment", length = 2147483647)
    private String comment;
    @Column(name = "affectingdrug", length = 2147483647)
    private String affectingdrug;
    @Column(name = "affecteddrug", length = 2147483647)
    private String affecteddrug;

    public Interactions() {
    }

    public Interactions(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getAffectingatc() {
        return affectingatc;
    }

    public void setAffectingatc(String affectingatc) {
        this.affectingatc = affectingatc;
    }

    public String getAffectedatc() {
        return affectedatc;
    }

    public void setAffectedatc(String affectedatc) {
        this.affectedatc = affectedatc;
    }

    public String getEffect() {
        return effect;
    }

    public void setEffect(String effect) {
        this.effect = effect;
    }

    public String getSignificance() {
        return significance;
    }

    public void setSignificance(String significance) {
        this.significance = significance;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getAffectingdrug() {
        return affectingdrug;
    }

    public void setAffectingdrug(String affectingdrug) {
        this.affectingdrug = affectingdrug;
    }

    public String getAffecteddrug() {
        return affecteddrug;
    }

    public void setAffecteddrug(String affecteddrug) {
        this.affecteddrug = affecteddrug;
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
        if (!(object instanceof Interactions)) {
            return false;
        }
        Interactions other = (Interactions) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "org.drugref.ca.dpd.Interactions[id=" + id + "]";
    }

}
