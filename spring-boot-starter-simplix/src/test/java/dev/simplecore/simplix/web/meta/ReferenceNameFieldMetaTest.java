package dev.simplecore.simplix.web.meta;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.annotation.References;
import dev.simplecore.simplix.core.annotation.ReferencesByKind;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.web.controller.SimpliXBaseController;
import dev.simplecore.simplix.web.meta.model.DtoMeta;
import dev.simplecore.simplix.web.meta.model.FieldMeta;
import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import jakarta.persistence.Id;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.util.pattern.PathPatternParser;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A reference that declares the property of the holding record carrying the referenced record's
 * name, read through the whole Meta build: published on every DTO an operation answers with (a list
 * and a detail), left out of a search DTO and a request body, and refused where an answered DTO
 * carries neither the property nor the nested path.
 */
class ReferenceNameFieldMetaTest {

    static class Person {
        @Id String personId;
    }

    static class Account {
        @Id String accountId;
    }

    static class Entry {
        @Id String entryId;
        @References(value = Person.class, nameField = "actorName") String actorId;
        @References(value = Account.class, nameField = "account.ownerName") String accountId;
        @ReferencesByKind(kindField = "subjectType", nameField = "subjectLabel", kinds = {
                @ReferencesByKind.Kind(value = "PERSON", entity = "Person"),
                @ReferencesByKind.Kind(value = "ACCOUNT", entity = "Account")})
        String subjectId;
        String subjectType;
        @References(Person.class) String reviewerId;
    }

    /** The nested record a holding DTO carries, as a client receives it. */
    public static class AccountRef {
        public String accountId;
        public String ownerName;
    }

    static class EntryDTOs {
        static class EntrySearchDTO {
            @SearchableField(operators = {SearchOperator.EQUALS, SearchOperator.IN}, sortable = true)
            public String entryId;
            @SearchableField(operators = {SearchOperator.EQUALS, SearchOperator.IN})
            public String actorId;
        }

        static class EntryCreateDTO {
            public String actorId;
            public String accountId;
        }

        static class EntryListDTO {
            public String entryId;
            public String actorId;
            public String actorName;
            public String accountId;
            public AccountRef account;
            public String subjectType;
            public String subjectId;
            public String subjectLabel;
            public String reviewerId;
            public List<String> aliases;
            public Instant occurredAt;
        }

        static class EntryDetailDTO {
            public String entryId;
            public String actorId;
            public String actorName;
            public String accountId;
            public AccountRef account;
            public String subjectType;
            public String subjectId;
            public String subjectLabel;
        }
    }

    static class EntryRestController extends SimpliXBaseController<Entry, String> {
        EntryRestController() { super(null); }

        public SimpliXApiResponse<Page<EntryDTOs.EntryListDTO>> search(
                @RequestBody SearchCondition<EntryDTOs.EntrySearchDTO> condition) { return null; }

        public SimpliXApiResponse<EntryDTOs.EntryDetailDTO> get(@PathVariable String id) { return null; }

        public SimpliXApiResponse<EntryDTOs.EntryDetailDTO> create(@RequestBody EntryDTOs.EntryCreateDTO body) { return null; }
    }

    /** A detail without the declared sibling: the list carries it, the detail does not. */
    static class Slip {
        @Id String slipId;
        @References(value = Person.class, nameField = "actorName") String actorId;
    }

    static class SlipDTOs {
        static class SlipListDTO {
            public String slipId;
            public String actorId;
            public String actorName;
        }

        static class SlipDetailDTO {
            public String slipId;
            public String actorId;
        }
    }

    static class SlipRestController extends SimpliXBaseController<Slip, String> {
        SlipRestController() { super(null); }

        public SimpliXApiResponse<SlipDTOs.SlipDetailDTO> get(@PathVariable String id) { return null; }
    }

    /** A nested path whose last segment the nested record does not carry. */
    static class Voucher {
        @Id String voucherId;
        @References(value = Account.class, nameField = "account.holderName") String accountId;
    }

    static class VoucherDTOs {
        static class VoucherListDTO {
            public String voucherId;
            public String accountId;
            public AccountRef account;
        }
    }

    static class VoucherRestController extends SimpliXBaseController<Voucher, String> {
        VoucherRestController() { super(null); }

