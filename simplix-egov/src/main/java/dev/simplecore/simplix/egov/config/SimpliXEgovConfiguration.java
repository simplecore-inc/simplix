package dev.simplecore.simplix.egov.config;

import org.egovframe.rte.fdl.cmmn.trace.LeaveaTrace;
import org.egovframe.rte.fdl.cmmn.trace.manager.DefaultTraceHandleManager;
import org.egovframe.rte.fdl.cmmn.trace.manager.TraceHandlerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;

/**
 * eGovFrame bean configuration for LeaveaTrace and TraceHandler.
 * Required by EgovAbstractServiceImpl which injects LeaveaTrace via @Resource.
 */
@Configuration
@ConditionalOnClass(name = "org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl")
public class SimpliXEgovConfiguration {

    @Bean
    @ConditionalOnMissingBean(SimpliXTraceHandler.class)
    public SimpliXTraceHandler simplixTraceHandler() {
        return new SimpliXTraceHandler();
    }

    @Bean
    public DefaultTraceHandleManager traceHandlerService(
        SimpliXTraceHandler simplixTraceHandler
    ) {
        AntPathMatcher antPathMatcher = new AntPathMatcher();
        DefaultTraceHandleManager manager = new DefaultTraceHandleManager();
        manager.setReqExpMatcher(antPathMatcher);
        manager.setPatterns(new String[] {"*"});
        manager.setHandlers(
            new org.egovframe.rte.fdl.cmmn.trace.handler.TraceHandler[] {simplixTraceHandler}
        );
        return manager;
    }

    @Bean("leaveaTrace")
    @ConditionalOnMissingBean(name = "leaveaTrace")
    public LeaveaTrace leaveaTrace(DefaultTraceHandleManager traceHandlerService) {
        LeaveaTrace leaveaTrace = new LeaveaTrace();
        leaveaTrace.setTraceHandlerServices(
            new TraceHandlerService[] {traceHandlerService}
        );
        return leaveaTrace;
    }
}
