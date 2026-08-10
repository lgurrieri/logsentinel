package com.logsentinel.infrastructure.adapters.out.persistence;

import com.pgvector.PGvector;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;

/**
 * Hibernate {@link UserType} that maps a Java {@code float[]} to the native pgvector
 * `vector` column type, using the pgvector-java driver helper ({@link PGvector}).
 * <p>
 * This is plumbing for the persistence layer only (Opción B — Flyway custom + query
 * nativa JPA, ver {@code .claude/state/orchestration/US2.md}). The similarity-search
 * query itself (operador {@code <=>}, Top K, fallback Full-Text) is scope de
 * LOG-US2-BE-02, no de este ticket.
 */
public class VectorType implements UserType<float[]> {

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<float[]> returnedClass() {
        return float[].class;
    }

    @Override
    public boolean equals(float[] x, float[] y) {
        return Arrays.equals(x, y);
    }

    @Override
    public int hashCode(float[] x) {
        return Arrays.hashCode(x);
    }

    @Override
    public float[] nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
            throws SQLException {
        String raw = rs.getString(position);
        if (raw == null) {
            return null;
        }
        return new PGvector(raw).toArray();
    }

    @Override
    public void nullSafeSet(PreparedStatement st, float[] value, int index, SharedSessionContractImplementor session)
            throws SQLException {
        st.setObject(index, value == null ? null : new PGvector(value));
    }

    @Override
    public float[] deepCopy(float[] value) {
        return value == null ? null : value.clone();
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(float[] value) {
        return deepCopy(value);
    }

    @Override
    public float[] assemble(Serializable cached, Object owner) {
        return deepCopy((float[]) cached);
    }
}
