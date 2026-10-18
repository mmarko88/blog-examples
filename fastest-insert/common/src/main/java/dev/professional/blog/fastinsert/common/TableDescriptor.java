package dev.professional.blog.fastinsert.common;

import lombok.*;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor(staticName = "of")
@Builder(toBuilder = true)
@EqualsAndHashCode
public class TableDescriptor<T> {
    @NonNull
    private final String tableName;
    @NonNull
    private final List<ColumnDescriptor<T>> columns;
    @NonNull
    private final List<String> pkColumns;

    public String getColumnsCommaSeparated() {
        return columns.stream()
                .map(ColumnDescriptor::getColumnName)
                .collect(Collectors.joining(","));
    }
}
