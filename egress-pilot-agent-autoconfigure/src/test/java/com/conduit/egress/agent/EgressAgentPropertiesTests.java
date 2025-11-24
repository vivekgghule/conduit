package com.conduit.egress.agent;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EgressAgentPropertiesTests {

    private final Validator validator;

    EgressAgentPropertiesTests() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
    }

    @Test
    void defaultPropertiesAreValid() {
        EgressAgentProperties props = new EgressAgentProperties();

        assertThat(validator.validate(props)).isEmpty();
    }

    @Test
    void blankServiceNameIsInvalid() {
        EgressAgentProperties props = new EgressAgentProperties();
        props.setServiceName("");

        assertThat(validator.validate(props)).isNotEmpty();
    }

    @Test
    void refreshIntervalBelowMinimumIsInvalid() {
        EgressAgentProperties props = new EgressAgentProperties();
        props.setRefreshIntervalSeconds(1);

        assertThat(validator.validate(props)).isNotEmpty();
    }
}
