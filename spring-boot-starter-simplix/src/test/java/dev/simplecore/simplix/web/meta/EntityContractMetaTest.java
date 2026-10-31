package dev.simplecore.simplix.web.meta;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.simplix.core.annotation.BooleanTone;
import dev.simplecore.simplix.core.annotation.CodeValue;
import dev.simplecore.simplix.core.annotation.FieldLabel;
import dev.simplecore.simplix.core.annotation.References;
import dev.simplecore.simplix.core.annotation.Tone;
import dev.simplecore.simplix.core.annotation.ToneKind;
import dev.simplecore.simplix.core.entity.annotation.MappingEntity;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.web.controller.SimpliXBaseController;
import dev.simplecore.simplix.web.meta.model.DtoMeta;
import dev.simplecore.simplix.web.meta.model.EntityMeta;
import dev.simplecore.simplix.web.meta.model.FieldMeta;
import dev.simplecore.simplix.web.meta.model.HolderMeta;
import dev.simplecore.simplix.web.meta.model.OperationMeta;
import dev.simplecore.simplix.web.meta.model.TypeMeta;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The entity-level contract: display names, references, holders, sort aliases, entity links. */
class EntityContractMetaTest {

    static class Grade {
        @Id String gradeId;
        String gradeName;
    }

    static class Badge {
        @Id String badgeId;
    }

    enum MemberStanding {
        @Tone(ToneKind.SUCCESS) ACTIVE,
        @Tone(ToneKind.DANGER) SUSPENDED,
        UNKNOWN
    }

    static class Member {
        @Id String memberId;
        @BooleanTone(whenTrue = ToneKind.SUCCESS, whenFalse = ToneKind.NEUTRAL) Boolean active;
        @References(Badge.class) Set<String> badgeIds;
        @Column(name = "grade_id") String gradeId;
        @ManyToOne @JoinColumn(name = "grade_id", insertable = false, updatable = false) Grade grade;
        String memberName;
        @CodeValue String memberCode;
    }

    static class MemberDTOs {
        static class MemberSearchDTO {
            @SearchableField(operators = {SearchOperator.EQUALS, SearchOperator.IN}, sortable = true)
            public String memberId;
            @SearchableField(entityField = "badgeIds", operators = {SearchOperator.EQUALS, SearchOperator.IN})
            public Set<String> badgeIds;
            @SearchableField(entityField = "gradeId", operators = {SearchOperator.EQUALS})
            public String gradeId;
            @SearchableField(entityField = "memberNameSearch", operators = {SearchOperator.CONTAINS})
            public String memberName;
            @SearchableField(entityField = "memberNameSortEn", sortable = true)
            public String memberNameSortEn;
            @SearchableField(entityField = "memberNameSortKo", sortable = true)
            public String memberNameSortKo;
        }

        static class MemberDetailDTO {
            public String memberId;
            public Boolean active;
            @BooleanTone(whenTrue = ToneKind.INFO, whenFalse = ToneKind.NEUTRAL) public Boolean pending;
            public MemberStanding standing;
            public Set<String> badgeIds;
            public String gradeId;
            public String memberCode;
            @CodeValue public String inviteToken;
            @FieldLabel("{field.count}") public Long count;
            @FieldLabel("{field.unknown}") public Long unknown;
            @FieldLabel("Literal") public Long literal;
        }
    }

    static class GradeDTOs {
        static class GradeDetailDTO {
            public String gradeId;
            public String gradeName;
        }
    }

    static class MemberRestController extends SimpliXBaseController<Member, String> {
        MemberRestController() { super(null); }

        @PostMapping("/search")
        public SimpliXApiResponse<Page<MemberDTOs.MemberDetailDTO>> search(
                @RequestBody SearchCondition<MemberDTOs.MemberSearchDTO> condition) { return null; }

        @GetMapping("/{memberId}")
        @PreAuthorize("hasPermission('PEOPLE', 'view') or @memberService.isOwner(#memberId)")
        public SimpliXApiResponse<MemberDTOs.MemberDetailDTO> get(@PathVariable("memberId") String memberId) {
            return null;
        }
    }

    /** Maps a grade to a badge: it references a grade, and must still never hold one. */
    @MappingEntity
    static class GradeBadge {
        @Id String gradeBadgeId;
        @Column(name = "grade_id") String gradeId;
        @ManyToOne @JoinColumn(name = "grade_id", insertable = false, updatable = false) Grade grade;
        @References(Badge.class) String badgeId;
    }

    static class GradeBadgeDTOs {
        static class GradeBadgeSearchDTO {
            @SearchableField(operators = {SearchOperator.EQUALS, SearchOperator.IN}, sortable = true)
            public String gradeBadgeId;
            @SearchableField(entityField = "gradeId", operators = {SearchOperator.EQUALS})
            public String gradeId;
        }

        static class GradeBadgeListDTO {
            public String gradeBadgeId;
            public String gradeId;
        }
    }

    static class GradeBadgeRestController extends SimpliXBaseController<GradeBadge, String> {
        GradeBadgeRestController() { super(null); }

