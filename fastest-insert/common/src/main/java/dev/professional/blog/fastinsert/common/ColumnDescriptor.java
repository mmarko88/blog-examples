package dev.professional.blog.fastinsert.common;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.function.Function;

@Getter
@RequiredArgsConstructor(staticName = "of")
@EqualsAndHashCode
public class ColumnDescriptor<T> {
    private final String columnName;
    private final String dbSqlType;
    private final int sqlType;
    private final Function<T, Object> entityFieldMapper;

    public Object getObjectFieldValue(T entity) {
        return entityFieldMapper.apply(entity);
    }
}
