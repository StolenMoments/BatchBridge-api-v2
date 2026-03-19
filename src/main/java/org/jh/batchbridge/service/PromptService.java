package org.jh.batchbridge.service;

import lombok.RequiredArgsConstructor;
import org.jh.batchbridge.repository.BatchPromptRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PromptService {
    private final BatchPromptRepository batchPromptRepository;
}
