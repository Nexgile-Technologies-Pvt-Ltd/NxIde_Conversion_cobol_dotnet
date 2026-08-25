package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Customer. COBOL source {@code CVCUS01Y.cpy} / VSAM {@code CUSTDAT} (500 bytes).
 *
 * <p>Byte offsets: id 1-9, first 10-34, middle 35-59, last 60-84, address lines 85-234,
 * state 235-236, country 237-239, ZIP 240-249, phones 250-279, SSN 280-288,
 * government id 289-308, DOB 309-318, EFT 319-328, primary holder 329, FICO 330-332.</p>
 */
@Entity
@Table(name = "customer")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    /** {@code CUST-ID} 9(9). Held as text so leading zeroes survive. */
    @Id
    @Column(name = "customer_id", length = 9, nullable = false)
    private String customerId;

    @Column(name = "first_name", length = 25, nullable = false)
    private String firstName = "";

    @Column(name = "middle_name", length = 25, nullable = false)
    private String middleName = "";

    @Column(name = "last_name", length = 25, nullable = false)
    private String lastName = "";

    @Column(name = "addr_line_1", length = 50, nullable = false)
    private String addrLine1 = "";

    @Column(name = "addr_line_2", length = 50, nullable = false)
    private String addrLine2 = "";

    /** {@code CUST-ADDR-LINE-3}; the account screens label this field "City". */
    @Column(name = "addr_line_3", length = 50, nullable = false)
    private String addrLine3 = "";

    @Column(name = "addr_state_cd", length = 2, nullable = false)
    private String addrStateCd = "";

    @Column(name = "addr_country_cd", length = 3, nullable = false)
    private String addrCountryCd = "";

    /** X(10) in storage even though the BMS map only shows five cells. */
    @Column(name = "addr_zip", length = 10, nullable = false)
    private String addrZip = "";

    @Column(name = "phone_num_1", length = 15, nullable = false)
    private String phoneNum1 = "";

    @Column(name = "phone_num_2", length = 15, nullable = false)
    private String phoneNum2 = "";

    @Column(name = "ssn", length = 9, nullable = false)
    private String ssn = "";

    @Column(name = "govt_issued_id", length = 20, nullable = false)
    private String govtIssuedId = "";

    /** {@code CUST-DOB-YYYY-MM-DD} X(10). */
    @Column(name = "date_of_birth", length = 10, nullable = false)
    private String dateOfBirth = "";

    @Column(name = "eft_account_id", length = 10, nullable = false)
    private String eftAccountId = "";

    @Column(name = "pri_card_holder_ind", length = 1, nullable = false)
    private String priCardHolderInd = "N";

    /** {@code CUST-FICO-CREDIT-SCORE} 9(3); business range 300-850. */
    @Column(name = "fico_score", nullable = false)
    private int ficoScore;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
