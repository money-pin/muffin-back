package com.muffin.news.domain.term;

import org.springframework.data.jpa.repository.JpaRepository;

/** TermDictionary aggregate root repository. */
public interface TermDictionaryRepository extends JpaRepository<TermDictionary, Long> {}
