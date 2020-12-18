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

package io.prestosql.tempto.assertions

import io.prestosql.tempto.configuration.Configuration
import spock.lang.Specification
import spock.lang.Unroll

import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import java.time.ZonedDateTime

import static java.sql.JDBCType.BIGINT
import static java.sql.JDBCType.BINARY
import static java.sql.JDBCType.BIT
import static java.sql.JDBCType.CHAR
import static java.sql.JDBCType.DATE
import static java.sql.JDBCType.DECIMAL
import static java.sql.JDBCType.DOUBLE
import static java.sql.JDBCType.FLOAT
import static java.sql.JDBCType.INTEGER
import static java.sql.JDBCType.JAVA_OBJECT
import static java.sql.JDBCType.LONGVARBINARY
import static java.sql.JDBCType.LONGVARCHAR
import static java.sql.JDBCType.NUMERIC
import static java.sql.JDBCType.NVARCHAR
import static java.sql.JDBCType.SMALLINT
import static java.sql.JDBCType.TIME
import static java.sql.JDBCType.TIMESTAMP
import static java.sql.JDBCType.TIMESTAMP_WITH_TIMEZONE
import static java.sql.JDBCType.TIME_WITH_TIMEZONE
import static java.sql.JDBCType.TINYINT
import static java.sql.JDBCType.VARBINARY
import static java.sql.JDBCType.VARCHAR