        @PostMapping("/search")
        public SimpliXApiResponse<Page<GradeBadgeDTOs.GradeBadgeListDTO>> search(
                @RequestBody SearchCondition<GradeBadgeDTOs.GradeBadgeSearchDTO> condition) { return null; }
    }

    static class GradeRestController extends SimpliXBaseController<Grade, String> {
        GradeRestController() { super(null); }

        @GetMapping("/{gradeId}")
        @PreAuthorize("hasPermission('PEOPLE', 'view')")
        public SimpliXApiResponse<GradeDTOs.GradeDetailDTO> get(@PathVariable("gradeId") String gradeId) {
            return null;
        }
    }

    private static final Map<String, Map<String, String>> BUNDLE = Map.of(
            "entities.Member", Map.of("en", "Member", "ko", "구성원"),
            "entities.Member._plural", Map.of("en", "Members", "ko", "구성원 목록"),
            "entities.Member._description", Map.of("en", "People who belong", "ko", "소속된 사람"),
            "entities.Grade", Map.of("en", "Grade"),
            "field.count", Map.of("en", "Count", "ko", "건수"));

    private DtoMeta build() throws Exception {
        RequestMappingInfo.BuilderConfiguration config = new RequestMappingInfo.BuilderConfiguration();
        config.setPatternParser(new PathPatternParser());
        MemberRestController members = new MemberRestController();
        GradeRestController grades = new GradeRestController();
        GradeBadgeRestController gradeBadges = new GradeBadgeRestController();
        Map<RequestMappingInfo, HandlerMethod> map = new LinkedHashMap<>();
        map.put(RequestMappingInfo.paths("/members/search").methods(RequestMethod.POST).options(config).build(),
                new HandlerMethod(members, MemberRestController.class.getMethod("search", SearchCondition.class)));
        map.put(RequestMappingInfo.paths("/members/{memberId}").methods(RequestMethod.GET).options(config).build(),
                new HandlerMethod(members, MemberRestController.class.getMethod("get", String.class)));
        map.put(RequestMappingInfo.paths("/grades/{gradeId}").methods(RequestMethod.GET).options(config).build(),
                new HandlerMethod(grades, GradeRestController.class.getMethod("get", String.class)));
        map.put(RequestMappingInfo.paths("/grade-badges/search").methods(RequestMethod.POST).options(config).build(),
                new HandlerMethod(gradeBadges, GradeBadgeRestController.class.getMethod("search", SearchCondition.class)));
        return new DtoMetaBuilder(new ObjectMapper(), List.of(),
                key -> BUNDLE.getOrDefault(key, Map.of())).build(map);
    }

