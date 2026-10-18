package dev.professional.blog.fastinsert.common;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = PersonConstants.PERSON_TABLE_NAME)
@Getter
public final class Person {
    @Id
    @Column(name = PersonConstants.PERSON_ID, columnDefinition = PersonConstants.PERSON_ID_COL_DEF)
    private int personId;
    @Column(name = PersonConstants.USER_NAME, nullable = false, length = PersonConstants.USERNAME_LENGTH, columnDefinition = PersonConstants.USERNAME_COL_DEF)
    private String userName;
    @Column(name = PersonConstants.FIRST_NAME, nullable = false, length = PersonConstants.FIRST_NAME_LENGTH, columnDefinition = PersonConstants.FIRST_NAME_COL_DEF)
    private String firstName;
    @Column(name = PersonConstants.LAST_NAME, nullable = false, length = PersonConstants.LAST_NAME_LENGTH, columnDefinition = PersonConstants.LAST_NAME_COL_DEF)
    private String lastName;
    @Column(name = PersonConstants.YEARS, columnDefinition = PersonConstants.YEARS_COL_DEF)
    private int years;
}
