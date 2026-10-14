package com.muffin.news.application.rss;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class RssFeedCategorySeedTest {

    private static final Pattern SEEDED_NAME = Pattern.compile("\\('([^']+)', NULL, NOW\\(6\\)\\)");

    /** 피드의 카테고리가 시드에 없으면 RssFeedWriter가 예외를 던져 그 카테고리 기사가 통째로 저장되지 않는다. */
    @Test
    void everyFeedCategoryIsSeeded() throws IOException {
        List<String> feedCategories = feedCategories();

        assertThat(feedCategories).isNotEmpty().doesNotHaveDuplicates();
        assertThat(seededCategories()).containsAll(feedCategories);
    }

    private static List<String> feedCategories() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();

        List<String> categories = new ArrayList<>();
        for (int index = 0; ; index++) {
            String category = properties.getProperty("muffin.news.rss.feeds[" + index + "].category");
            if (category == null) {
                return categories;
            }
            categories.add(category);
        }
    }

    private static List<String> seededCategories() throws IOException {
        String seed = new ClassPathResource("db/seed/R__seed_category.sql").getContentAsString(StandardCharsets.UTF_8);
        List<String> names = new ArrayList<>();
        Matcher matcher = SEEDED_NAME.matcher(seed);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }
}