class QueryResultValueComparatorTest
        extends Specification
{
    @Unroll
    def 'queryResultValueComparator(#type).compare(#actual,#expected) = #result'()
    {
        setup:
        Configuration configuration = Mock(Configuration)
        configuration.getDouble(_) >> Optional.empty()

        expect:
        QueryResultValueComparator.comparatorForType(type, configuration).test(actual, expected) == result

        where:
        type                    | actual                                   | expected                                 | result
        CHAR                    | null                                     | null                                     | true
        CHAR                    | null                                     | "a"                                      | false
        CHAR                    | "a"                                      | null                                     | false

        CHAR                    | "a"                                      | "a"                                      | true
        VARCHAR                 | "a"                                      | "a"                                      | true
        NVARCHAR                | "a"                                      | "a"                                      | true
        LONGVARCHAR             | "a"                                      | "a"                                      | true
        VARCHAR                 | "a"                                      | "b"                                      | false
        VARCHAR                 | "b"                                      | "a"                                      | false
        VARCHAR                 | "b"                                      | 1                                        | false

        BINARY                  | byteArray(0)                             | byteArray(0)                             | true
        VARBINARY               | byteArray(0)                             | byteArray(0)                             | true
        LONGVARBINARY           | byteArray(0)                             | byteArray(0)                             | true
        BINARY                  | byteArray(0)                             | byteArray(1)                             | false
        BINARY                  | byteArray(1)                             | byteArray(0)                             | false
        BINARY                  | byteArray(0)                             | 0                                        | false

        BIT                     | true                                     | true                                     | true
        BIT                     | true                                     | false                                    | false
        BIT                     | false                                    | true                                     | false
        BIT                     | false                                    | 0                                        | false

        BIGINT                  | 1L                                       | 1                                        | true
        INTEGER                 | 1                                        | 1                                        | true
        SMALLINT                | 1 as short                               | 1                                        | true
        TINYINT                 | 1 as byte                                | 1                                        | true
        BIGINT                  | 1L                                       | 0L                                       | false
        BIGINT                  | 0L                                       | 1L                                       | false
        BIGINT                  | 0L                                       | "a"                                      | false

        DOUBLE                  | Double.valueOf(0.0)                      | Double.valueOf(0.0)                      | true
        DOUBLE                  | Double.valueOf(1.0)                      | Double.valueOf(0.0)                      | false
        DOUBLE                  | Double.valueOf(0.0)                      | Double.valueOf(1.0)                      | false
        DOUBLE                  | Double.valueOf(0.0)                      | "a"                                      | false

        DOUBLE                  | Double.valueOf(1.0)                      | Double.valueOf(1.00000001)               | false
        DOUBLE                  | Double.valueOf(1.0)                      | Double.valueOf(1.000000000000001)        | false
        DOUBLE                  | Double.valueOf(1.0)                      | Double.valueOf(1.0000000000000001)       | true
        FLOAT                   | Float.valueOf(1.0)                       | Float.valueOf(1.0000001)                 | false
        FLOAT                   | Float.valueOf(1.0)                       | Float.valueOf(1.00000001)                | true

        NUMERIC                 | BigDecimal.valueOf(0.0)                  | BigDecimal.valueOf(0.0)                  | true
        DECIMAL                 | BigDecimal.valueOf(0.0)                  | BigDecimal.valueOf(0.0)                  | true
        NUMERIC                 | BigDecimal.valueOf(1.0)                  | BigDecimal.valueOf(0.0)                  | false
        NUMERIC                 | BigDecimal.valueOf(0.0)                  | BigDecimal.valueOf(1.0)                  | false
        NUMERIC                 | BigDecimal.valueOf(0.0)                  | "a"                                      | false

        DATE                    | Date.valueOf("2015-02-15")               | Date.valueOf("2015-02-15")               | true
        DATE                    | Date.valueOf("2015-02-16")               | Date.valueOf("2015-02-15")               | false
        DATE                    | Date.valueOf("2015-02-15")               | Date.valueOf("2015-02-16")               | false
        DATE                    | Date.valueOf("2015-02-15")               | "a"                                      | false

        TIME                    | Time.valueOf("10:10:10")                 | Time.valueOf("10:10:10")                 | true
        TIME_WITH_TIMEZONE      | Time.valueOf("10:10:10")                 | Time.valueOf("10:10:10")                 | true
        TIME                    | Time.valueOf("11:10:10")                 | Time.valueOf("10:10:10")                 | false
        TIME                    | Time.valueOf("10:10:10")                 | Time.valueOf("11:10:10")                 | false
        TIME                    | Time.valueOf("10:10:10")                 | "a"                                      | false

        TIMESTAMP               | Timestamp.valueOf("2015-02-15 10:10:10") | Timestamp.valueOf("2015-02-15 10:10:10") | true
        TIMESTAMP_WITH_TIMEZONE | Timestamp.valueOf("2015-02-15 10:10:10") | Timestamp.valueOf("2015-02-15 10:10:10") | true
        TIMESTAMP_WITH_TIMEZONE | ZonedDateTime.parse("2015-02-15T10:10:10Z") | ZonedDateTime.parse("2015-02-15T10:10:10Z") | true
        TIMESTAMP_WITH_TIMEZONE | ZonedDateTime.parse("2015-02-15T10:10:10+01:00") | ZonedDateTime.parse("2015-02-15T10:10:10+01:00") | true
        TIMESTAMP_WITH_TIMEZONE | ZonedDateTime.parse("2015-02-15T11:10:10+02:00") | ZonedDateTime.parse("2015-02-15T10:10:10+01:00") | false // same point in time, different zone
        TIMESTAMP_WITH_TIMEZONE | ZonedDateTime.parse("2015-02-15T10:10:10+02:00") | ZonedDateTime.parse("2015-02-15T10:10:10+01:00") | false // same local time, different zone
        TIMESTAMP               | Timestamp.valueOf("2015-02-16 10:10:10") | Timestamp.valueOf("2015-02-15 10:10:10") | false
        TIMESTAMP               | Timestamp.valueOf("2015-02-15 10:10:10") | Timestamp.valueOf("2015-02-16 10:10:10") | false
        TIMESTAMP               | Timestamp.valueOf("2015-02-15 10:10:10") | "a"                                      | false

        JAVA_OBJECT             | null                                     | null                                     | true
        JAVA_OBJECT             | UUID.fromString("50554d6e-29bb-11e5-b345-feff819cdc9f") | UUID.fromString("50554d6e-29bb-11e5-b345-feff819cdc9f") | true
        JAVA_OBJECT             | UUID.fromString("12151fd2-7586-11e9-8f9e-2a86e4085a59") | UUID.fromString("12151fd2-7586-11e9-8f9e-2a86e4085a59") | true
        JAVA_OBJECT             | UUID.fromString("50554d6e-29bb-11e5-b345-feff819cdc9f") | UUID.fromString("12151fd2-7586-11e9-8f9e-2a86e4085a59") | false
        JAVA_OBJECT             | UUID.fromString("50554d6e-29bb-11e5-b345-feff819cdc9f") | "50554d6e-29bb-11e5-b345-feff819cdc9f" | false
        JAVA_OBJECT             | UUID.fromString("50554d6e-29bb-11e5-b345-feff819cdc9f") | "a"                       | false
    }

    @Unroll
    def 'queryResultValueComparator(#type).compare(#actual,#expected) = #result with 0.01 tolerance'()
    {
        setup:
        Configuration configuration = Mock(Configuration)
        configuration.getDouble(_) >> Optional.of(Double.valueOf(0.01))

        expect:
        QueryResultValueComparator.comparatorForType(type, configuration).test(actual, expected) == result

        where:
        type   | actual                    | expected                | result
        DOUBLE | Double.valueOf(1.0)       | Double.valueOf(1.0)     | true
        DOUBLE | Double.valueOf(1.009999)  | Double.valueOf(1.0)     | true
        DOUBLE | Double.valueOf(1.01)      | Double.valueOf(1.0)     | false
        FLOAT  | Double.valueOf(1.0)       | Double.valueOf(1.0)     | true
        FLOAT  | Double.valueOf(1.009999)  | Double.valueOf(1.0)     | true
        FLOAT  | Double.valueOf(1.01)      | Double.valueOf(1.0)     | false

        DOUBLE | Double.valueOf(1000.0)    | Double.valueOf(1000.0)  | true
        DOUBLE | Double.valueOf(1010.0)    | Double.valueOf(1000.0)  | true
        DOUBLE | Double.valueOf(1010.001)  | Double.valueOf(1000.0)  | false
        FLOAT  | Double.valueOf(1000.0)    | Double.valueOf(1000.0)  | true
        FLOAT  | Double.valueOf(1010.0)    | Double.valueOf(1000.0)  | true
        FLOAT  | Double.valueOf(1010.001)  | Double.valueOf(1000.0)  | false

        DOUBLE | Double.valueOf(-1000.0)   | Double.valueOf(-1000.0) | true
        DOUBLE | Double.valueOf(-1010.0)   | Double.valueOf(-1000.0) | true
        DOUBLE | Double.valueOf(-1010.001) | Double.valueOf(-1000.0) | false
        FLOAT  | Double.valueOf(-1000.0)   | Double.valueOf(-1000.0) | true
        FLOAT  | Double.valueOf(-1010.0)   | Double.valueOf(-1000.0) | true
        FLOAT  | Double.valueOf(-1010.001) | Double.valueOf(-1000.0) | false
    }

    private byte[] byteArray(int value)
    {
        return [value];
    }
}
