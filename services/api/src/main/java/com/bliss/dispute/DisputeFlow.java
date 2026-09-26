package com.bliss.dispute;

import static io.quarkiverse.flow.dsl.FlowDSL.*;
import static io.quarkiverse.flow.dsl.FlowWorkflowBuilder.workflow;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.configurers.FuncTaskConfigurer;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;

@ApplicationScoped
public class DisputeFlow extends Flow {
    @Inject DisputeSteps steps;

    @Override
    public Workflow descriptor() {
        FuncTaskConfigurer wait =
                tasks ->
                        tasks.wait(
                                "waitForPartiesOrAdmin",
                                w -> w.wait(Duration.ofSeconds(2)).then("advanceDispute"));
        return workflow("dispute-resolution", "com.bliss", "1.0.0")
                .tasks(
                        withInstanceId(
                                "advanceDispute",
                                steps::advance,
                                DisputeSteps.State.class,
                                DisputeSteps.State.class),
                        switchWhenOrElse(
                                ".done == false", "waitForPartiesOrAdmin", FlowDirectiveEnum.END),
                        wait)
                .build();
    }
}
