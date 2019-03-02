package it.fvaleri.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import it.fvaleri.example.InputReportIncident;
import it.fvaleri.example.OutputReportIncident;

public class RequestWorker {
    private static final Logger LOG = LoggerFactory.getLogger(RequestWorker.class);

    public OutputReportIncident process(InputReportIncident in) {
        LOG.info("Processing request");
        OutputReportIncident out = new OutputReportIncident();
        out.setCode(in.getIncidentId()+"-ok");
        return out;
    }
}