        public SimpliXApiResponse<Page<VoucherDTOs.VoucherListDTO>> get(@PathVariable String id) { return null; }
    }

    /** A nested path through a collection, which names no single record. */
    static class Batch {
        @Id String batchId;
        @References(value = Account.class, nameField = "accounts.ownerName") String accountId;
    }

    static class BatchDTOs {
        static class BatchListDTO {
            public String batchId;
            public String accountId;
            public List<AccountRef> accounts;
        }
    }

    static class BatchRestController extends SimpliXBaseController<Batch, String> {
        BatchRestController() { super(null); }

        public SimpliXApiResponse<List<BatchDTOs.BatchListDTO>> get(@PathVariable String id) { return null; }
    }

    /** A by-kind reference whose single name field the answered DTO does not carry. */
    static class Event {
        @Id String eventId;
        @ReferencesByKind(kindField = "subjectType", nameField = "subjectLabel", kinds = {
                @ReferencesByKind.Kind(value = "PERSON", entity = "Person")})
        String subjectId;
        String subjectType;
    }

    static class EventDTOs {
        static class EventDetailDTO {
            public String eventId;
            public String subjectType;
            public String subjectId;
        }
    }

    static class EventRestController extends SimpliXBaseController<Event, String> {
        EventRestController() { super(null); }

        public SimpliXApiResponse<EventDTOs.EventDetailDTO> get(@PathVariable String id) { return null; }
    }

    private record Route(String path, RequestMethod method, Object bean, String name, Class<?>... parameters) {}

    private static DtoMeta build(Route... routes) throws Exception {
        RequestMappingInfo.BuilderConfiguration config = new RequestMappingInfo.BuilderConfiguration();
        config.setPatternParser(new PathPatternParser());
        Map<RequestMappingInfo, HandlerMethod> map = new LinkedHashMap<>();
        for (Route route : routes) {
            map.put(RequestMappingInfo.paths(route.path()).methods(route.method()).options(config).build(),
                    new HandlerMethod(route.bean(), route.bean().getClass().getMethod(route.name(), route.parameters())));
        }
        return new DtoMetaBuilder(new ObjectMapper(), List.of(), key -> Map.of()).build(map);
    }

    private static DtoMeta entries() throws Exception {
        EntryRestController controller = new EntryRestController();
        return build(
                new Route("/entries/search", RequestMethod.POST, controller, "search", SearchCondition.class),
                new Route("/entries/{id}", RequestMethod.GET, controller, "get", String.class),
                new Route("/entries/create", RequestMethod.POST, controller, "create", EntryDTOs.EntryCreateDTO.class));
    }

