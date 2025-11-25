package dev.simplecore.simplix.web.controller;

import dev.simplecore.simplix.web.service.SimpliXService;

@SimpliXStandardApi
public abstract class SimpliXBaseController<E, ID> {
    
    protected final SimpliXService<E, ID> service;

    protected SimpliXBaseController(SimpliXService<E, ID> service) {
        this.service = service;
    }

}
