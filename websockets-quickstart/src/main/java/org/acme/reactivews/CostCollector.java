package org.acme.reactivews;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.eclipse.microprofile.reactive.messaging.Incoming;

@Path("/collected-costs")
@ApplicationScoped
public class CostCollector {
    private double sum;

    @GET
    public synchronized double getCosts() {
        return sum;
    }

    @Incoming("collector")
    synchronized void collect(Cost cost) {
        if ("EUR".equals(cost.getCurrency())) {
            sum += cost.getValue();
        }
    }
}
