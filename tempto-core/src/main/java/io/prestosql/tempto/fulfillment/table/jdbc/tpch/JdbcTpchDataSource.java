/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.tempto.fulfillment.table.jdbc.tpch;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import io.airlift.tpch.TpchEntity;
import io.prestosql.tempto.fulfillment.table.hive.tpch.TpchTable;
import io.prestosql.tempto.fulfillment.table.jdbc.RelationalDataSource;
import io.prestosql.tempto.internal.query.QueryRowMapper;

import java.sql.JDBCType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Streams.stream;
import static java.util.Objects.requireNonNull;

public class JdbcTpchDataSource
        implements RelationalDataSource
{
    private static final Splitter SPLITTER = Splitter.on('|');

    private final TpchTable table;
    private final List<JDBCType> columnTypes;
    private final double scaleFactor;

    public JdbcTpchDataSource(TpchTable table, List<JDBCType> columns, double scaleFactor)
    {
        checkArgument(scaleFactor > 0.0, "scaleFactor should be greater than zero");

        this.table = requireNonNull(table, "table is null");
        this.columnTypes = ImmutableList.copyOf(columns);
        this.scaleFactor = scaleFactor;
    }

    @Override
    public Iterator<List<Object>> getDataRows()
    {
        return stream(table.entity().createGenerator(scaleFactor, 1, 1))
                .map(this::tpchEntityToObjects)
                .iterator();
    }

    private List<Object> tpchEntityToObjects(TpchEntity entity)
    {
        List<String> columnValues = SPLITTER.splitToList(entity.toLine());
        QueryRowMapper queryRowMapper = new QueryRowMapper(columnTypes);
        List<String> valuesWithoutFinalBlank = columnValues.subList(0, columnValues.size() - 1);
        return new ArrayList<>(queryRowMapper.mapToRow(valuesWithoutFinalBlank).getValues());
    }
}
