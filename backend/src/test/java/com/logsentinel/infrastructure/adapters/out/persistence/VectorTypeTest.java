package com.logsentinel.infrastructure.adapters.out.persistence;

import com.pgvector.PGvector;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link VectorType}, the Hibernate {@code UserType} that maps a Java
 * {@code float[]} to the native pgvector `vector` column type (LOG-US2-DB-01).
 * Pure JUnit + Mockito — no Spring context, no real database (the real database
 * round-trip is already covered by {@code RunbookChunkJpaRepositoryIntegrationTest}
 * with Testcontainers).
 */
@ExtendWith(MockitoExtension.class)
class VectorTypeTest {

    private final VectorType vectorType = new VectorType();

    @Mock
    private ResultSet resultSet;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private SharedSessionContractImplementor session;

    @Test
    @DisplayName("getSqlType should map to Types.OTHER (pgvector has no standard JDBC type)")
    void getSqlType_should_return_types_other() {
        assertThat(vectorType.getSqlType()).isEqualTo(Types.OTHER);
    }

    @Test
    @DisplayName("returnedClass should be float[]")
    void returnedClass_should_be_float_array() {
        assertThat(vectorType.returnedClass()).isEqualTo(float[].class);
    }

    @Test
    @DisplayName("equals should compare array contents")
    void equals_should_compare_array_contents() {
        float[] a = {0.1f, 0.2f, 0.3f};
        float[] b = {0.1f, 0.2f, 0.3f};
        float[] c = {0.9f, 0.9f, 0.9f};

        assertThat(vectorType.equals(a, b)).isTrue();
        assertThat(vectorType.equals(a, c)).isFalse();
    }

    @Test
    @DisplayName("hashCode should be based on array contents")
    void hashCode_should_be_based_on_array_contents() {
        float[] a = {0.1f, 0.2f, 0.3f};
        float[] b = {0.1f, 0.2f, 0.3f};

        assertThat(vectorType.hashCode(a)).isEqualTo(vectorType.hashCode(b));
    }

    @Test
    @DisplayName("deepCopy should return null for null input")
    void deepCopy_should_return_null_for_null_input() {
        assertThat(vectorType.deepCopy(null)).isNull();
    }

    @Test
    @DisplayName("deepCopy should return an equal but distinct array")
    void deepCopy_should_return_equal_but_distinct_array() {
        float[] original = {0.1f, 0.2f, 0.3f};

        float[] copy = vectorType.deepCopy(original);

        assertThat(copy).containsExactly(original).isNotSameAs(original);
    }

    @Test
    @DisplayName("isMutable should be true (float[] is a mutable Java type)")
    void isMutable_should_be_true() {
        assertThat(vectorType.isMutable()).isTrue();
    }

    @Test
    @DisplayName("disassemble/assemble should round-trip the array for Hibernate's second-level cache")
    void disassemble_and_assemble_should_round_trip() {
        float[] original = {0.1f, 0.2f, 0.3f};

        var cached = vectorType.disassemble(original);
        float[] assembled = vectorType.assemble(cached, new Object());

        assertThat(assembled).containsExactly(original);
    }

    @Test
    @DisplayName("nullSafeGet should return null when the column value is null")
    void nullSafeGet_should_return_null_when_column_is_null() throws SQLException {
        given(resultSet.getString(1)).willReturn(null);

        float[] result = vectorType.nullSafeGet(resultSet, 1, session, new Object());

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("nullSafeGet should parse the pgvector text representation into a float[]")
    void nullSafeGet_should_parse_vector_literal() throws SQLException {
        given(resultSet.getString(1)).willReturn("[0.5,-0.25,1.0]");

        float[] result = vectorType.nullSafeGet(resultSet, 1, session, new Object());

        assertThat(result).containsExactly(0.5f, -0.25f, 1.0f);
    }

    @Test
    @DisplayName("nullSafeSet should bind a null value directly")
    void nullSafeSet_should_bind_null_directly() throws SQLException {
        vectorType.nullSafeSet(preparedStatement, null, 1, session);

        verify(preparedStatement).setObject(1, null);
    }

    @Test
    @DisplayName("nullSafeSet should bind a PGvector wrapping the given array")
    void nullSafeSet_should_bind_pgvector_wrapping_array() throws SQLException {
        float[] value = {0.1f, 0.2f, 0.3f};

        vectorType.nullSafeSet(preparedStatement, value, 1, session);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(preparedStatement).setObject(eq(1), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(PGvector.class);
        assertThat(((PGvector) captor.getValue()).toArray()).containsExactly(value);
    }
}