    private static FieldMeta field(TypeMeta type, String name) {
        return type.fields().stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void fieldLabelledByKeyCarriesItsTextPerLanguage() throws Exception {
        TypeMeta detail = build().types().get("MemberDetailDTO");
        FieldMeta count = field(detail, "count");
        assertThat(count.labelKey()).isEqualTo("field.count");
        assertThat(count.labels()).containsEntry("en", "Count").containsEntry("ko", "건수");
        assertThat(field(detail, "unknown").labels()).isNull();
        assertThat(field(detail, "literal").labels()).isNull();
        assertThat(field(detail, "memberId").labels()).isNull();
    }

    @Test
    void entityCarriesDisplayNameAndPluralPerLanguage() throws Exception {
        EntityMeta member = build().entities().get("Member");
        assertThat(member.labelKey()).isEqualTo("entities.Member");
        assertThat(member.pluralKey()).isEqualTo("entities.Member._plural");
        assertThat(member.label()).containsEntry("en", "Member").containsEntry("ko", "구성원");
        assertThat(member.plural()).containsEntry("en", "Members").containsEntry("ko", "구성원 목록");
        assertThat(member.descriptionKey()).isEqualTo("entities.Member._description");
        assertThat(member.description()).containsEntry("en", "People who belong").containsEntry("ko", "소속된 사람");
        assertThat(member.idField()).isEqualTo("memberId");
    }

    @Test
    void entityWithoutPluralOmitsIt() throws Exception {
        EntityMeta grade = build().entities().get("Grade");
        assertThat(grade.label()).containsOnlyKeys("en");
        assertThat(grade.plural()).isNull();
        assertThat(grade.description()).isNull();
    }

    @Test
    void operationsAndTypesNameTheirEntity() throws Exception {
        DtoMeta meta = build();
        assertThat(meta.operations()).extracting(OperationMeta::entity).containsOnly("Member", "Grade", "GradeBadge");
        assertThat(meta.types().get("MemberSearchDTO").entity()).isEqualTo("Member");
        assertThat(meta.types().get("GradeDetailDTO").entity()).isEqualTo("Grade");
    }

    @Test
    void declaredReferenceOnIdCollection() throws Exception {
        FieldMeta badgeIds = field(build().types().get("MemberDetailDTO"), "badgeIds");
        assertThat(badgeIds.reference()).isNotNull();
        assertThat(badgeIds.reference().entity()).isEqualTo("Badge");
        assertThat(badgeIds.reference().idField()).isEqualTo("badgeId");
        assertThat(badgeIds.reference().many()).isTrue();
    }

    @Test
    void referenceReadFromTheManyToOneSharingTheJoinColumn() throws Exception {
        FieldMeta gradeId = field(build().types().get("MemberSearchDTO"), "gradeId");
        assertThat(gradeId.reference().entity()).isEqualTo("Grade");
        assertThat(gradeId.reference().idField()).isEqualTo("gradeId");
        assertThat(gradeId.reference().many()).isFalse();
    }

    @Test
    void ownIdentifierIsNoReference() throws Exception {
        DtoMeta meta = build();
        assertThat(field(meta.types().get("MemberSearchDTO"), "memberId").reference()).isNull();
        assertThat(field(meta.types().get("GradeDetailDTO"), "gradeId").reference()).isNull();
    }

    @Test
    void holdersDerivedFromSearchableReferences() throws Exception {
        DtoMeta meta = build();
        List<HolderMeta> gradeHolders = meta.entities().get("Grade").holders();
        assertThat(gradeHolders).hasSize(1);
        assertThat(gradeHolders.get(0).entity()).isEqualTo("Member");
        assertThat(gradeHolders.get(0).field()).isEqualTo("gradeId");
        assertThat(gradeHolders.get(0).operators()).containsExactly("EQUALS");
        assertThat(gradeHolders.get(0).searchOperation()).isEqualTo("MemberRest_search");
        assertThat(meta.entities().get("Member").holders()).isNull();
    }

    @Test
    void mappingEntityIsNeverAHolder() throws Exception {
        DtoMeta meta = build();
        // GradeBadge filters by gradeId exactly as Member does; only the mark tells them apart.
        assertThat(meta.entities()).containsKey("GradeBadge");
        assertThat(meta.entities().get("Grade").holders())
                .extracting(HolderMeta::entity).containsExactly("Member");
    }

    @Test
    void visibleNameSortedThroughLocaleSiblings() throws Exception {
        FieldMeta name = field(build().types().get("MemberSearchDTO"), "memberName");
        assertThat(name.searchable().sortable()).isFalse();
        assertThat(name.searchable().sortAlias())
                .containsExactly(Map.entry("en", "memberNameSortEn"), Map.entry("ko", "memberNameSortKo"));
        assertThat(field(build().types().get("MemberSearchDTO"), "memberId").searchable().sortAlias()).isNull();
    }

    @Test
    void keywordIsAbsentUnlessTheFieldOptsIn() throws Exception {
        // The test classpath carries a searchable-jpa without keyword(), which reads as no opt-in.
        FieldMeta name = field(build().types().get("MemberSearchDTO"), "memberName");
        assertThat(name.searchable().keyword()).isNull();
        assertThat(name.searchable().withKeyword(true).keyword()).isTrue();
        assertThat(name.searchable().withKeyword(true).withSortAlias(Map.of()).keyword()).isTrue();
        assertThat(name.searchable().withKeyword(false).keyword()).isNull();
    }

    @Test
    void orChainPublishesItsPermissionTerms() throws Exception {
        OperationMeta get = build().operations().stream()
                .filter(o -> o.id().equals("MemberRest_get")).findFirst().orElseThrow();
        assertThat(get.access().kind()).isEqualTo("expression");
        assertThat(get.access().raw()).contains("@memberService.isOwner");
        assertThat(get.access().operator()).isEqualTo("or");
        assertThat(get.access().permissions()).hasSize(1);
        assertThat(get.access().permissions().get(0).group()).isEqualTo("PEOPLE");
        assertThat(get.access().permissions().get(0).action()).isEqualTo("view");
    }

    @Test
    void enumConstantsCarryTheirTone() throws Exception {
        DtoMeta meta = build();
        assertThat(meta.enums().get("MemberStanding").values())
                .extracting(v -> v.name() + ":" + v.tone())
                .containsExactly("ACTIVE:success", "SUSPENDED:danger", "UNKNOWN:null");
    }

    @Test
    void flagTonesFromEntityFieldAndFromDerivedDtoField() throws Exception {
        TypeMeta detail = build().types().get("MemberDetailDTO");
        assertThat(field(detail, "active").tones())
                .containsEntry("true", "success").containsEntry("false", "neutral");
        assertThat(field(detail, "pending").tones())
                .containsEntry("true", "info").containsEntry("false", "neutral");
        assertThat(field(detail, "memberId").tones()).isNull();
    }

    @Test
    void codeFormatFromEntityFieldAndFromDerivedDtoField() throws Exception {
        TypeMeta detail = build().types().get("MemberDetailDTO");
        assertThat(field(detail, "memberCode").format()).isEqualTo("code");
        assertThat(field(detail, "inviteToken").format()).isEqualTo("code");
        assertThat(field(detail, "memberId").format()).isNull();
        assertThat(field(detail, "gradeId").format()).isNull();
    }
}
