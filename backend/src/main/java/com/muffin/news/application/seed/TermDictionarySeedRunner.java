package com.muffin.news.application.seed;

import com.muffin.news.domain.term.TermDictionary;
import com.muffin.news.domain.term.TermDictionaryRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 애플리케이션 기동 시 경제금융용어 700선 기준 데이터를 멱등하게 채워 넣는다.
 *
 * <p>용어 설명은 뉴스 본문 바텀시트에서 바로 노출할 수 있도록 200자 이내로 정리된 seed 파일을 사용한다.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class TermDictionarySeedRunner implements ApplicationRunner {

    private static final String SEED_PATH = "seed/term_dictionary.tsv";

    private final TermDictionaryRepository termDictionaryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int targetCount = 0;
        int insertedCount = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(SEED_PATH).getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split("\t", 2);
                if (columns.length < 2) {
                    log.warn("용어 사전 시딩 스킵: 잘못된 행 형식, line={}", line);
                    continue;
                }

                targetCount++;
                String term = columns[0].trim();
                String content = columns[1].trim();

                if (term.isBlank() || content.isBlank()) {
                    log.warn("용어 사전 시딩 스킵: 빈 값 포함, term={}", term);
                    continue;
                }

                if (termDictionaryRepository.findByTerm(term).isPresent()) {
                    continue;
                }

                termDictionaryRepository.save(TermDictionary.create(term, content));
                insertedCount++;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("용어 사전 seed 파일을 읽을 수 없습니다: " + SEED_PATH, exception);
        }

        log.info("용어 사전 시딩 완료: 대상 {}건, 신규 {}건", targetCount, insertedCount);
    }
}
