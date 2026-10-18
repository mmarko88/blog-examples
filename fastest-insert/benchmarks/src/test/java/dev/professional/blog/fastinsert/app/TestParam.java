package dev.professional.blog.fastinsert.app;

import dev.professional.blog.fastinsert.common.Person;
import lombok.Value;

import java.util.Date;
import java.util.List;

@Value
public class TestParam {
    List<Person> people;
    int batchSize;
    Date testStartDate;
    int objectsPerInsert;
}
