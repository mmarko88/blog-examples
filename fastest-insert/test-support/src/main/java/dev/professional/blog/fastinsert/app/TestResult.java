package dev.professional.blog.fastinsert.app;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * Stores benchmark results in the database for later analysis.
 */
@Entity
@Table(name = "testResult")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TestResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column
    private String methodName;
    @Column
    private Date creationDate;
    @Column
    private long durationMs;
    @Column
    private int totalNoOfPeople;
    @Column
    private int batchSize;
    @Column
    private int noOfPeoplePerInsert;
    @Column
    private int repetition;
    @Column
    private Date testDate;
}
