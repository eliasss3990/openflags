package com.openflags.core.evaluation.rule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorTest {

    @Test
    void operator_hasExpectedValues() {
        assertThat(Operator.values()).containsExactly(
                Operator.EQ,
                Operator.NEQ,
                Operator.IN,
                Operator.NOT_IN,
                Operator.GT,
                Operator.GTE,
                Operator.LT,
                Operator.LTE,
                Operator.CONTAINS,
                Operator.STARTS_WITH,
                Operator.ENDS_WITH,
                Operator.MATCHES
        );
    }

    @Test
    void operator_valueOf_works() {
        assertThat(Operator.valueOf("EQ")).isEqualTo(Operator.EQ);
        assertThat(Operator.valueOf("MATCHES")).isEqualTo(Operator.MATCHES);
        assertThat(Operator.valueOf("NOT_IN")).isEqualTo(Operator.NOT_IN);
    }

    @Test
    void operator_count_is12() {
        assertThat(Operator.values()).hasSize(12);
    }
}
