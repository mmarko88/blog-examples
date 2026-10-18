package dev.professional.blog.fastinsert.common;

import java.sql.Types;
import java.util.Arrays;
import java.util.List;

/**
 * Reusable table/column descriptors for the Person entity.
 * Used by JDBC-level insert strategies across Parts 2–4.
 */
public class PersonDescriptors {

    public static final TableDescriptor<Person> PERSON_TABLE = TableDescriptor.of(
            PersonConstants.PERSON_TABLE_NAME,
            Arrays.asList(
                    ColumnDescriptor.of(PersonConstants.PERSON_ID, PersonConstants.PERSON_ID_COL_DEF, Types.INTEGER, Person::getPersonId),
                    ColumnDescriptor.of(PersonConstants.FIRST_NAME, PersonConstants.FIRST_NAME_COL_DEF, Types.NVARCHAR, Person::getFirstName),
                    ColumnDescriptor.of(PersonConstants.LAST_NAME, PersonConstants.LAST_NAME_COL_DEF, Types.NVARCHAR, Person::getLastName),
                    ColumnDescriptor.of(PersonConstants.USER_NAME, PersonConstants.USERNAME_COL_DEF, Types.NVARCHAR, Person::getUserName),
                    ColumnDescriptor.of(PersonConstants.YEARS, PersonConstants.YEARS_COL_DEF, Types.INTEGER, Person::getYears)
            ),
            List.of(PersonConstants.PERSON_ID));

    private PersonDescriptors() {}
}
