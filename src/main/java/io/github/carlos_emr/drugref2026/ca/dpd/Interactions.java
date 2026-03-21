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
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * JPA entity mapping to the {@code interactions} table, which stores drug-drug interaction
 * records based on the Holbrook evidence-based interaction dataset.
 *
 * <p>Each record represents a known pharmacological interaction between two drugs, identified
 * by their ATC codes. The Holbrook dataset provides clinically significant interactions with
 * evidence ratings and significance levels, enabling clinical decision support for prescribing
 * safety. Interactions are directional: one drug "affects" another.</p>
 *
 * <p>A unique constraint on {@code (affectingatc, affectedatc, effect)} prevents duplicate
 * interaction records for the same drug pair and effect type.</p>
 *
 * <p>Key fields:</p>
 * <ul>
 *   <li>{@code affectingatc} -- The ATC code of the drug causing the interaction (up to 7 characters).</li>
 *   <li>{@code affectedatc} -- The ATC code of the drug being affected by the interaction.</li>
 *   <li>{@code effect} -- A coded effect type describing the nature of the interaction
 *       (e.g., increased or decreased drug levels).</li>
 *   <li>{@code significance} -- A coded significance level indicating clinical importance.</li>
 *   <li>{@code evidence} -- A coded evidence quality rating for the interaction.</li>
 *   <li>{@code comment} -- Free-text clinical commentary about the interaction.</li>
 *   <li>{@code affectingdrug} -- The human-readable name of the affecting drug or drug class.</li>
 *   <li>{@code affecteddrug} -- The human-readable name of the affected drug or drug class.</li>
 * </ul>
 *
 * <p>Source: The bundled resource file {@code interactions-holbrook.txt} (CSV format),
 * loaded during DPD import via {@link io.github.carlos_emr.drugref2026.ca.dpd.fetch.RecordParser}.</p>
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
        return "io.github.carlos_emr.drugref2026.ca.dpd.Interactions[id=" + id + "]";
    }

}
