package com.bliss.workflow;

import static io.quarkiverse.flow.dsl.FlowDSL.*;
import static io.quarkiverse.flow.dsl.FlowWorkflowBuilder.workflow;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.configurers.FuncTaskConfigurer;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;

/** Durable human-action inbox with an engine-managed wait; no sleeping Java worker. */
@ApplicationScoped
public class BookingLifecycleFlow extends Flow {
    @Inject BookingWorkflowSteps steps;

    @Override
    public Workflow descriptor() {
        FuncTaskConfigurer waitForAction =
                tasks ->
                        tasks.wait(
                                "waitForAction",
                                w -> w.wait(Duration.ofSeconds(2)).then("advanceBooking"));
        return workflow("booking-lifecycle", "com.bliss", "1.0.0")
                .tasks(
                        withInstanceId(
                                "advanceBooking",
                                steps::advance,
                                BookingWorkflowSteps.State.class,
                                BookingWorkflowSteps.State.class),
                        switchWhenOrElse(".done == false", "waitForAction", FlowDirectiveEnum.END),
                        waitForAction)
                .build();
    }
}