    private static FieldMeta field(DtoMeta meta, String type, String name) {
        return meta.types().get(type).fields().stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void aSiblingNameIsPublishedOnTheListAndTheDetail() throws Exception {
        DtoMeta meta = entries();
        assertThat(field(meta, "EntryListDTO", "actorId").reference().nameField()).isEqualTo("actorName");
        assertThat(field(meta, "EntryDetailDTO", "actorId").reference().nameField()).isEqualTo("actorName");
        assertThat(field(meta, "EntryListDTO", "actorId").reference().entity()).isEqualTo("Person");
    }

    @Test
    void aNestedPathIsPublishedWhereTheNestedRecordCarriesTheName() throws Exception {
        DtoMeta meta = entries();
        assertThat(field(meta, "EntryListDTO", "accountId").reference().nameField()).isEqualTo("account.ownerName");
        assertThat(field(meta, "EntryDetailDTO", "accountId").reference().nameField()).isEqualTo("account.ownerName");
    }

    @Test
    void aReferenceByKindTakesOneNameForEveryKind() throws Exception {
        DtoMeta meta = entries();
        FieldMeta subject = field(meta, "EntryListDTO", "subjectId");
        assertThat(subject.reference().kindField()).isEqualTo("subjectType");
        assertThat(subject.reference().nameField()).isEqualTo("subjectLabel");
        assertThat(field(meta, "EntryDetailDTO", "subjectId").reference().nameField()).isEqualTo("subjectLabel");
    }

    @Test
    void aSearchDtoAndARequestBodyPublishTheReferenceWithoutAName() throws Exception {
        DtoMeta meta = entries();
        assertThat(field(meta, "EntrySearchDTO", "actorId").reference()).isNotNull();
        assertThat(field(meta, "EntrySearchDTO", "actorId").reference().nameField()).isNull();
        assertThat(field(meta, "EntryCreateDTO", "actorId").reference().nameField()).isNull();
        assertThat(field(meta, "EntryCreateDTO", "accountId").reference().nameField()).isNull();
    }

    @Test
    void aReferenceDeclaringNoNamePublishesNone() throws Exception {
        assertThat(field(entries(), "EntryListDTO", "reviewerId").reference().nameField()).isNull();
    }

    @Test
    void aNameFieldIsWrittenToTheDocumentUnderReference() throws Exception {
        String json = new ObjectMapper().writeValueAsString(field(entries(), "EntryListDTO", "actorId"));
        assertThat(json).contains("\"nameField\":\"actorName\"");
        String plain = new ObjectMapper().writeValueAsString(field(entries(), "EntryListDTO", "reviewerId"));
        assertThat(plain).doesNotContain("nameField");
    }

    @Test
    void anAnsweredDetailWithoutTheSiblingFailsTheBuildNamingTheField() {
        SlipRestController controller = new SlipRestController();
        assertThatThrownBy(() -> build(new Route("/slips/{id}", RequestMethod.GET, controller, "get", String.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Slip.class.getName() + ".actorId")
                .hasMessageContaining("\"actorName\"")
                .hasMessageContaining("SlipDetailDTO");
    }

    @Test
    void aNestedPathTheNestedRecordDoesNotCarryFailsTheBuild() {
        VoucherRestController controller = new VoucherRestController();
        assertThatThrownBy(() -> build(new Route("/vouchers/{id}", RequestMethod.GET, controller, "get", String.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Voucher.class.getName() + ".accountId")
                .hasMessageContaining("\"account.holderName\"")
                .hasMessageContaining("VoucherListDTO");
    }

    @Test
    void aNestedPathThroughACollectionFailsTheBuild() {
        BatchRestController controller = new BatchRestController();
        assertThatThrownBy(() -> build(new Route("/batches/{id}", RequestMethod.GET, controller, "get", String.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Batch.class.getName() + ".accountId")
                .hasMessageContaining("\"accounts.ownerName\"");
    }

    @Test
    void aByKindNameTheAnsweredDtoDoesNotCarryFailsTheBuild() {
        EventRestController controller = new EventRestController();
        assertThatThrownBy(() -> build(new Route("/events/{id}", RequestMethod.GET, controller, "get", String.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Event.class.getName() + ".subjectId")
                .hasMessageContaining("\"subjectLabel\"")
                .hasMessageContaining("EventDetailDTO");
    }

    @Test
    void theCheckResolvesPathsByWireNameThroughNestedRecords() {
        TypeMetaFactory factory = new TypeMetaFactory(new ObjectMapper(), new TypeRefMapper(new MetaTypeRegistry()),
                new MetaTypeRegistry());
        assertThat(factory.carriesPath(EntryDTOs.EntryListDTO.class, "actorName")).isTrue();
        assertThat(factory.carriesPath(EntryDTOs.EntryListDTO.class, "account.ownerName")).isTrue();
        assertThat(factory.carriesPath(EntryDTOs.EntryListDTO.class, "account")).isTrue();
        assertThat(factory.carriesPath(EntryDTOs.EntryListDTO.class, "account.")).isFalse();
        assertThat(factory.carriesPath(EntryDTOs.EntryListDTO.class, "actorName.length")).isFalse();
        assertThat(factory.carriesPath(EntryDTOs.EntryListDTO.class, "actorName.empty")).isFalse();
        assertThat(factory.carriesPath(BatchDTOs.BatchListDTO.class, "accounts.ownerName")).isFalse();
        // A name is one value: a list of them names no single record.
        assertThat(factory.carriesPath(EntryDTOs.EntryListDTO.class, "aliases")).isFalse();
        // A value type's own getters (an instant's epoch second) are not a nested record.
        assertThat(factory.carriesPath(EntryDTOs.EntryListDTO.class, "occurredAt.epochSecond")).isFalse();
        assertThat(factory.carriesPath(EntryDTOs.EntryListDTO.class, "missing")).isFalse();
    }
}
