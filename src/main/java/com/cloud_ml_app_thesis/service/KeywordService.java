package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.entity.model.Keyword;
import com.cloud_ml_app_thesis.repository.KeywordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor
@Service
public class KeywordService {
    private KeywordRepository keywordRepository;
    public Set<Keyword> resolveOrCreate(Set<String> keywordStrings) {
        Set<Keyword> result = new HashSet<>();
        for (String raw : keywordStrings) {
            String clean = raw.trim().toLowerCase();
            Optional<Keyword> existing = keywordRepository.findByName(clean);
            if (existing.isPresent()) {
                result.add(existing.get());
            } else {
                Keyword newKeyword = new Keyword();
                newKeyword.setName(clean);
                result.add(keywordRepository.save(newKeyword));
            }
        }
        return result;
    }
}
