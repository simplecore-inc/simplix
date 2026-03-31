package dev.simplecore.simplix.egov.config;

import lombok.extern.slf4j.Slf4j;
import org.egovframe.rte.fdl.cmmn.trace.handler.TraceHandler;

/**
 * SimpliX implementation of eGovFrame TraceHandler.
 * Logs trace messages from EgovAbstractServiceImpl.leaveaTrace() calls.
 */
@Slf4j
public class SimpliXTraceHandler implements TraceHandler {

    @Override
    public void todo(Class<?> clazz, String message) {
        log.debug("[eGov-TRACE] {}: {}", clazz.getName(), message);
    }
}
