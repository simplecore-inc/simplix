package dev.simplecore.simplix.web.controller;

import dev.simplecore.simplix.web.service.SimpliXService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXBaseController - abstract base controller with service injection")
class SimpliXBaseControllerTest {

    @Mock
    private SimpliXService<Object, Long> service;

    @Test
    @DisplayName("Should store service reference via constructor")
    void constructorInjection() {
        TestController controller = new TestController(service);

        assertThat(controller.getService()).isSameAs(service);
    }

    @Test
    @DisplayName("Should have @SimpliXStandardApi annotation on class level")
    void hasStandardApiAnnotation() {
        SimpliXStandardApi annotation = SimpliXBaseController.class.getAnnotation(SimpliXStandardApi.class);

        assertThat(annotation).isNotNull();
    }

    static class TestController extends SimpliXBaseController<Object, Long> {
        TestController(SimpliXService<Object, Long> service) {
            super(service);
        }

        SimpliXService<Object, Long> getService() {
            return service;
        }
    }
}
