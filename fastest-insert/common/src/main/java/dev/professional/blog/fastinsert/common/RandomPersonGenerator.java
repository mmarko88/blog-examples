package dev.professional.blog.fastinsert.common;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates random Person objects for benchmarking.
 */
public class RandomPersonGenerator {
    private static int personId = 0;

    public static List<Person> generateRandomPersons(int count) {
        List<Person> people = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            people.add(generatePerson());
        }
        return people;
    }

    private static Person generatePerson() {
        ++personId;
        return new Person(
                personId,
                RandomStringUtils.randomAlphanumeric(PersonConstants.USERNAME_LENGTH),
                RandomStringUtils.randomAlphabetic(PersonConstants.FIRST_NAME_LENGTH),
                RandomStringUtils.randomAlphabetic(PersonConstants.LAST_NAME_LENGTH),
                RandomUtils.nextInt(10, 100));
    }

    private RandomPersonGenerator() {}
}
