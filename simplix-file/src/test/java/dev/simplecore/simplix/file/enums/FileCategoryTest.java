package dev.simplecore.simplix.file.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FileCategory Enum")
class FileCategoryTest {

    @Test
    @DisplayName("Should define all expected file categories")
    void shouldDefineAllExpectedFileCategories() {
        FileCategory[] values = FileCategory.values();

        assertThat(values).hasSize(6);
        assertThat(values).containsExactlyInAnyOrder(
            FileCategory.IMAGE,
            FileCategory.VIDEO,
            FileCategory.AUDIO,
            FileCategory.DOCUMENT,
            FileCategory.ARCHIVE,
            FileCategory.OTHER
        );
    }

    @Test
    @DisplayName("Should resolve enum from name string")
    void shouldResolveEnumFromNameString() {
        assertThat(FileCategory.valueOf("IMAGE")).isEqualTo(FileCategory.IMAGE);
        assertThat(FileCategory.valueOf("VIDEO")).isEqualTo(FileCategory.VIDEO);
        assertThat(FileCategory.valueOf("AUDIO")).isEqualTo(FileCategory.AUDIO);
        assertThat(FileCategory.valueOf("DOCUMENT")).isEqualTo(FileCategory.DOCUMENT);
        assertThat(FileCategory.valueOf("ARCHIVE")).isEqualTo(FileCategory.ARCHIVE);
        assertThat(FileCategory.valueOf("OTHER")).isEqualTo(FileCategory.OTHER);
    }

    @Test
    @DisplayName("Should return correct name for each category")
    void shouldReturnCorrectNameForEachCategory() {
        assertThat(FileCategory.IMAGE.name()).isEqualTo("IMAGE");
        assertThat(FileCategory.VIDEO.name()).isEqualTo("VIDEO");
        assertThat(FileCategory.AUDIO.name()).isEqualTo("AUDIO");
        assertThat(FileCategory.DOCUMENT.name()).isEqualTo("DOCUMENT");
        assertThat(FileCategory.ARCHIVE.name()).isEqualTo("ARCHIVE");
        assertThat(FileCategory.OTHER.name()).isEqualTo("OTHER");
    }

    @Test
    @DisplayName("Should maintain ordinal order")
    void shouldMaintainOrdinalOrder() {
        assertThat(FileCategory.IMAGE.ordinal()).isEqualTo(0);
        assertThat(FileCategory.VIDEO.ordinal()).isEqualTo(1);
        assertThat(FileCategory.AUDIO.ordinal()).isEqualTo(2);
        assertThat(FileCategory.DOCUMENT.ordinal()).isEqualTo(3);
        assertThat(FileCategory.ARCHIVE.ordinal()).isEqualTo(4);
        assertThat(FileCategory.OTHER.ordinal()).isEqualTo(5);
    }
}
