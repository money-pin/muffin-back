-- 재구성 단계에서 news.title이 AI 생성 제목으로 바뀌므로, 매경 원문 제목을 따로 남긴다.
-- AI가 사실을 왜곡한 제목을 만들었을 때 원문과 대조하고, 프롬프트 수정 후 다시 생성할 때 원본 입력으로 쓴다.
ALTER TABLE news ADD COLUMN original_title varchar(255) NULL;

-- 기존 행은 title이 곧 매경 원문 제목이므로 그대로 채운다.
UPDATE news SET original_title = title WHERE original_title IS NULL;
